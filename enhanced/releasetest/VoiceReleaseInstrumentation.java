package local.gboard.releasetest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** Same-signature instrumentation validates the shipped service from the keyboard UID. */
public final class VoiceReleaseInstrumentation extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle output=new Bundle();try{
    Context target=getTargetContext();Intent launch=new Intent().setClassName(target.getPackageName(),"local.gboard.enhanced.ManagementActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    target.startActivity(launch);SystemClock.sleep(1500);JSONObject checks=new JSONObject();
    for(String label:new String[]{"fixture_rejected","audio_stop","audio_cancel"}){
      android.util.Log.i("ReleaseVoiceTest",label);
      JSONObject report=new JSONObject();CountDownLatch done=new CountDownLatch(1);SpeechRecognizer[] recognizer=new SpeechRecognizer[1];boolean[] finished={false};
      runOnMainSync(()->{recognizer[0]=SpeechRecognizer.createSpeechRecognizer(target,new ComponentName("local.gboard.voice","local.gboard.voice.OfflineRecognitionService"));
        recognizer[0].setRecognitionListener(new RecognitionListener(){
          void value(String name,Object value){try{report.put(name,value);}catch(Exception e){throw new RuntimeException(e);}}
          void complete(){if(!finished[0]){finished[0]=true;done.countDown();}}
          public void onReadyForSpeech(Bundle b){value("ready",true);new Handler(Looper.getMainLooper()).postDelayed(()->{if(!finished[0]){if(label.equals("audio_cancel")){recognizer[0].cancel();value("cancel_requested",true);complete();}else recognizer[0].stopListening();}},2000);}
          public void onBeginningOfSpeech(){value("audio_started",true);}
          public void onEndOfSpeech(){value("ended",true);}
          public void onError(int error){value("error",error);complete();}
          public void onResults(Bundle b){value("results",true);ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);value("recognized",words==null||words.isEmpty()?"":words.get(0));complete();}
          public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
        });recognizer[0].startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra("local_fixture",label.equals("fixture_rejected")));
      });
      if(!done.await(120,TimeUnit.SECONDS))throw new Exception(label+" timeout");
      runOnMainSync(()->recognizer[0].destroy());
      if(label.equals("fixture_rejected")){if(report.optInt("error",-1)!=5)throw new Exception("Release fixture guard: "+report);}
      else if(report.has("error")||!report.optBoolean("ready")||!report.optBoolean("audio_started")||(!label.equals("audio_cancel")&&(!report.optBoolean("ended")||!report.optBoolean("results")))||label.equals("audio_cancel")&&!report.optBoolean("cancel_requested"))throw new Exception(label+": "+report);
      checks.put(label,report);
    }
    output.putString("report",new JSONObject().put("caller","Shipped keyboard UID, same-signature test instrumentation").put("checks",checks).put("passed",true).toString());finish(0,output);
  }catch(Throwable error){output.putString("error",android.util.Log.getStackTraceString(error));finish(1,output);}}
}
