package local.gboard.voice;
import android.app.Service;
import android.content.Intent;
import android.os.*;
import local.gboard.enhanced.VoiceBridge;
/** Model-only preparation. Never opens the microphone or receives editor text. */
public final class WarmupService extends Service {
  private final IBinder binder=new Binder();
  public void onCreate(){super.onCreate();new Thread(()->{long start=SystemClock.elapsedRealtime();try{VoiceBridge.prepare(getApplicationContext());android.util.Log.i("OfflineSpeech","warmup_ms="+(SystemClock.elapsedRealtime()-start));}catch(Exception e){android.util.Log.i("OfflineSpeech","Warmup unavailable");}},"OfflineSpeech-Warmup").start();}
  public IBinder onBind(Intent intent){return binder;}
}
