package local.gboard.voicecheck;
import android.app.Activity;
import android.os.*;
import android.text.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import local.gboard.enhanced.VoiceHook;
/** No taps after the synthetic microphone event: the recognized fixture must commit itself. */
public final class HookCheckActivity extends Activity {
  private EditText editor;private TextView status;private boolean saved;
  public void onCreate(Bundle state){super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);
    status=new TextView(this);status.setText("AVD direct voice commit check");layout.addView(status);
    editor=new EditText(this);editor.setInputType(InputType.TYPE_CLASS_TEXT);editor.setSaveEnabled(false);layout.addView(editor);setContentView(layout);
    editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){}
      public void afterTextChanged(Editable text){if(text.length()>0)new Handler(Looper.getMainLooper()).postDelayed(()->save(false),300);}});
    new Handler(Looper.getMainLooper()).postDelayed(()->save(true),90000);
  }
  public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){editor.requestFocus();editor.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));}}
  private void save(boolean timeout){if(saved)return;saved=true;try{Field f=VoiceHook.class.getDeclaredField("active");f.setAccessible(true);
    JSONObject report=new JSONObject().put("starts_without_confirmation",HookCheckService.startsImmediately).put("recognized",editor.getText().toString())
      .put("direct_commit_without_confirmation",editor.getText().toString().equals("今天下午召开会议请注意保护隐私")).put("panel_closed_after_result",f.get(null)==null).put("timeout",timeout);
    report.put("passed",!timeout&&HookCheckService.startsImmediately&&report.getBoolean("direct_commit_without_confirmation")&&report.getBoolean("panel_closed_after_result"));
    try(FileOutputStream out=openFileOutput("hook-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}status.setText(report.toString(2));
  }catch(Exception e){throw new RuntimeException(e);}}
}
