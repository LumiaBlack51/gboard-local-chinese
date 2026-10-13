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
  private EditText editor;private TextView status;private boolean saved,requested,retryRetainedText;private int focusLost;private final Handler ui=new Handler(Looper.getMainLooper());
  public void onCreate(Bundle state){super.onCreate(state);getSharedPreferences("checks",0).edit().putBoolean("reject_commit",getIntent().getBooleanExtra("retry",false)).putBoolean("undo",getIntent().getBooleanExtra("undo",false)).commit();HookCheckService.commitRejected=false;HookCheckService.startsImmediately=false;LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);
    status=new TextView(this);status.setText("AVD direct voice commit check");layout.addView(status);
    editor=new EditText(this);editor.setInputType(InputType.TYPE_CLASS_TEXT);editor.setSaveEnabled(false);layout.addView(editor);setContentView(layout);
    editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){}
      public void afterTextChanged(Editable text){if(text.length()>0&&!getIntent().getBooleanExtra("undo",false))new Handler(Looper.getMainLooper()).postDelayed(()->save(false),300);}});
    new Handler(Looper.getMainLooper()).postDelayed(()->save(true),90000);
    if(getIntent().getBooleanExtra("restart",false))ui.postDelayed(this::restartWhenRecording,1000);
    if(getIntent().getBooleanExtra("retry",false))ui.postDelayed(this::retryWhenReady,1000);
  }
  private void retryWhenReady(){if(saved)return;try{Field f=VoiceHook.class.getDeclaredField("active");f.setAccessible(true);Object run=f.get(null);if(run!=null){Field dialog=run.getClass().getDeclaredField("dialog"),result=run.getClass().getDeclaredField("result");dialog.setAccessible(true);result.setAccessible(true);android.app.AlertDialog panel=(android.app.AlertDialog)dialog.get(run);Button retry=panel.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
      if("填入文字".contentEquals(retry.getText())){retryRetainedText=editor.length()==0&&"今天下午召开会议请注意保护隐私。".equals(result.get(run));retry.performClick();return;}}
    }catch(Exception e){throw new RuntimeException(e);}ui.postDelayed(this::retryWhenReady,100);}
  private void restartWhenRecording(){if(saved)return;if(!HookCheckService.startsImmediately){ui.postDelayed(this::restartWhenRecording,100);return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).restartInput(editor);}
  private void showIme(){if(saved||!hasWindowFocus()||HookCheckService.startsImmediately)return;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);android.view.WindowInsetsController insets=getWindow().getInsetsController();if(insets!=null)insets.show(android.view.WindowInsets.Type.ime());ui.postDelayed(this::showIme,300);}
  public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&HookCheckService.startsImmediately)focusLost++;if(focus&&!requested){requested=true;editor.requestFocus();editor.postDelayed(this::showIme,300);}}
  private void save(boolean timeout){if(saved)return;saved=true;try{Field f=VoiceHook.class.getDeclaredField("active");f.setAccessible(true);
    JSONObject report=new JSONObject().put("starts_without_confirmation",HookCheckService.startsImmediately).put("recognized",editor.getText().toString())
      .put("direct_commit_without_confirmation",!getIntent().getBooleanExtra("retry",false)&&editor.getText().toString().equals("今天下午召开会议请注意保护隐私。")).put("panel_closed_after_result",f.get(null)==null).put("timeout",timeout);
    report.put("panel_keeps_editor_focus",HookCheckService.panelKeepsEditorFocus&&focusLost==0).put("same_editor_restart",HookCheckService.restartUpdatedEditor);
    report.put("failed_commit_retains_text",retryRetainedText);
    report.put("passed",!timeout&&HookCheckService.startsImmediately&&editor.getText().toString().equals("今天下午召开会议请注意保护隐私。")&&report.getBoolean("panel_closed_after_result")&&report.getBoolean("panel_keeps_editor_focus")&&(!getIntent().getBooleanExtra("restart",false)||HookCheckService.restartUpdatedEditor)&&(!getIntent().getBooleanExtra("retry",false)||retryRetainedText));
    try(FileOutputStream out=openFileOutput("hook-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}status.setText(report.toString(2));
  }catch(Exception e){throw new RuntimeException(e);}}
}
