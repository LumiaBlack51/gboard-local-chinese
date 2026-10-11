package local.gboard.voicecheck;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.widget.TextView;
import java.util.*;
import org.json.JSONObject;
public final class VoiceCheckActivity extends Activity {
  private SpeechRecognizer recognizer;private JSONObject report=new JSONObject();private TextView status;private boolean done;
  private void value(String key,Object value){try{report.put(key,value);}catch(Exception ignored){}}
  private void finishReport(){done=true;status.setText(report.toString());try(java.io.FileOutputStream out=openFileOutput("voice-report.json",0)){out.write(report.toString().getBytes("UTF-8"));}catch(Exception error){throw new RuntimeException(error);}recognizer.destroy();recognizer=null;}
  public void onCreate(Bundle state){super.onCreate(state);status=new TextView(this);status.setText("Testing explicit offline service");setContentView(status);
    boolean fixture=getIntent().getBooleanExtra("fixture",true);value("fixture",fixture);value("ready",false);value("audio_started",false);
    recognizer=SpeechRecognizer.createSpeechRecognizer(this,new ComponentName("local.gboard.voice","local.gboard.voice.OfflineRecognitionService"));
    recognizer.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle b){value("ready",true);if(!fixture)new Handler().postDelayed(()->{if(!done&&recognizer!=null){if(getIntent().getBooleanExtra("cancel",false)){recognizer.cancel();value("cancel_requested",true);finishReport();}else recognizer.stopListening();}},2000);}
      public void onBeginningOfSpeech(){value("audio_started",true);}
      public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
      public void onEndOfSpeech(){value("ended",true);}
      public void onError(int error){value("error",error);finishReport();}
      public void onResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);value("recognized",words==null||words.isEmpty()?"":words.get(0));value("results",true);finishReport();}
    });
    recognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra("local_fixture",fixture));
  }
  public void onDestroy(){if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}super.onDestroy();}
}
