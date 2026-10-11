package local.gboard.voice;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.util.*;
import local.gboard.enhanced.VoiceBridge;
/** Explicit local-only RecognitionService, with signature-checked clients. */
public final class OfflineRecognitionService extends RecognitionService {
  private Run active;
  private static class Run {volatile boolean stop,cancel;volatile AudioRecord audio;Callback callback;Context attributed;}
  protected void onStartListening(Intent request,Callback callback){
    if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("OfflineSpeech","caller="+callback.getCallingUid()+"; service="+android.os.Process.myUid()+"; signatures="+getPackageManager().checkSignatures(callback.getCallingUid(),android.os.Process.myUid()));
    if(getPackageManager().checkSignatures(callback.getCallingUid(),android.os.Process.myUid())!=PackageManager.SIGNATURE_MATCH){try{callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);}catch(Exception ignored){}return;}
    Run run=new Run();run.callback=callback;active=run;
    // RecognitionService tracks attribution creation synchronously in this callback.
    // Creating it later on the worker races the framework's permission delivery check.
    run.attributed=createContext(new ContextParams.Builder().setNextAttributionSource(callback.getCallingAttributionSource()).build());
    boolean fixture=request.getBooleanExtra("local_fixture",false);
    if(fixture&&(getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0){try{callback.error(SpeechRecognizer.ERROR_CLIENT);}catch(Exception ignored){}active=null;return;}
    new Thread(()->decode(run,fixture),"OfflineSpeech-Decode").start();
  }
  protected void onStopListening(Callback callback){Run run=active;if(run!=null){run.stop=true;stopAudio(run);}}
  protected void onCancel(Callback callback){Run run=active;if(run!=null){run.cancel=true;run.stop=true;stopAudio(run);}active=null;}
  public void onDestroy(){if(active!=null){active.cancel=true;active.stop=true;stopAudio(active);}super.onDestroy();}
  private void stopAudio(Run run){AudioRecord a=run.audio;if(a!=null)try{a.stop();}catch(Exception ignored){}}
  private Bundle words(String text){Bundle result=new Bundle();result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Collections.singletonList(text)));return result;}
  private void decode(Run run,boolean fixture){long recognizer=0;AudioRecord audio=null;StringBuilder committed=new StringBuilder();
    try {
      recognizer=VoiceBridge.create(VoiceBridge.prepare(getApplicationContext()));if(recognizer==0)throw new IOException("Model unavailable");if(run.cancel)return;
      if(fixture){
        run.callback.readyForSpeech(new Bundle());
        try(DataInputStream wav=new DataInputStream(new BufferedInputStream(getAssets().open("speech-fixture.wav")))){
          byte[] header=new byte[12];wav.readFully(header);
          while(true){byte[] tag=new byte[4];wav.readFully(tag);int bytes=Integer.reverseBytes(wav.readInt());
            if(new String(tag,"US-ASCII").equals("data")){short[] samples=new short[1600];while(bytes>0&&!run.cancel){int n=Math.min(samples.length,bytes/2);if(n==0)break;for(int i=0;i<n;i++)samples[i]=Short.reverseBytes(wav.readShort());bytes-=n*2;if(VoiceBridge.accept(recognizer,samples,n))committed.append(VoiceBridge.parse(VoiceBridge.result(recognizer,1),"text"));}break;}
            int left=bytes+(bytes&1);while(left>0){int n=wav.skipBytes(left);if(n==0)throw new EOFException();left-=n;}
          }
        }
      }else{
        int size=Math.max(6400,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
        audio=new AudioRecord.Builder().setContext(run.attributed).setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(size).build();
        if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone unavailable");run.audio=audio;if(run.cancel)return;
        if(!run.stop){audio.startRecording();run.callback.readyForSpeech(new Bundle());run.callback.beginningOfSpeech();}
        short[] buffer=new short[1600];long deadline=SystemClock.elapsedRealtime()+120000;
        while(!run.cancel&&!run.stop&&SystemClock.elapsedRealtime()<deadline){int n=audio.read(buffer,0,buffer.length);if(n<0){if(run.stop||run.cancel)break;throw new IOException("Audio read failed");}if(n==0)continue;
          if(VoiceBridge.accept(recognizer,buffer,n)){committed.append(VoiceBridge.parse(VoiceBridge.result(recognizer,1),"text"));run.callback.partialResults(words(committed.toString()));}
          else run.callback.partialResults(words(committed.toString()+VoiceBridge.parse(VoiceBridge.result(recognizer,0),"partial")));
        }
      }
      if(run.cancel)return;run.callback.endOfSpeech();committed.append(VoiceBridge.parse(VoiceBridge.result(recognizer,2),"text"));run.callback.results(words(committed.toString()));
    }catch(Throwable error){android.util.Log.e("OfflineSpeech","Recognition failed",error);if(!run.cancel)try{run.callback.error(SpeechRecognizer.ERROR_AUDIO);}catch(Exception ignored){}}
    finally{run.audio=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}if(recognizer!=0)VoiceBridge.destroy(recognizer);if(active==run)active=null;if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("OfflineSpeech","run closed; cancelled="+run.cancel+"; stopped="+run.stop);}
  }
}
