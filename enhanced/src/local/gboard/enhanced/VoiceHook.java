package local.gboard.enhanced;
import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.*;
import android.view.WindowManager;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.reflect.Method;
import java.io.File;
import java.util.ArrayList;
/** Keep speech computation outside the keyboard; bind only our offline service. */
public final class VoiceHook {
  public static final String HELPER="local.gboard.voice";
  private static Run active;private static Method eventCode;
  private static class Run {
    InputMethodService service;InputConnection connection;EditorInfo editor;
    AlertDialog dialog;TextView status;SpeechRecognizer recognizer;boolean started,cancel,fixture;
  }
  public static boolean handle(Object owner,Object event){
    if(!(owner instanceof InputMethodService)||event==null)return false;
    try {
      Method method=eventCode;if(method==null){method=event.getClass().getDeclaredMethod("a");method.setAccessible(true);eventCode=method;}
      int code=(Integer)method.invoke(event);if(code!=-10042&&code!=-10217&&code!=-10108)return false;
      InputMethodService service=(InputMethodService)owner;EditorInfo editor=service.getCurrentInputEditorInfo();
      if(editor==null)return true;Diagnostics.trace(service,"voice command; editor="+editor.packageName);
      int variation=editor.inputType&0xfff;
      if((editor.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0||variation==0x81||variation==0x91||variation==0xe1||variation==0x12){Toast.makeText(service,"隐私或密码输入中不启用麦克风",Toast.LENGTH_SHORT).show();return true;}
      if(service.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){service.startActivity(new Intent(service,VoicePermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
      if(service.getPackageManager().checkSignatures(service.getPackageName(),HELPER)!=PackageManager.SIGNATURE_MATCH){Toast.makeText(service,"请安装配套的中英离线语音服务",Toast.LENGTH_LONG).show();return true;}
      if(service.getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO,HELPER)!=PackageManager.PERMISSION_GRANTED){service.startActivity(new Intent().setComponent(new ComponentName(HELPER,"local.gboard.voice.PermissionActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
      if(active!=null)return true;EnhancedHook.finishActive();show(service,editor);return true;
    }catch(Throwable failure){if(active!=null&&active.service==owner){cancel(active);active=null;}android.util.Log.e("LocalVoice","Voice panel failed",failure);Toast.makeText((Context)owner,"本地语音暂不可用",Toast.LENGTH_SHORT).show();return true;}
  }
  private static void show(InputMethodService service,EditorInfo editor){
    Run run=new Run();run.service=service;run.editor=editor;run.connection=service.getCurrentInputConnection();active=run;
    run.status=new TextView(service);run.status.setTextSize(19);run.status.setPadding(36,28,36,28);run.status.setText("支持普通话和英文混说。点击开始后录音，音频不保存。首次载入模型需要几秒。");
    run.dialog=new AlertDialog.Builder(service).setTitle("本地语音输入").setView(run.status).setPositiveButton("开始",null).setNegativeButton("取消",(d,w)->cancel(run)).create();
    run.dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
    run.dialog.getWindow().getAttributes().token=service.getWindow().getWindow().getDecorView().getWindowToken();
    run.dialog.setOnCancelListener(d->cancel(run));run.dialog.show();Diagnostics.trace(service,"dialog shown");
    run.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(run.started){run.recognizer.stopListening();run.status.setText("正在结束识别…");v.setEnabled(false);}else record(run);});
  }
  static void startFixture(){Run run=active;if(run!=null&&Diagnostics.isDebug(run.service)&&new File(run.service.getNoBackupFilesDir(),"voice-test.pcm").isFile()){run.fixture=true;new File(run.service.getNoBackupFilesDir(),"voice-test.pcm").delete();run.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();}}
  private static void cancel(Run run){run.cancel=true;if(run.recognizer!=null){run.recognizer.cancel();run.recognizer.destroy();run.recognizer=null;}if(active==run)active=null;}
  public static void hidden(Object owner){Run run=active;if(run!=null&&run.service==owner){Diagnostics.trace(run.service,"keyboard hidden; cancelling");cancel(run);run.dialog.dismiss();}}
  private static void record(Run run){
    run.started=true;run.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("完成");run.status.setText("正在载入离线语音…");
    run.recognizer=SpeechRecognizer.createSpeechRecognizer(run.service,new ComponentName(HELPER,"local.gboard.voice.OfflineRecognitionService"));
    run.recognizer.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle b){if(!run.cancel)run.status.setText("正在听，可混说普通话和英文…");}
      public void onBeginningOfSpeech(){}
      public void onRmsChanged(float value){}
      public void onBufferReceived(byte[] audio){}
      public void onEndOfSpeech(){if(!run.cancel)run.status.setText("正在结束识别…");}
      public void onPartialResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(!run.cancel&&words!=null&&!words.isEmpty())run.status.setText(words.get(0));}
      public void onEvent(int type,Bundle b){}
      public void onError(int error){if(run.cancel)return;Diagnostics.trace(run.service,"recognition error="+error);run.status.setText("离线识别未完成，请取消后重试。");run.started=false;run.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);if(run.recognizer!=null){run.recognizer.destroy();run.recognizer=null;}}
      public void onResults(Bundle b){if(run.cancel)return;ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String text=words==null||words.isEmpty()?"":words.get(0);
        if(run.service.getCurrentInputEditorInfo()!=run.editor){Diagnostics.trace(run.service,"editor changed; cancelled");cancel(run);run.dialog.dismiss();return;}
        if(!text.isEmpty()&&run.connection!=null){boolean ok=run.connection.commitText(text,1);Diagnostics.trace(run.service,"commit result="+ok+"; chars="+text.length());}else Toast.makeText(run.service,"没有识别到语音",Toast.LENGTH_SHORT).show();
        active=null;run.recognizer.destroy();run.recognizer=null;run.dialog.dismiss();
      }
    });
    Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra("local_fixture",run.fixture);
    run.recognizer.startListening(request);
  }
}
