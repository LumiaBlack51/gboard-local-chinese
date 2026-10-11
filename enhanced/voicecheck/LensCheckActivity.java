package local.gboard.voicecheck;
import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
/** Synthetic editor checks the composing popup on a real IME window, without native decoding. */
public final class LensCheckActivity extends Activity {
  private EditText editor;
  public void onCreate(Bundle state){super.onCreate(state);getSharedPreferences("checks",0).edit().putBoolean("lens",true).commit();editor=new EditText(this);editor.setHint("AVD pinyin magnifier window check");setContentView(editor);}
  public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){editor.requestFocus();editor.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT));}}
}
