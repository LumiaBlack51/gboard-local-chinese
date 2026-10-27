package local.gboard.updater;
import android.content.*;
/** Package replacement registers only the system job, then this isolated process exits. */
public final class PhraseSyncReceiver extends BroadcastReceiver {
  public void onReceive(Context context,Intent intent){PhraseSync.schedule(context);new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(!PhraseSync.running)android.os.Process.killProcess(android.os.Process.myPid());},500);}
}
