"""Read fixed synthetic test outputs from the disposable, same-signature probe UI."""
import argparse,subprocess,time,xml.etree.ElementTree as E,re,base64,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--serial',required=True);p.add_argument('--backend',choices=['cpu','npu','gpu','auto'],default='auto');a=p.parse_args()
def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True,encoding='utf8',errors='replace')
adb('shell','am','force-stop','local.gboard.probe')
adb('shell','am','start','-n','local.gboard.probe/.ProofCheckActivity','--es','backend',a.backend)
deadline=time.monotonic()+180
while time.monotonic()<deadline:
 time.sleep(3);s=adb('exec-out','uiautomator','dump','/dev/tty')
 if '<?xml' not in s:continue
 try:root=E.fromstring(s[s.index('<?xml'):s.index('</hierarchy>')+12])
 except Exception:continue
 text='\n'.join(n.attrib.get('text','') for n in root.iter('node'))
 if 'case=14 ' in text:break
else:raise TimeoutError('Synthetic probe did not finish: '+text[:200])
rows=[]
for line in text.splitlines():
 m=re.search(r'case=(\d+) backend=(.*?) ms=(\d+) error=(.*?) text64=(\S+)',line)
 if m:rows.append({'case':int(m[1]),'backend':m[2],'ms':int(m[3]),'error':m[4],'output':base64.b64decode(m[5]).decode('utf8')})
report={'device':'Snapdragon 8 Elite Android phone' if not a.serial.startswith('emulator-') else 'AVD','requested_backend':a.backend,'synthetic_only':True,'cases':rows}
if a.backend!='gpu':
 expected={0:'我今天去图书馆看书。',1:'这个项目已经完成了。',2:'请把会议记录发给我。',4:'张伟教授说订单编号B27不要改成B72。',5:'我不想取消明天的预约。',6:'我的final year project需要GPU加速，订单A123保持不变。',7:'She goes to school every day.',8:'We need to meet before the deadline.',11:'I phone my professor every Friday.',12:'I am studying AI and my GPU is an RTX 4090.',13:'Please do not change order B27 to B72.'}
 assert len(rows)==15 and all(not r['error'] for r in rows),rows
 assert all(rows[i]['output']==value for i,value in expected.items()),'Correction/protection regression'
 if a.backend=='npu':assert all('Hexagon NPU' in rows[i]['backend'] for i in range(7))
 report.update({'checked_corrections_and_protected_cases':list(expected),'passed':True,'limitation':'Small synthetic regression set, not a general accuracy benchmark. Ambiguous errors may be preserved.'})
out=Path(__file__).parent/'results'/('proofread-'+a.backend+'-v1.3.0.json');out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8')
print(json.dumps(report,ensure_ascii=True,indent=2))
