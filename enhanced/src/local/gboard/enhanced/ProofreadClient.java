package local.gboard.enhanced;
import android.content.*;import android.content.pm.PackageManager;import android.os.*;
/** Bounded, same-signature local service request. Every failure returns the original. */
final class ProofreadClient implements ServiceConnection {
  static final String PACKAGE="local.gboard.proofread";
  interface Callback {void progress(String text);void complete(String text);}
  final Context context;final String original;final Callback callback;final Handler main=new Handler(Looper.getMainLooper());
  final int id=(int)SystemClock.uptimeMillis();Messenger remote;boolean bound,done;
  final Runnable timeout=this::fallback;
  final Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),this::receive));
  void fallback(){finish(original);}
  boolean receive(Message m){if(!done&&m.arg1==id){if(m.what==3)callback.progress(m.getData().getString("text","正在本地校对…"));else if(m.what==1){String result=m.getData().getString("text",original);finish(result.length()<=4096?result:original);}}return true;}
  ProofreadClient(Context c,String text,Callback cb){context=c;original=text;callback=cb;}
  void start(){
    if(original.length()>2048||!LocalSettings.proofread(context)||context.getPackageManager().checkSignatures(context.getPackageName(),PACKAGE)!=PackageManager.SIGNATURE_MATCH){finish(original);return;}
    try{bound=context.bindService(new Intent().setComponent(new ComponentName(PACKAGE,PACKAGE+".ProofreadingService")),this,Context.BIND_AUTO_CREATE);if(!bound)finish(original);else main.postDelayed(timeout,2500);}catch(RuntimeException e){finish(original);}
  }
  public void onServiceConnected(ComponentName n,IBinder b){if(done)return;remote=new Messenger(b);Message m=Message.obtain(null,1,id,0);Bundle data=new Bundle();data.putString("text",original);m.setData(data);m.replyTo=reply;try{remote.send(m);}catch(RemoteException e){finish(original);}}
  public void onServiceDisconnected(ComponentName n){finish(original);}
  public void onBindingDied(ComponentName n){finish(original);}
  public void onNullBinding(ComponentName n){finish(original);}
  void cancel(){if(done)return;done=true;main.removeCallbacks(timeout);if(remote!=null)try{remote.send(Message.obtain(null,2,id,0));}catch(RemoteException ignored){}if(bound){bound=false;try{context.unbindService(this);}catch(RuntimeException ignored){}}}
  void finish(String result){if(done)return;cancel();callback.complete(result);}
}
