"""Export task-specific correctors; sources are pinned in PROOFREADING-SOURCES.md.

Build dependency only: torch, transformers, onnx, onnxruntime, sentencepiece.
No model conversion, downloads or Python are used on the phone.
"""
import argparse, json, shutil, onnx
from onnx import numpy_helper
from pathlib import Path
import numpy as np
import torch
from transformers import AutoModelForSeq2SeqLM, AutoModelForMaskedLM, AutoTokenizer
from onnxruntime.quantization import quantize_dynamic, QuantType, quantize, CalibrationDataReader
from onnxruntime.quantization.execution_providers.qnn import qnn_preprocess_model, get_qnn_qdq_config

p=argparse.ArgumentParser();p.add_argument('--sources',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True);a.work.mkdir(parents=True,exist_ok=True);torch.set_num_threads(4)

class Encoder(torch.nn.Module):
    def __init__(self,m):super().__init__();self.encoder=m.encoder
    def forward(self,ids,mask):return self.encoder(input_ids=ids,attention_mask=mask,return_dict=False)[0]

class Decoder(torch.nn.Module):
    def __init__(self,m):super().__init__();self.decoder=m.decoder;self.head=m.lm_head;self.scale=m.model_dim**-0.5 if m.config.tie_word_embeddings else 1.
    def forward(self,ids,hidden,mask):
        x=self.decoder(input_ids=ids,encoder_hidden_states=hidden,encoder_attention_mask=mask,use_cache=False,return_dict=False)[0]
        return self.head(x[:,-1,:]*self.scale)

en=a.sources/'t5tiny';m=AutoModelForSeq2SeqLM.from_pretrained(en,local_files_only=True,attn_implementation='eager').eval()
tok=AutoTokenizer.from_pretrained(en,local_files_only=True);sample=tok('She go to school every day.',return_tensors='pt')
with torch.no_grad():
    hidden=Encoder(m)(sample['input_ids'],sample['attention_mask'])
    for name,module,inputs,ins,outs,dynamic in [
        ('en-encoder',Encoder(m),(sample['input_ids'],sample['attention_mask']),['ids','mask'],['hidden'],{'ids':{1:'source'},'mask':{1:'source'},'hidden':{1:'source'}}),
        ('en-decoder',Decoder(m),(torch.tensor([[0,27]]),hidden,sample['attention_mask']),['ids','hidden','mask'],['logits'],{'ids':{1:'target'},'hidden':{1:'source'},'mask':{1:'source'}})]:
        raw=a.work/(name+'.onnx')
        if not raw.exists():torch.onnx.export(module,inputs,raw,input_names=ins,output_names=outs,dynamic_axes=dynamic,opset_version=17,dynamo=False)
        quantize_dynamic(raw,a.output/(name+'.onnx'),per_channel=True,weight_type=QuantType.QInt8,op_types_to_quantize=['MatMul','Gather'])
shutil.copyfile(en/'tokenizer.json',a.output/'en-tokenizer.json')
del m

zh=a.sources/'macbert';tokenizer=AutoTokenizer.from_pretrained(zh,local_files_only=True)
class Chinese(torch.nn.Module):
    def __init__(self):super().__init__();self.m=AutoModelForMaskedLM.from_pretrained(zh,local_files_only=True,attn_implementation='eager').eval()
    def forward(self,ids,mask):return self.m(input_ids=ids,attention_mask=mask,return_dict=False)[0]
raw=a.work/'zh.onnx';optimized=a.work/'zh-mask-pre.onnx'
if not raw.exists():
    model=Chinese().eval();x=tokenizer('今天心情很好。',return_tensors='pt',padding='max_length',max_length=128)
    with torch.no_grad():torch.onnx.export(model,(x['input_ids'],x['attention_mask']),raw,input_names=['ids','mask'],output_names=['logits'],opset_version=17,dynamo=False)
    del model
if not optimized.exists():
    # BERT's float-min attention mask destroys activation calibration ranges.
    # -100 still masks padded keys (exp(-100) is negligible) without that outlier.
    graph=onnx.load(raw);changed=0
    tensors=list(graph.graph.initializer)+[attr.t for node in graph.graph.node for attr in node.attribute if attr.type==onnx.AttributeProto.TENSOR]
    for tensor in tensors:
        values=numpy_helper.to_array(tensor).copy()
        if np.issubdtype(values.dtype,np.floating) and np.any(values < -1e20):
            values[values < -1e20]=-100.;tensor.CopyFrom(numpy_helper.from_array(values,tensor.name));changed+=1
    assert changed>0, 'Expected an exported BERT attention mask constant'
    bounded=a.work/'zh-mask.onnx';onnx.save(graph,bounded)
    if not qnn_preprocess_model(bounded,optimized,fuse_layernorm=True):shutil.copyfile(bounded,optimized)

# Synthetic calibration text, distinct from the held-out correction checks.
calibration=['今天下午我们一起讨论新的学习计划。','请确认收货地址和联系电话，订单金额是三百元。','自然语言处理能够帮助人们提高工作效率。','明天的天气怎么样，周末可以去公园散步吗？','这个程序使用Python和Java开发，版本是1.3。','老师要求我们在星期五之前完成实验报告。','会议从上午九点开始，到十一点结束。','测试编号A123和B456必须保持不变。','我没有同意取消预约，也不希望修改原来的安排。','请把文件保存到本地，不要上传任何个人信息。','北京上海广州深圳成都武汉南京杭州苏州天津。','项目的研究目标包括模型压缩、量化和性能优化。']
class Reader(CalibrationDataReader):
    def __init__(self):self.items=iter(calibration)
    def get_next(self):
        s=next(self.items,None)
        if s is None:return None
        x=tokenizer(s,return_tensors='np',padding='max_length',truncation=True,max_length=128)
        return {'ids':x['input_ids'].astype(np.int64),'mask':x['attention_mask'].astype(np.int64)}
config=get_qnn_qdq_config(optimized,Reader(),activation_type=QuantType.QUInt16,weight_type=QuantType.QUInt8)
quantize(optimized,a.output/'zh-qdq.onnx',config)
shutil.copyfile(zh/'vocab.txt',a.output/'zh-vocab.txt')
dictionary=Path(__file__).parent/'rime-data/cn_dicts/8105.dict.yaml'
readings=[]
for line in dictionary.read_text('utf8').splitlines():
    fields=line.split('\t')
    if len(fields)>=2 and len(fields[0])==1 and fields[1].isascii() and fields[1].isalpha():readings.append('\t'.join(fields[:2]))
(a.output/'zh-readings.tsv').write_text('\n'.join(readings)+'\n','utf8')
for f in a.output.iterdir():print(f.name,f.stat().st_size,flush=True)
