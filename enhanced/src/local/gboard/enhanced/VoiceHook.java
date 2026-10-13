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
    InputMethodService service;EditorInfo editor;
    AlertDialog dialog;TextView status;SpeechRecognizer recognizer;MicrophonePipe audio;ProofreadClient proof;boolean started,cancel,fixture,failed;String result="",original="";
  }
  public static boolean handle(Object owner,Object event){
    if(!(owner instanceof InputMethodService)||event==null)return false;
    try {
      Method method=eventCode;if(method==null){method=event.getClass().getDeclaredMethod("a");method.setAccessible(true);eventCode=method;}
      int code=(Integer)method.invoke(event);VoiceUndo.clear();if(code!=-10042&&code!=-10217&&code!=-10108)return false;
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
    Run run=new Run();run.service=service;run.editor=editor;active=run;
    run.status=new TextView(service);run.status.setTextSize(19);run.status.setPadding(36,28,36,28);
    run.fixture=Diagnostics.isDebug(service)&&new File(service.getNoBackupFilesDir(),"voice-test.pcm").isFile();
    if(run.fixture)new File(service.getNoBackupFilesDir(),"voice-test.pcm").delete();
    String title="本地语音输入";try{String version=service.getPackageManager().getPackageInfo(service.getPackageName(),0).versionName;int local=version.indexOf("-local-");title+="（"+(local>=0?version.substring(local+7):version)+"）";}catch(Exception ignored){}
    run.dialog=new AlertDialog.Builder(service).setTitle(title).setView(run.status).setPositiveButton("结束录音",null).setNegativeButton("取消",(d,w)->cancel(run)).create();
    run.dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
    run.dialog.getWindow().getAttributes().token=service.getWindow().getWindow().getDecorView().getWindowToken();
    // The speech panel belongs to the IME; the application's editor keeps focus.
    run.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    run.dialog.setCanceledOnTouchOutside(false);
    run.dialog.setOnCancelListener(d->cancel(run));run.dialog.show();Diagnostics.trace(service,"dialog shown");
    run.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(run.recognizer!=null){if(run.audio!=null)run.audio.stop();run.recognizer.stopListening();run.status.setText("正在结束识别…");v.setEnabled(false);}});
    record(run);
  }
  private static void cancel(Run run){run.cancel=true;if(run.proof!=null){run.proof.cancel();run.proof=null;}if(run.audio!=null){run.audio.cancel();run.audio=null;}if(run.recognizer!=null){run.recognizer.cancel();run.recognizer.destroy();run.recognizer=null;}if(active==run)active=null;}
  public static void inputStarted(Object owner,EditorInfo editor,boolean restarting){VoiceUndo.clear();Run run=active;if(run==null||run.service!=owner)return;
    if(restarting&&sameEditor(run.editor,editor)){run.editor=editor;return;}
    cancel(run);run.dialog.dismiss();
  }
  private static boolean sameEditor(EditorInfo before,EditorInfo now){return before!=null&&now!=null&&java.util.Objects.equals(before.packageName,now.packageName)&&before.fieldId==now.fieldId&&before.inputType==now.inputType;}
  public static void shown(Object owner){if(owner instanceof InputMethodService)VoiceWarmup.shown((InputMethodService)owner);}
  public static void hidden(Object owner){VoiceWarmup.hide(owner);VoiceUndo.clear();Run run=active;if(run!=null&&run.service==owner){Diagnostics.trace(run.service,"keyboard hidden; cancelling");cancel(run);run.dialog.dismiss();}}
  private static void commit(Run run){
    if(run.cancel||active!=run)return;
    if(!sameEditor(run.editor,run.service.getCurrentInputEditorInfo())){cancel(run);run.dialog.dismiss();return;}
    run.result=VoiceText.finishUtterance(run.result);
    InputConnection connection=run.service.getCurrentInputConnection();
    boolean ok=false;try{ok=connection!=null&&connection.commitText(run.result,1);}catch(RuntimeException error){android.util.Log.e("LocalVoice","Text commit failed",error);}
    Diagnostics.trace(run.service,"commit result="+ok+"; chars="+run.result.length());
    if(ok){EnhancedHook.voiceCommitted(run.result);active=null;run.dialog.dismiss();try{VoiceUndo.offer(run.service,connection,run.original,run.result);}catch(RuntimeException ignored){}}
    else{run.status.setText(run.result+"\n\n文字未填入，请点「填入文字」重试。");Button retry=run.dialog.getButton(AlertDialog.BUTTON_POSITIVE);retry.setText("填入文字");retry.setEnabled(true);retry.setOnClickListener(v->commit(run));}
  }
  private static void failure(Run run,String message){
    if(run.cancel||run.failed)return;run.failed=true;
    if(run.audio!=null){run.audio.cancel();run.audio=null;}
    if(run.recognizer!=null){SpeechRecognizer old=run.recognizer;run.recognizer=null;old.cancel();old.destroy();}
    run.status.setText(message);Button retry=run.dialog.getButton(AlertDialog.BUTTON_POSITIVE);retry.setText("重新录音");retry.setEnabled(true);
    retry.setOnClickListener(v->{cancel(run);run.dialog.dismiss();if(sameEditor(run.editor,run.service.getCurrentInputEditorInfo()))show(run.service,run.service.getCurrentInputEditorInfo());});
  }
  private static void record(Run run){
    run.started=true;run.status.setText("正在载入离线语音…");
    run.recognizer=SpeechRecognizer.createSpeechRecognizer(run.service,new ComponentName(HELPER,"local.gboard.voice.OfflineRecognitionService"));
    run.recognizer.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle b){if(!run.cancel&&!run.failed){if(run.audio!=null)run.audio.accepted();run.status.setText("正在听，可混说普通话和英文。停顿后自动填入，也可点结束录音。");}}
      public void onBeginningOfSpeech(){}
      public void onRmsChanged(float value){}
      public void onBufferReceived(byte[] audio){}
      public void onEndOfSpeech(){if(!run.cancel&&!run.failed)run.status.setText("正在结束识别…");}
      public void onPartialResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(!run.cancel&&!run.failed&&words!=null&&!words.isEmpty()&&!words.get(0).isEmpty())run.status.setText(VoiceText.sentenceCase(words.get(0)));}
      public void onEvent(int type,Bundle b){}
      public void onError(int error){if(run.cancel||run.failed)return;Diagnostics.trace(run.service,"recognition error="+error);failure(run,"离线识别未完成（"+error+"），请重试");}
      public void onResults(Bundle b){if(run.cancel||run.failed)return;ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String text=words==null||words.isEmpty()?"":words.get(0);
        if(!sameEditor(run.editor,run.service.getCurrentInputEditorInfo())){Diagnostics.trace(run.service,"editor changed; cancelled");cancel(run);run.dialog.dismiss();return;}
        if(run.audio!=null){run.audio.cancel();run.audio=null;}run.recognizer.destroy();run.recognizer=null;
        if(!text.isEmpty()){proofread(run,text);}else{failure(run,"没有识别到语音，请靠近麦克风后重试");}
      }
    });
    Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra("local_fixture",run.fixture).putExtra("local_auto_finish",true);
    try{
      if(!run.fixture){run.audio=new MicrophonePipe(run.service,message->{if(!run.cancel&&active==run)failure(run,message);});
        request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,run.audio.reader).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,android.media.AudioFormat.ENCODING_PCM_16BIT);
        run.status.setText("已开始录音，请直接说话。首次载入时文字会稍后出现。");
      }
      run.recognizer.startListening(request);
    }catch(Exception e){failure(run,"无法开始录音，请检查麦克风权限后重试");}
  }
  private static void proofread(Run run,String text){
    run.original=VoiceText.sentenceCase(text);run.result=run.original;
    run.status.setText(run.original+"\n\n正在本地校对…");Button skip=run.dialog.getButton(AlertDialog.BUTTON_POSITIVE);skip.setText("使用原文");skip.setEnabled(true);
    skip.setOnClickListener(v->{if(run.proof!=null){run.proof.cancel();run.proof=null;}run.result=run.original;commit(run);});
    run.proof=new ProofreadClient(run.service,run.original,new ProofreadClient.Callback(){
      public void progress(String message){if(!run.cancel&&active==run)run.status.setText(run.original+"\n\n"+message);}
      public void complete(String result){run.proof=null;if(!run.cancel&&active==run){run.result=result;commit(run);}}
    });run.proof.start();
  }
}
