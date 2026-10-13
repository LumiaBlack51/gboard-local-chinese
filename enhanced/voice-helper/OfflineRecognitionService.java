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
  private static class Run {volatile boolean stop,cancel;boolean autoFinish,endpointFinished;volatile AudioRecord audio;ParcelFileDescriptor input;Callback callback;Context attributed;}
  protected void onStartListening(Intent request,Callback callback){
    if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("OfflineSpeech","caller="+callback.getCallingUid()+"; service="+android.os.Process.myUid()+"; signatures="+getPackageManager().checkSignatures(callback.getCallingUid(),android.os.Process.myUid()));
    if(getPackageManager().checkSignatures(callback.getCallingUid(),android.os.Process.myUid())!=PackageManager.SIGNATURE_MATCH){try{callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);}catch(Exception ignored){}return;}
    Run run=new Run();run.input=request.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE);run.callback=callback;run.autoFinish=request.getBooleanExtra("local_auto_finish",false);
    synchronized(this){cancelActive();active=run;}
    // RecognitionService tracks attribution creation synchronously in this callback.
    // Creating it later on the worker races the framework's permission delivery check.
    run.attributed=createContext(new ContextParams.Builder().setNextAttributionSource(callback.getCallingAttributionSource()).build());
    boolean fixture=request.getBooleanExtra("local_fixture",false);
    if(fixture&&(getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0){clear(run);try{callback.error(SpeechRecognizer.ERROR_CLIENT);}catch(Exception ignored){}return;}
    String sample=request.getBooleanExtra("mixed_fixture",false)?"speech-mixed.wav":"speech-fixture.wav";
    new Thread(()->decode(run,fixture,sample),"OfflineSpeech-Decode").start();
  }
  protected synchronized void onStopListening(Callback callback){Run run=active;if(run!=null&&run.callback==callback){run.stop=true;stopAudio(run);}}
  protected synchronized void onCancel(Callback callback){if(active!=null&&active.callback==callback)cancelActive();}
  private synchronized void cancelActive(){Run run=active;active=null;if(run!=null){run.cancel=true;run.stop=true;stopAudio(run);closeInput(run);}}
  private synchronized void clear(Run run){if(active==run)active=null;}
  public boolean onUnbind(Intent intent){cancelActive();return super.onUnbind(intent);}
  public void onDestroy(){cancelActive();super.onDestroy();}
  private void closeInput(Run run){if(run.input!=null)try{run.input.close();}catch(IOException ignored){}}
  private void stopAudio(Run run){AudioRecord a=run.audio;if(a!=null)try{a.stop();}catch(Exception ignored){}}
  private Bundle words(String text){Bundle result=new Bundle();result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Collections.singletonList(text)));return result;}
  private void accept(Run run,long recognizer,StringBuilder committed,short[] samples,int length)throws Exception{
    if(VoiceBridge.accept(recognizer,samples,length)){
      VoiceBridge.append(committed,VoiceBridge.parse(VoiceBridge.result(recognizer,1),"text"));run.callback.partialResults(words(committed.toString()));
      if(run.autoFinish&&committed.length()>0){run.endpointFinished=true;run.stop=true;stopAudio(run);}
    }else{StringBuilder partial=new StringBuilder(committed);VoiceBridge.append(partial,VoiceBridge.parse(VoiceBridge.result(recognizer,0),"partial"));run.callback.partialResults(words(partial.toString()));}
  }
  private void decode(Run run,boolean fixture,String sample){long recognizer=0;AudioRecord audio=null;StringBuilder committed=new StringBuilder();
    try {
      recognizer=VoiceBridge.create(VoiceBridge.prepare(getApplicationContext()));if(recognizer==0)throw new IOException("Model unavailable");if(run.cancel)return;
      if(run.input!=null){
        run.callback.readyForSpeech(new Bundle());run.callback.beginningOfSpeech();
        // stopListening closes the producer. Drain all captured PCM through EOF,
        // including speech captured while the model was starting.
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(run.input)){
          byte[] bytes=new byte[3200];short[] samples=new short[1600];int carry=-1,n;
          while(!run.cancel&&!run.endpointFinished&&(n=input.read(bytes))!=-1){int count=0,i=0;
            if(carry>=0&&n>0){samples[count++]=(short)(carry|((bytes[i++]&255)<<8));carry=-1;}
            while(i+1<n){samples[count++]=(short)((bytes[i]&255)|((bytes[i+1]&255)<<8));i+=2;}
            if(i<n)carry=bytes[i]&255;
            if(count>0)accept(run,recognizer,committed,samples,count);
          }
        }
      }else if(fixture){
        run.callback.readyForSpeech(new Bundle());
        try(DataInputStream wav=new DataInputStream(new BufferedInputStream(getAssets().open(sample)))){
          byte[] header=new byte[12];wav.readFully(header);
          while(true){byte[] tag=new byte[4];wav.readFully(tag);int bytes=Integer.reverseBytes(wav.readInt());
            if(new String(tag,"US-ASCII").equals("data")){short[] samples=new short[1600];while(bytes>0&&!run.cancel&&!run.stop){int n=Math.min(samples.length,bytes/2);if(n==0)break;for(int i=0;i<n;i++)samples[i]=Short.reverseBytes(wav.readShort());bytes-=n*2;accept(run,recognizer,committed,samples,n);}break;}
            int left=bytes+(bytes&1);while(left>0){int n=wav.skipBytes(left);if(n==0)throw new EOFException();left-=n;}
          }
        }
        if(run.autoFinish){short[] silence=new short[1600];for(int i=0;i<30&&!run.cancel&&!run.stop;i++)accept(run,recognizer,committed,silence,silence.length);}
      }else{
        int size=Math.max(6400,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
        audio=new AudioRecord.Builder().setContext(run.attributed).setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(size).build();
        if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Microphone unavailable");run.audio=audio;if(run.cancel)return;
        if(!run.stop){audio.startRecording();run.callback.readyForSpeech(new Bundle());run.callback.beginningOfSpeech();}
        short[] buffer=new short[1600];long deadline=SystemClock.elapsedRealtime()+120000;
        while(!run.cancel&&!run.stop&&SystemClock.elapsedRealtime()<deadline){int n=audio.read(buffer,0,buffer.length);if(n<0){if(run.stop||run.cancel)break;throw new IOException("Audio read failed");}if(n==0)continue;
          accept(run,recognizer,committed,buffer,n);
        }
      }
      if(run.cancel)return;
      // Close the microphone before results reset the framework's audio attribution session.
      run.audio=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}
      VoiceBridge.append(committed,VoiceBridge.parse(VoiceBridge.result(recognizer,2),"text"));VoiceBridge.destroy(recognizer);recognizer=0;
      if(run.cancel)return;clear(run);run.callback.endOfSpeech();Bundle result=words(committed.toString());result.putBoolean("local_endpoint_finished",run.endpointFinished);run.callback.results(result);
    }catch(Throwable error){android.util.Log.e("OfflineSpeech","Recognition failed",error);if(!run.cancel)try{run.callback.error(SpeechRecognizer.ERROR_AUDIO);}catch(Exception ignored){}}
    finally{closeInput(run);run.audio=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}if(recognizer!=0)VoiceBridge.destroy(recognizer);clear(run);if((getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.i("OfflineSpeech","run closed; cancelled="+run.cancel+"; stopped="+run.stop);}
  }
}
