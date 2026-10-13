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
  public static boolean panelKeepsEditorFocus,restartUpdatedEditor,commitRejected;private boolean launched;
  private TextView keyboard;
  public static final class VoiceEvent {public int a(){return -10042;}}
  public View onCreateInputView(){TextView view=new TextView(this);view.setText("AVD voice hook test");view.setGravity(Gravity.CENTER);view.setMinHeight(150);keyboard=view;return view;}
  public void onStartInputView(EditorInfo editor,boolean restarting){super.onStartInputView(editor,restarting);
    if(!"local.gboard.voicecheck".equals(editor.packageName)||launched)return;
    new Handler(Looper.getMainLooper()).postDelayed(()->{try{
      if(getCurrentInputEditorInfo()!=editor||!isInputViewShown())return;
      if(getSharedPreferences("checks",0).getBoolean("lens",false)){checkLens();return;}
      try(FileOutputStream out=new FileOutputStream(new File(getNoBackupFilesDir(),"voice-test.pcm"))){out.write(1);}
      VoiceHook.handle(this,new VoiceEvent());Field f=VoiceHook.class.getDeclaredField("active");f.setAccessible(true);Object run=f.get(null);
      if(run!=null){launched=true;Field started=run.getClass().getDeclaredField("started");started.setAccessible(true);startsImmediately=started.getBoolean(run);Field dialog=run.getClass().getDeclaredField("dialog");dialog.setAccessible(true);panelKeepsEditorFocus=(((android.app.AlertDialog)dialog.get(run)).getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0;}
    }catch(Exception error){android.util.Log.e("HookCheck","Failed",error);}},700);
  }
  public void onStartInput(EditorInfo editor,boolean restarting){VoiceHook.inputStarted(this,editor,restarting);super.onStartInput(editor,restarting);if(!restarting){launched=false;startsImmediately=panelKeepsEditorFocus=restartUpdatedEditor=false;}else if(launched)restartUpdatedEditor=true;}
  public InputConnection getCurrentInputConnection(){InputConnection current=super.getCurrentInputConnection();if(current==null||!getSharedPreferences("checks",0).getBoolean("reject_commit",false))return current;
    return new InputConnectionWrapper(current,false){public boolean commitText(CharSequence text,int cursor){if(!commitRejected){commitRejected=true;return false;}return super.commitText(text,cursor);}};
  }
  public void onFinishInput(){VoiceHook.hidden(this);super.onFinishInput();}
  public void onWindowHidden(){VoiceHook.hidden(this);super.onWindowHidden();}
  private void checkLens(){org.json.JSONObject report=new org.json.JSONObject();try{
    Class<?> hook=Class.forName("local.gboard.enhanced.EnhancedHook"),session=Class.forName("local.gboard.enhanced.EnhancedHook$Session");java.lang.reflect.Constructor<?> ctor=session.getDeclaredConstructor(Object.class);ctor.setAccessible(true);Object state=ctor.newInstance(new Object());Field raw=session.getDeclaredField("raw"),caret=session.getDeclaredField("caret"),active=hook.getDeclaredField("active");raw.setAccessible(true);caret.setAccessible(true);active.setAccessible(true);raw.set(state,"ceshishurufa");caret.setInt(state,5);active.set(null,state);
    Class<?> lens=Class.forName("local.gboard.enhanced.ComposingLens");Method show=lens.getDeclaredMethod("show",View.class),visible=lens.getDeclaredMethod("visible"),hide=lens.getDeclaredMethod("hide");show.setAccessible(true);visible.setAccessible(true);hide.setAccessible(true);show.invoke(null,keyboard);boolean shown=(Boolean)visible.invoke(null);boolean connected=getCurrentInputConnection()!=null&&isInputViewShown();report.put("attached_to_real_ime_window",keyboard.getWindowToken()!=null).put("popup_shown",shown).put("input_connection_preserved",connected).put("passed",shown&&connected);hide.invoke(null);active.set(null,null);
  }catch(Throwable error){try{report.put("passed",false).put("error",android.util.Log.getStackTraceString(error));}catch(Exception ignored){}}
    try(FileOutputStream out=openFileOutput("lens-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}catch(Exception e){throw new RuntimeException(e);}getSharedPreferences("checks",0).edit().remove("lens").commit();
  }
}
