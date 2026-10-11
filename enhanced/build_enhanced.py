import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from build_config import JAVA,BT,ANDROID,APKTOOL,SIGN_ARGS
from pathlib import Path
import subprocess,zipfile,struct,sys,shutil
ROOT=Path(__file__).resolve().parent.parent
CP=str(ROOT/'tools'/'dexlib2-2.5.2.jar')+';'+str(ROOT/'tools'/'guava-27.1-android.jar')+';'+str(APKTOOL)+';'+str(ROOT/'tools')
def run(*args):subprocess.run([str(a) for a in args],check=True)
work=ROOT/'enhanced'/'build';work.mkdir(exist_ok=True)
patches=work/'smali';patches.mkdir(exist_ok=True)
original=ROOT/'gboard-18.4.1'/'smali'/'smali_classes2'
rel=Path('com/google/android/libraries/inputmethod/ime/processor/core/BaseDecodeProcessor.smali')
source=(original/rel).read_text(encoding='utf8')
anchor='.method public final el(Lotf;)Z\n    .locals 4\n'
injected='''
    invoke-static {p0, p1}, Llocal/gboard/enhanced/EnhancedHook;->dispatch(Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :local_chinese_fallthrough
    const/4 v0, 0x1
    return v0
    :local_chinese_fallthrough
'''
assert source.count(anchor)==1
(patches/'BaseDecodeProcessor.smali').write_text(source.replace(anchor,anchor+injected),encoding='utf8')
source=(original/'nho.smali').read_text(encoding='utf8')
start=source.index('.method public final e(Ljava/lang/CharSequence;)V')
end=source.index('.end method',start)
body=source[start:end].replace('    .locals 4','    .locals 4\n    invoke-static {p0}, Llocal/gboard/enhanced/EnhancedHook;->bindComposingView(Ljava/lang/Object;)V',1)
(patches/'nho.smali').write_text(source[:start]+body+source[end:],encoding='utf8')
rel=Path('com/google/android/apps/inputmethod/latin/keyboard/widget/LatinFixedCountCandidatesHolderView.smali')
source=(ROOT/'gboard-18.4.1/smali/smali'/rel).read_text('utf8')
start=source.index('.method public final D(Ljava/util/List;Ljava/util/ArrayList;)V');end=source.index('.end method',start)
body=source[start:end].replace('    return-void','    invoke-static/range {p0 .. p0}, Llocal/gboard/enhanced/EnhancedHook;->bindCandidateBar(Landroid/view/View;)V\n    return-void')
source=source[:start]+body+source[end:]
start=source.index('.method private final Q()I');end=source.index('.end method',start)
body=source[start:end].replace('    .locals 3','    .locals 4\n    move-object v3, p0',1)
assert body.count('    return p0')==1
body=body.replace('    return p0','    invoke-static {v3, p0}, Llocal/gboard/enhanced/EnhancedHook;->candidateWidth(Landroid/view/View;I)I\n    move-result p0\n    return p0')
source=source[:start]+body+source[end:]
source+='''
.method protected onLayout(ZIIII)V
    .locals 0
    invoke-super/range {p0 .. p5}, Landroid/widget/LinearLayout;->onLayout(ZIIII)V
    invoke-static {p0}, Llocal/gboard/enhanced/EnhancedHook;->layoutDismissButton(Landroid/view/View;)V
    return-void
.end method
.method protected onAttachedToWindow()V
    .locals 0
    invoke-super {p0}, Landroid/widget/LinearLayout;->onAttachedToWindow()V
    invoke-static {p0}, Llocal/gboard/enhanced/EnhancedHook;->bindCandidateBar(Landroid/view/View;)V
    return-void
.end method
.method protected onVisibilityChanged(Landroid/view/View;I)V
    .locals 0
    invoke-super {p0, p1, p2}, Landroid/widget/LinearLayout;->onVisibilityChanged(Landroid/view/View;I)V
    invoke-static {p0}, Llocal/gboard/enhanced/EnhancedHook;->bindCandidateBar(Landroid/view/View;)V
    return-void
.end method
'''
(patches/'LatinFixedCountCandidatesHolderView.smali').write_text(source,encoding='utf8')
rel=Path('com/google/android/libraries/inputmethod/widgets/SoftKeyboardView.smali')
source=(ROOT/'gboard-18.4.1/smali/smali'/rel).read_text('utf8')
anchor='.method public final dispatchTouchEvent(Landroid/view/MotionEvent;)Z\n    .locals 4\n'
assert source.count(anchor)==1
source=source.replace(anchor,anchor+'''
    invoke-static {p0, p1}, Llocal/gboard/enhanced/KeyboardGestures;->touch(Landroid/view/View;Landroid/view/MotionEvent;)Z
    move-result v0
    if-eqz v0, :local_gesture_fallthrough
    const/4 v0, 0x1
    return v0
    :local_gesture_fallthrough
''')
for method,hook in [('onAttachedToWindow()V','attach'),('onDetachedFromWindow()V','detached')]:
  start=source.index(method);body_start=source.index('\n',source.index('.locals',start))+1
  source=source[:body_start]+'    invoke-static {p0}, Llocal/gboard/enhanced/KeyboardGestures;->'+hook+'(Landroid/view/View;)V\n'+source[body_start:]
(patches/'SoftKeyboardView.smali').write_text(source,encoding='utf8')
source=(ROOT/'gboard-18.4.1'/'smali'/'smali'/'pbb.smali').read_text(encoding='utf8')
anchor='.method public final aw(Loas;)Z\n    .locals 26\n'
assert source.count(anchor)==1
source=source.replace(anchor,anchor+'''
    invoke-static/range {p1 .. p1}, Llocal/gboard/enhanced/EnhancedHook;->handleEnter(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :local_enter_fallthrough
    const/4 v0, 0x1
    return v0
    :local_enter_fallthrough
    invoke-static/range {p0 .. p1}, Llocal/gboard/enhanced/VoiceHook;->handle(Ljava/lang/Object;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :local_voice_fallthrough
    const/4 v0, 0x1
    return v0
    :local_voice_fallthrough
''')
for method in ['onFinishInput()V','onWindowHidden()V']:
  start=source.index('.method public final '+method);body_start=source.index('\n',source.index('.locals',start))+1
  source=source[:body_start]+'    invoke-static/range {p0 .. p0}, Llocal/gboard/enhanced/VoiceHook;->hidden(Ljava/lang/Object;)V\n'+source[body_start:]
start=source.index('.method public final onStartInputView(Landroid/view/inputmethod/EditorInfo;Z)V');body_start=source.index('\n',source.index('.locals',start))+1
source=source[:body_start]+'    invoke-static/range {p0 .. p0}, Llocal/gboard/enhanced/Diagnostics;->install(Ljava/lang/Object;)V\n'+source[body_start:]
(patches/'pbb.smali').write_text(source,encoding='utf8')
run(JAVA/'javac.exe','-cp',APKTOOL,ROOT/'tools'/'Assemble.java')
run(JAVA/'java.exe','-cp',str(APKTOOL)+';'+str(ROOT/'tools'),'Assemble',patches,work/'patched.dex')
classes=work/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir(exist_ok=True)
sources=list((ROOT/'enhanced'/'src').rglob('*.java'))
if '--test' in sys.argv:sources+=list((ROOT/'enhanced/testsrc').rglob('*.java'))
run(JAVA/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-cp',ANDROID,'-d',classes,*sources)
run(JAVA/'jar.exe','cf',work/'helper.jar','-C',classes,'.')
dex=work/'helper-dex';dex.mkdir(exist_ok=True)
run(BT/'d8.bat','--min-api','32','--lib',ANDROID,'--output',dex,work/'helper.jar',work/'patched.dex')
raw=work/'original-dex';raw.mkdir(exist_ok=True)
fixed=work/'fixed-dex';fixed.mkdir(exist_ok=True)
base=ROOT/'enhanced'/'baseline'/'base.apk'
with zipfile.ZipFile(base) as z:
  for name in ['classes.dex','classes2.dex']:(raw/name).write_bytes(z.read(name))
run(JAVA/'java.exe','-Xmx2G','-cp',CP,'DexTool',raw,fixed,work/'patched.dex')
unsigned=work/'enhanced.apk'
with zipfile.ZipFile(base) as z,zipfile.ZipFile(unsigned,'w') as out:
  for info in z.infolist():
    if info.filename.startswith('META-INF/'):continue
    content=(fixed/info.filename).read_bytes() if info.filename in ['classes.dex','classes2.dex'] else z.read(info)
    if info.filename=='AndroidManifest.xml':
      sys.path.insert(0,str(ROOT));from add_activity import add_activity,add_voice_access,management_launcher
      content=management_launcher(add_voice_access(add_activity(content,'local.gboard.enhanced.VoicePermissionActivity')))
      if '--test' in sys.argv:
        content=add_activity(content,'local.gboard.enhanced.AdapterCheckActivity',True)
        content=add_activity(content,'local.gboard.enhanced.GestureCheckActivity',True)
    out.writestr(info,content)
  out.write(dex/'classes.dex','classes5.dex',compress_type=zipfile.ZIP_DEFLATED)
  for name in ['libgboard_local_rime.so','librime.so','libFcitx5Core.so','libFcitx5Config.so','libFcitx5Utils.so','libc++_shared.so','libgboard_local_handwriting.so']:
    out.write(ROOT/'enhanced'/'native'/'lib'/'arm64-v8a'/name,'lib/arm64-v8a/'+name,compress_type=zipfile.ZIP_STORED)
  for f in (ROOT/'enhanced'/'rime-data').rglob('*'):
    if f.is_file() and f.name!='word-index.bin':out.write(f,'assets/local-rime/'+f.relative_to(ROOT/'enhanced'/'rime-data').as_posix(),compress_type=zipfile.ZIP_STORED if f.suffix=='.gram' else zipfile.ZIP_DEFLATED)
  out.write(ROOT/'enhanced/handwriting-data/handwriting-zh_CN.model','assets/local-handwriting/handwriting-zh_CN.model',compress_type=zipfile.ZIP_DEFLATED)
  for name in ['ZINNIA-LICENSE','TEGAKI-LICENSE','HANDWRITING-SOURCES.md']:out.write(ROOT/'enhanced'/name,'assets/licenses/'+name,compress_type=zipfile.ZIP_DEFLATED)
out=ROOT/'enhanced'/'release';out.mkdir(exist_ok=True)
run(BT/'zipalign.exe','-P','16','-f','4',unsigned,out/'aligned-base.apk')
run(BT/'apksigner.bat','sign',*SIGN_ARGS,'--out',out/'base.apk',out/'aligned-base.apk')
for split in (ROOT/'enhanced'/'baseline').glob('split_*.apk'):(out/split.name).write_bytes(split.read_bytes())
run(BT/'apksigner.bat','verify',out/'base.apk')
print(out/'base.apk')
