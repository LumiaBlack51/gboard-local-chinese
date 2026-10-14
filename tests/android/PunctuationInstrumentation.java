package local.gboard.punctuationcheck;
import android.app.Instrumentation;
import android.os.*;
import android.content.*;
import android.speech.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import local.gboard.enhanced.VoiceBridge;
import local.gboard.enhanced.VoicePunctuation;
/** Fixed synthetic text in the signed voice helper's process. Never reads user input. */
public final class PunctuationInstrumentation extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  public void onStart(){Bundle output=new Bundle();try{
    String[][] samples={
      {"今天下午开会请大家准时参加不要迟到","今天下午开会，请大家准时参加，不要迟到。"},
      {"我买了苹果香蕉和橘子然后回家做饭","我买了苹果、香蕉和橘子，然后回家做饭。"},
      {"你今天晚上有时间吗我们一起去吃饭好不好","你今天晚上有时间吗？我们一起去吃饭好不好？"},
      {"如果明天下雨我们就在家看书如果天气好我们就出去散步","如果明天下雨，我们就在家看书。如果天气好，我们就出去散步。"},
      {"张伟教授说订单编号B27不要改成B72","张伟教授说，订单编号B27不要改成B72。"},
      {"WHEN YOU ARRIVE PLEASE CALL ME","WHEN YOU ARRIVE, PLEASE CALL ME."},
      {"我的GPU型号是RTX4090明天可以开会吗","我的GPU型号是RTX4090，明天可以开会吗？"},
      {"HELLO HOW ARE YOU I AM FINE THANK YOU","HELLO HOW ARE YOU I AM FINE THANK YOU"},
      {"明天15:30开会","明天15:30开会"},
      {"网址https://example.com不要改","网址https://example.com不要改"},
      {"你好！明天见。","你好！明天见。"},
      {"B27","B27"}
    };
    long cold=SystemClock.elapsedRealtime();VoiceBridge.prepare(getTargetContext());cold=SystemClock.elapsedRealtime()-cold;
    JSONArray rows=new JSONArray();
    for(String[] sample:samples){long start=SystemClock.elapsedRealtime();String actual=VoiceBridge.punctuate(getTargetContext(),sample[0]);long ms=SystemClock.elapsedRealtime()-start;
      rows.put(new JSONObject().put("input",sample[0]).put("output",actual).put("ms",ms));
      if(!actual.equals(sample[1]))throw new AssertionError(sample[0]+" => "+actual+" expected "+sample[1]);
      if(!actual.equals(VoicePunctuation.insertionsOnly(sample[0],actual)))throw new AssertionError("Characters changed");
    }
    JSONArray pipe=new JSONArray();pipe.put(audioPipe(false));pipe.put(audioPipe(true));
    output.putString("report",new JSONObject().put("synthetic_only",true).put("cold_prepare_ms",cold).put("cases",rows).put("audio_pipe",pipe).put("passed",true).toString());finish(0,output);
  }catch(Throwable failure){output.putString("error",android.util.Log.getStackTraceString(failure));finish(1,output);}}
  private JSONObject audioPipe(boolean earlyStop)throws Exception{
    JSONObject report=new JSONObject().put("early_stop",earlyStop);CountDownLatch done=new CountDownLatch(1);
    ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();SpeechRecognizer[] recognizer=new SpeechRecognizer[1];
    runOnMainSync(()->{
      recognizer[0]=SpeechRecognizer.createSpeechRecognizer(getTargetContext(),new ComponentName("local.gboard.voice","local.gboard.voice.OfflineRecognitionService"));
      recognizer[0].setRecognitionListener(new RecognitionListener(){
        void value(String key,Object value){try{report.put(key,value);}catch(Exception e){throw new RuntimeException(e);}}
        public void onReadyForSpeech(Bundle b){value("ready",true);}
        public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}
        public void onPartialResults(Bundle b){}public void onEvent(int type,Bundle b){}
        public void onError(int error){value("error",error);done.countDown();}
        public void onResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);value("recognized",words==null||words.isEmpty()?"":words.get(0));done.countDown();}
      });
      recognizer[0].startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0]).putExtra("local_auto_finish",false));
      if(earlyStop)new Handler(Looper.getMainLooper()).postDelayed(()->recognizer[0].stopListening(),100);
    });
    try(DataInputStream wav=new DataInputStream(getContext().getAssets().open("speech-fixture.wav"));OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
      wav.skipBytes(12);int remaining;
      while(true){byte[] tag=new byte[4];wav.readFully(tag);remaining=Integer.reverseBytes(wav.readInt());if(new String(tag,"US-ASCII").equals("data"))break;wav.skipBytes(remaining+(remaining&1));}
      byte[] bytes=new byte[997];while(remaining>0){int n=wav.read(bytes,0,Math.min(remaining,bytes.length));if(n<0)throw new EOFException();out.write(bytes,0,n);remaining-=n;}
    }
    if(!done.await(90,TimeUnit.SECONDS))throw new Exception("Audio pipe timeout");
    runOnMainSync(()->recognizer[0].destroy());pipe[0].close();
    if(report.has("error")||!report.optBoolean("ready")||!report.optString("recognized").equals("今天下午召开会议，请注意保护隐私。"))throw new Exception("Audio pipe: "+report);
    SystemClock.sleep(750);return report;
  }
}
