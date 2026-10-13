package local.gboard.proofread;
import android.content.Context;
import android.os.SystemClock;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;
import local.gboard.enhanced.VoiceText;

/** Two task-specific quantized models. Audio and keyboard history never enter this component. */
final class ProofEngine implements AutoCloseable {
  final Context app;final File dir;final OrtEnvironment env;
  OrtSession encoder,decoder,chinese;EnglishTokenizer enTokens;ChineseTokenizer zhTokens;
  final Map<String,Set<String>> readings=new HashMap<>();String backend="CPU",lastBackend="";static final Object MODEL_LOCK=new Object();
  interface Progress{void update(String message);}
  static final class Run implements AutoCloseable {
    volatile boolean cancelled;final long deadline;
    final OrtSession.RunOptions options=new OrtSession.RunOptions();
    Run()throws OrtException{this(12000);}Run(long ms)throws OrtException{deadline=SystemClock.elapsedRealtime()+ms;}
    void check()throws IOException{if(cancelled||SystemClock.elapsedRealtime()>deadline)throw new IOException("cancelled or timeout");}
    void cancel(){cancelled=true;try{options.setTerminate(true);}catch(Exception ignored){}}
    public void close(){options.close();}
  }
  ProofEngine(Context c)throws Exception{app=c.getApplicationContext();dir=new File(app.getNoBackupFilesDir(),"proof-v2");dir.mkdirs();env=OrtEnvironment.getEnvironment();env.setTelemetry(false);}
  File asset(String name)throws Exception{
    File f=new File(dir,name);if(f.isFile()&&f.length()>0)return f;
    File temp=new File(dir,name+".tmp");try(InputStream in=app.getAssets().open("models/"+name);OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[131072];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(!temp.renameTo(f))throw new IOException("Model installation failed");return f;
  }
  OrtSession.SessionOptions cpu()throws Exception{OrtSession.SessionOptions o=new OrtSession.SessionOptions();o.setIntraOpNumThreads(2);o.setInterOpNumThreads(1);o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);return o;}
  void loadEnglish(Run run)throws Exception{
    synchronized(MODEL_LOCK){
    if(encoder!=null&&decoder!=null)return;run.check();
    if(enTokens==null)enTokens=new EnglishTokenizer(asset("en-tokenizer.json"));
    try(OrtSession.SessionOptions o=cpu()){
      if(encoder==null)encoder=env.createSession(asset("en-encoder.onnx").getPath(),o);run.check();
      if(decoder==null)decoder=env.createSession(asset("en-decoder.onnx").getPath(),o);
    }
    }
  }
  void loadChinese(Run run,String requestedBackend)throws Exception{
    synchronized(MODEL_LOCK){
    if(chinese!=null)return;run.check();
    if(zhTokens==null)zhTokens=new ChineseTokenizer(asset("zh-vocab.txt"));
    if(readings.isEmpty())try(BufferedReader reader=new BufferedReader(new InputStreamReader(app.getAssets().open("models/zh-readings.tsv"),"UTF-8"))){String s;while((s=reader.readLine())!=null){String[] parts=s.split("\t");if(parts.length==2)readings.computeIfAbsent(parts[0],k->new HashSet<>()).add(parts[1]);}}
    File model=asset("zh-qdq.onnx");
    if(!requestedBackend.equals("cpu")&&OrtEnvironment.getAvailableProviders().contains(OrtProvider.QNN)){
      try(OrtSession.SessionOptions o=cpu()){
        boolean gpu=requestedBackend.equals("gpu");File cache=new File(dir,gpu?"zh-gpu-context.onnx":"zh-htp-context.onnx");
        Map<String,String> qnn=new HashMap<>();qnn.put("backend_path",app.getApplicationInfo().nativeLibraryDir+(gpu?"/libQnnGpu.so":"/libQnnHtp.so"));
        if(!gpu){qnn.put("htp_performance_mode","balanced");qnn.put("htp_graph_finalization_optimization_mode","3");}
        o.addQnn(qnn);o.addConfigEntry("session.disable_cpu_ep_fallback","1");
        if(!cache.isFile()){o.addConfigEntry("ep.context_enable","1");o.addConfigEntry("ep.context_embed_mode","0");o.addConfigEntry("ep.context_file_path",cache.getPath());}
        chinese=env.createSession(cache.isFile()?cache.getPath():model.getPath(),o);backend=gpu?"Adreno GPU":"Hexagon NPU";
      }catch(Exception e){backend="CPU";android.util.Log.i("LocalProof","QNN initialization unavailable; using CPU");if(requestedBackend.equals("npu")||requestedBackend.equals("gpu"))throw e;}
    }
    if(chinese==null)try(OrtSession.SessionOptions o=cpu()){chinese=env.createSession(model.getPath(),o);backend="CPU";}
    run.check();
    }
  }
  private OnnxTensor integers(long[] values,long[] shape)throws Exception{return OnnxTensor.createTensor(env,LongBuffer.wrap(values),shape);}
  String english(String text,Run run)throws Exception{
    long[] input=enTokens.encode(text);if(input.length>128)return text;for(long id:input)if(id==2)return text;
    long[] mask=new long[input.length];Arrays.fill(mask,1);ArrayList<Integer> generated=new ArrayList<>();double minProbability=1;boolean ended=false;
    try(OnnxTensor ids=integers(input,new long[]{1,input.length});OnnxTensor maskTensor=integers(mask,new long[]{1,mask.length});OrtSession.Result encoded=encoder.run(new HashMap<String,OnnxTensor>(){{put("ids",ids);put("mask",maskTensor);}},run.options)){
      OnnxTensor hidden=(OnnxTensor)encoded.get(0);
      for(int step=0;step<Math.min(160,input.length*2+12);step++){
        run.check();long[] prefix=new long[generated.size()+1];for(int i=0;i<generated.size();i++)prefix[i+1]=generated.get(i);
        try(OnnxTensor tokens=integers(prefix,new long[]{1,prefix.length});OrtSession.Result result=decoder.run(new HashMap<String,OnnxTensor>(){{put("ids",tokens);put("hidden",hidden);put("mask",maskTensor);}},run.options)){
          FloatBuffer logits=((OnnxTensor)result.get(0)).getFloatBuffer();int best=0;float max=Float.NEGATIVE_INFINITY;
          for(int i=0;i<logits.limit();i++)if(logits.get(i)>max){max=logits.get(i);best=i;}
          double sum=0;for(int i=0;i<logits.limit();i++)sum+=Math.exp(logits.get(i)-max);minProbability=Math.min(minProbability,1/sum);
          if(best==1){ended=true;break;}if(best<3||best>=enTokens.pieces.length)return text;generated.add(best);
        }
      }
    }
    if(!ended||minProbability<.45)return text;
    String proposed=VoiceText.restoreAcronyms(text,enTokens.decode(generated));
    return VoiceText.acceptableEnglish(text,proposed)?proposed:text;
  }
  private boolean sameSound(String a,String b){Set<String> x=readings.get(a),y=readings.get(b);return x!=null&&y!=null&&!Collections.disjoint(x,y);}
  private boolean protectedHan(String text,int position){
    if("不没未无别零〇一二两三四五六七八九十百千万亿".indexOf(text.charAt(position))>=0)return true;
    // Conservatively protect names immediately before an address/title or speech attribution.
    Matcher names=Pattern.compile("[\\p{IsHan}]{2,3}(?=教授|老师|同学|先生|女士|医生|说|表示|告诉)").matcher(text);while(names.find())if(position>=names.start()&&position<names.end())return true;
    return false;
  }
  String chinese(String text,Run run)throws Exception{
    List<ChineseTokenizer.Token> tokens=zhTokens.encode(text);StringBuilder out=new StringBuilder(text);int edits=0,han=0;for(int i=0;i<text.length();i++)if(Character.UnicodeScript.of(text.charAt(i))==Character.UnicodeScript.HAN)han++;
    // Overlapping windows retain neighbouring words; only each window's core may change.
    for(int core=0;core<tokens.size();core+=96){run.check();int begin=Math.max(0,core-15),end=Math.min(tokens.size(),begin+126);long[] input=new long[128],mask=new long[128];input[0]=101;mask[0]=1;
      for(int i=begin;i<end;i++){input[i-begin+1]=tokens.get(i).id;mask[i-begin+1]=1;}input[end-begin+1]=102;mask[end-begin+1]=1;
      try(OnnxTensor ids=integers(input,new long[]{1,128});OnnxTensor mt=integers(mask,new long[]{1,128});OrtSession.Result result=chinese.run(new HashMap<String,OnnxTensor>(){{put("ids",ids);put("mask",mt);}},run.options)){
        FloatBuffer logits=((OnnxTensor)result.get(0)).getFloatBuffer();int width=zhTokens.vocab.size();
        for(int i=core;i<Math.min(core+96,end);i++){ChineseTokenizer.Token token=tokens.get(i);if(token.end-token.start!=1||protectedHan(text,token.start)||Character.UnicodeScript.of(text.charAt(token.start))!=Character.UnicodeScript.HAN)continue;
          int offset=(i-begin+1)*width,best=0;float max=Float.NEGATIVE_INFINITY;for(int j=0;j<width;j++)if(logits.get(offset+j)>max){max=logits.get(offset+j);best=j;}
          if(best==token.id)continue;String replacement=zhTokens.vocab.get(best),original=text.substring(token.start,token.end);if(replacement.length()!=1||!sameSound(original,replacement))continue;
          double sum=0;for(int j=0;j<width;j++)sum+=Math.exp(logits.get(offset+j)-max);
          if(1/sum>=.98&&max-logits.get(offset+token.id)>=4.6){out.setCharAt(token.start,replacement.charAt(0));edits++;}
        }
      }
    }
    return edits>Math.max(2,han/5)?text:out.toString();
  }
  String correct(String text,Run run,Progress progress,String requestedBackend)throws Exception{
    String result=VoiceText.sentenceCase(text);lastBackend="";
    if(result.matches("(?s).*[\\p{IsHan}].*")){progress.update("正在本地校对中文…");loadChinese(run,requestedBackend);lastBackend="Chinese: "+backend;result=chinese(result,run);}
    Matcher matches=Pattern.compile("[A-Za-z][A-Za-z0-9 ,.!?'’:/@#_+%()\\-]*").matcher(result);StringBuffer out=new StringBuffer();
    while(matches.find()){String segment=matches.group();String replacement=segment;
      if(segment.trim().split("\\s+").length>=3&&!segment.contains("://")&&!segment.contains("@")){progress.update("正在本地校对英文…");loadEnglish(run);if(!lastBackend.contains("English"))lastBackend+=(lastBackend.isEmpty()?"":"; ")+"English: CPU";replacement=english(segment.trim(),run);if(segment.endsWith(" "))replacement+=" ";}
      matches.appendReplacement(out,Matcher.quoteReplacement(replacement));
    }matches.appendTail(out);run.check();return out.toString();
  }
  public void close(){for(OrtSession s:new OrtSession[]{encoder,decoder,chinese})if(s!=null)try{s.close();}catch(Exception ignored){}encoder=decoder=chinese=null;enTokens=null;zhTokens=null;readings.clear();}
}
