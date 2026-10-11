package local.gboard.enhanced;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import java.lang.reflect.Method;
import java.io.File;
/** Debug builds only. Shell diagnostics are restricted to our synthetic editor. */
public final class Diagnostics {
  private static boolean installed;
  private static InputMethodService currentService;
  static boolean isDebug(Context context){try{return (context.getPackageManager().getApplicationInfo(context.getPackageName(),0).flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}catch(Exception missing){return false;}}
  static void trace(Context context,String state){if(!isDebug(context))return;try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(context.getNoBackupFilesDir(),"voice-debug-state.txt"),true)){out.write((state+"\n").getBytes("UTF-8"));}catch(Exception ignored){}}
  public static void install(Object owner){
    if(!(owner instanceof InputMethodService))return;InputMethodService ownerService=(InputMethodService)owner;
    if(!isDebug(ownerService))return;currentService=ownerService;if(installed)return;installed=true;
    android.util.Log.w("LocalVoice","Diagnostic receiver registered for synthetic editor");
    ownerService.getApplicationContext().registerReceiver(new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
      InputMethodService service=currentService;if(service==null)return;
      EditorInfo editor=service.getCurrentInputEditorInfo();
      if(editor==null||!"local.gboard.probe".equals(editor.packageName)||!service.isInputViewShown()){android.util.Log.w("LocalVoice","Fixture skipped: diagnostic editor not active");return;}
      if(!new File(service.getNoBackupFilesDir(),"voice-test.pcm").isFile()){android.util.Log.w("LocalVoice","Fixture skipped: no local test audio");return;}
      trace(service,"fixture window attached="+service.getWindow().getWindow().getDecorView().isAttachedToWindow()+"; token="+(service.getWindow().getWindow().getDecorView().getWindowToken()!=null));
      try{
        Method factory=Class.forName("oas").getDeclaredMethod("e",int.class,Object.class);factory.setAccessible(true);Object event=factory.invoke(null,-10042,"");
        VoiceHook.handle(service,event);
      }catch(Throwable error){android.util.Log.e("LocalVoice","Diagnostic fixture failed",error);}
    }},new IntentFilter("local.gboard.enhanced.TEST_FIXTURE"),"android.permission.DUMP",null,Context.RECEIVER_EXPORTED);
  }
}
