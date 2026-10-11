package local.gboard.voicecheck;
import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import local.gboard.enhanced.VoiceHook;
/** AVD-only test IME exercises the actual shipped voice panel in an attached IME window. */
public final class HookCheckService extends InputMethodService {
  public static boolean startsImmediately;
  public static final class VoiceEvent {public int a(){return -10042;}}
  public View onCreateInputView(){TextView view=new TextView(this);view.setText("AVD voice hook test");view.setGravity(Gravity.CENTER);view.setMinHeight(150);return view;}
  public void onStartInputView(EditorInfo editor,boolean restarting){super.onStartInputView(editor,restarting);
    if(!"local.gboard.voicecheck".equals(editor.packageName))return;
    new Handler(Looper.getMainLooper()).postDelayed(()->{try{
      if(getCurrentInputEditorInfo()!=editor||!isInputViewShown())return;
      try(FileOutputStream out=new FileOutputStream(new File(getNoBackupFilesDir(),"voice-test.pcm"))){out.write(1);}
      VoiceHook.handle(this,new VoiceEvent());Field f=VoiceHook.class.getDeclaredField("active");f.setAccessible(true);Object run=f.get(null);
      if(run!=null){Field started=run.getClass().getDeclaredField("started");started.setAccessible(true);startsImmediately=started.getBoolean(run);}
    }catch(Exception error){android.util.Log.e("HookCheck","Failed",error);}},700);
  }
  public void onFinishInput(){VoiceHook.hidden(this);super.onFinishInput();}
  public void onWindowHidden(){VoiceHook.hidden(this);super.onWindowHidden();}
}
