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
    boolean fixture=getIntent().getBooleanExtra("fixture",true);boolean pipe=getIntent().getBooleanExtra("pipe",false);value("fixture",fixture);value("ready",false);value("audio_started",false);
    recognizer=SpeechRecognizer.createSpeechRecognizer(this,new ComponentName("local.gboard.voice","local.gboard.voice.OfflineRecognitionService"));
    recognizer.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle b){value("ready",true);if(!fixture&&!pipe)new Handler().postDelayed(()->{if(!done&&recognizer!=null){if(getIntent().getBooleanExtra("cancel",false)){recognizer.cancel();value("cancel_requested",true);finishReport();}else recognizer.stopListening();}},2000);}
      public void onBeginningOfSpeech(){value("audio_started",true);}
      public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
      public void onEndOfSpeech(){value("ended",true);}
      public void onError(int error){value("error",error);finishReport();}
      public void onResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);value("recognized",words==null||words.isEmpty()?"":words.get(0));value("endpoint_finished",b.getBoolean("local_endpoint_finished"));value("results",true);finishReport();}
    });
    Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra("local_fixture",fixture).putExtra("mixed_fixture",getIntent().getBooleanExtra("mixed",false)).putExtra("local_auto_finish",getIntent().getBooleanExtra("auto_finish",false));
    if(pipe)try{
      ParcelFileDescriptor[] fds=ParcelFileDescriptor.createPipe();request.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,fds[0]).putExtra("local_fixture",false);value("pipe",true);
      new Thread(()->{try(java.io.InputStream input=getAssets().open("speech-fixture.wav");java.io.OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(fds[1])){
        java.io.DataInputStream wav=new java.io.DataInputStream(input);wav.skipBytes(12);int size;
        while(true){byte[] tag=new byte[4];wav.readFully(tag);size=Integer.reverseBytes(wav.readInt());if(new String(tag,"US-ASCII").equals("data"))break;wav.skipBytes(size+(size&1));}
        // Odd-sized writes also exercise PCM samples spanning pipe reads.
        byte[] bytes=new byte[997];while(size>0){int n=wav.read(bytes,0,Math.min(size,bytes.length));if(n<0)throw new java.io.EOFException();out.write(bytes,0,n);size-=n;}
      }catch(Exception e){android.util.Log.e("PipeCheck","Fixture write failed",e);}},"PipeFixture").start();
      recognizer.startListening(request);
      new Handler().postDelayed(()->{try{fds[0].close();}catch(Exception ignored){}},10000);
      if(getIntent().getBooleanExtra("early_stop",false))new Handler().postDelayed(()->{if(recognizer!=null)recognizer.stopListening();},100);
    }catch(Exception e){throw new RuntimeException(e);}else recognizer.startListening(request);
  }
  public void onDestroy(){if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}super.onDestroy();}
}
