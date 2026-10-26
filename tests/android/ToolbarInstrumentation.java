package local.gboard.toolbarcheck;

import android.app.Instrumentation;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/** Temporary, same-signature inspection restricted to the synthetic private editor. */
public final class ToolbarInstrumentation extends Instrumentation {
  private String mode;
  public void onCreate(Bundle args){super.onCreate(args);mode=args.getString("mode","inspect");start();}
  private String state()throws Exception{
    org.json.JSONObject saved=new org.json.JSONObject();
    saved.put("settings",new org.json.JSONObject(new java.util.TreeMap<>(getTargetContext().getSharedPreferences("local-enhancement-settings",0).getAll())).toString());
    java.io.File dictionary=new java.io.File(getTargetContext().getNoBackupFilesDir(),"local-dictionary/words.tsv");
    java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
    if(dictionary.isFile())try(java.io.InputStream input=new java.io.FileInputStream(dictionary)){byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1)digest.update(bytes,0,n);}
    saved.put("dictionary_exists",dictionary.isFile());saved.put("dictionary",android.util.Base64.encodeToString(digest.digest(),android.util.Base64.NO_WRAP));return saved.toString();
  }
  private void main(Runnable work)throws Exception{
    final Throwable[] error=new Throwable[1];
    super.runOnMainSync(()->{try{work.run();}catch(Throwable failure){error[0]=failure;}});
    if(error[0]!=null)throw new Exception(error[0]);
  }
  private InputMethodService service()throws Exception{
    Field field=Class.forName("local.gboard.enhanced.ToolbarHide").getDeclaredField("current");field.setAccessible(true);
    return (InputMethodService)((WeakReference<?>)field.get(null)).get();
  }
  private void shell(String command)throws Exception{
    android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command);
    try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){byte[] bytes=new byte[1024];while(input.read(bytes)!=-1){}}
  }
  private void tree(View view,StringBuilder out,int depth){
    if(view.getVisibility()!=View.VISIBLE)return;
    for(int i=0;i<depth;i++)out.append(' ');
    out.append(view.getClass().getName()).append(" ").append(view.getLeft()).append(',').append(view.getTop()).append('-').append(view.getRight()).append(',').append(view.getBottom());
    out.append(" id=").append(view.getId()).append(" desc=").append(view.getContentDescription()).append("\n");
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)tree(group.getChildAt(i),out,depth+1);}
  }
  private View find(View node,String label){
    if(!node.isShown())return null;
    if(label.equals(String.valueOf(node.getContentDescription()))||node instanceof android.widget.TextView&&label.equals(((android.widget.TextView)node).getText().toString()))return node;
    if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}
    return null;
  }
  private void tap(View view)throws Exception{
    int[] location=new int[2];main(()->view.getLocationOnScreen(location));
    int x=location[0]+view.getWidth()/2,y=location[1]+view.getHeight()/2;
    shell("input tap "+x+" "+y);
  }
  public void onStart(){Bundle result=new Bundle();try{
    java.io.File snapshot=new java.io.File(getTargetContext().getNoBackupFilesDir(),"toolbar-update-check.json");
    if(mode.equals("snapshot")){try(java.io.FileOutputStream output=new java.io.FileOutputStream(snapshot)){output.write(state().getBytes("UTF-8"));}result.putString("report","Settings and imported dictionary fingerprint recorded privately on device.");finish(0,result);return;}
    if(mode.equals("retention")){
      String before=new String(java.nio.file.Files.readAllBytes(snapshot.toPath()),"UTF-8");
      if(!before.equals(state()))throw new Exception("Settings or imported dictionary changed during replacement");
      if(!snapshot.delete())throw new Exception("Cannot remove private comparison snapshot");
      result.putString("report","Settings and imported dictionary preserved.");finish(0,result);return;
    }
    shell("ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME");
    shell("ime set com.localx.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME");
    shell("am start -f 0x10008000 -n local.gboard.probe/.MainActivity --ez private true");
    InputMethodService ime=null;
    for(int i=0;i<100;i++){ime=service();if(ime!=null&&ime.isInputViewShown())break;SystemClock.sleep(100);}
    if(ime==null)throw new Exception("Keyboard service unavailable");
    final InputMethodService target=ime;SystemClock.sleep(1500);
    main(()->{
      if(target.getCurrentInputEditorInfo()==null||!"local.gboard.probe".equals(target.getCurrentInputEditorInfo().packageName))throw new IllegalStateException("Synthetic editor required");
      StringBuilder output=new StringBuilder();tree(target.getWindow().getWindow().getDecorView(),output,0);result.putString("tree",output.toString());
    });
    if(mode.equals("verify")){
      final View[] controls=new View[4];
      main(()->{try{
        Class<?> hook=Class.forName("local.gboard.enhanced.ToolbarHide");
        Field button=hook.getDeclaredField("button"),slot=hook.getDeclaredField("displaced"),voice=hook.getDeclaredField("nativeVoice");button.setAccessible(true);slot.setAccessible(true);voice.setAccessible(true);
        controls[0]=(View)((WeakReference<?>)button.get(null)).get();controls[1]=(View)((WeakReference<?>)slot.get(null)).get();
        controls[3]=(View)((WeakReference<?>)voice.get(null)).get();
        if(controls[0]==null||!controls[0].isShown()||!controls[0].isClickable())throw new IllegalStateException("Hide button unavailable");
        if(controls[1]==null||!"设置".equals(String.valueOf(controls[1].getContentDescription()))||controls[1].getVisibility()!=View.INVISIBLE)throw new IllegalStateException("Settings slot was not replaced");
        ViewGroup row=(ViewGroup)controls[1].getParent();
        android.graphics.Rect hide=new android.graphics.Rect();controls[0].getGlobalVisibleRect(hide);
        for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);if("单手模式".equals(String.valueOf(child.getContentDescription())))controls[2]=child;
          if(child.getVisibility()==View.VISIBLE){android.graphics.Rect bounds=new android.graphics.Rect();child.getGlobalVisibleRect(bounds);if(android.graphics.Rect.intersects(hide,bounds))throw new IllegalStateException("Hide button overlaps another action");}}
        if(controls[2]==null||!controls[2].isShown())throw new IllegalStateException("One-handed action missing");
        if(controls[3]==null||!controls[3].isShown())throw new IllegalStateException("Native voice action missing");
        android.graphics.Rect microphone=new android.graphics.Rect(),header=new android.graphics.Rect();controls[3].getGlobalVisibleRect(microphone);((View)controls[0].getParent()).getGlobalVisibleRect(header);
        if(hide.left<microphone.right||Math.abs(hide.exactCenterY()-microphone.exactCenterY())>1||header.right-hide.right>3*getTargetContext().getResources().getDisplayMetrics().density)throw new IllegalStateException("Hide must be right of voice, at the right edge, and vertically centered");
        result.putString("geometry","voice="+microphone.toShortString()+"; hide="+hide.toShortString()+"; header="+header.toShortString());
      }catch(Exception error){throw new RuntimeException(error);}});
      tap(controls[0]);
      SystemClock.sleep(700);if(target.isInputViewShown())throw new Exception("Tapping hide did not dismiss keyboard");
      shell("am start -f 0x10008000 -n local.gboard.probe/.MainActivity --ez private true");SystemClock.sleep(1200);
      main(()->{if(!target.isInputViewShown()||!controls[0].isShown())throw new IllegalStateException("Hide button did not return after reopening");});
      final View[] menu=new View[1];main(()->menu[0]=find(target.getWindow().getWindow().getDecorView(),"打开功能菜单"));
      if(menu[0]==null)throw new Exception("Feature menu unavailable");tap(menu[0]);SystemClock.sleep(700);
      shell("screencap -p /data/local/tmp/toolbar-menu.png");
      main(()->{StringBuilder dump=new StringBuilder();tree(target.getWindow().getWindow().getDecorView(),dump,0);result.putString("menu_tree",dump.toString());});
      main(()->{if(find(target.getWindow().getWindow().getDecorView(),"设置")==null)throw new IllegalStateException("Settings unavailable in feature menu");});
      sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
      shell("am start -f 0x10008000 -n local.gboard.probe/.MainActivity --ez private true");SystemClock.sleep(1000);
      shell("input text nihao");SystemClock.sleep(500);
      main(()->{if(controls[0].isShown())throw new IllegalStateException("Hide button covers the composition candidates");});
      for(int i=0;i<5;i++)sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL);
      SystemClock.sleep(500);
      main(()->{if(!controls[0].isShown())throw new IllegalStateException("Hide button missing after clearing composition");});
      shell("am start -f 0x10008000 -n local.gboard.probe/.MainActivity --ez private false");SystemClock.sleep(1000);
      main(()->{try{
        View voice=controls[3],root=voice;while(root!=null&&!root.getClass().getName().equals("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView"))root=root.getParent() instanceof View?(View)root.getParent():null;
        if(root==null||!voice.isEnabled())throw new IllegalStateException("Normal editor voice unavailable");
        int[] a=new int[2],b=new int[2];voice.getLocationOnScreen(a);root.getLocationOnScreen(b);long now=SystemClock.uptimeMillis();
        android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,0,a[0]-b[0]+voice.getWidth()/2f,a[1]-b[1]+voice.getHeight()/2f,0);
        try{Object hit=root.getClass().getMethod("A",android.view.MotionEvent.class,int.class).invoke(root,event,0);if(hit!=voice)throw new IllegalStateException("Native voice hit target did not move with microphone");}
        finally{Field pointers=root.getClass().getDeclaredField("d");pointers.setAccessible(true);((android.util.SparseArray<?>)pointers.get(root)).remove(0);event.recycle();}
      }catch(Exception error){throw new RuntimeException(error);}});
      shell("am start -f 0x10008000 -n local.gboard.probe/.MainActivity --ez private true");SystemClock.sleep(700);
      shell("screencap -p /data/local/tmp/toolbar-rightmost.png");
      result.putString("report","PASS: hide right of native voice at right edge; vertical centers aligned; native microphone hit target moved; one-handed action visible; real tap hides; reopening and candidates restore; settings in menu.");
    }
    finish(0,result);
  }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}}
}
