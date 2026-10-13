package local.gboard.proofread;
import android.app.Service;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Signature-protected, text-only IPC. No network, audio, history, or persistent user text. */
public final class ProofreadingService extends Service {
  final Handler main=new Handler(Looper.getMainLooper());
  final ExecutorService worker=Executors.newSingleThreadExecutor();
  ProofEngine engine;volatile Job current;int generation;volatile boolean closed;
  static final class Job {Messenger reply;int id;volatile boolean cancelled;volatile ProofEngine.Run run;}
  final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper(),message->{
    if(getPackageManager().checkSignatures(android.os.Process.myUid(),message.sendingUid)!=android.content.pm.PackageManager.SIGNATURE_MATCH)return true;
    if(message.what==2){Job j=current;if(j!=null&&j.id==message.arg1)cancel(j);return true;}
    if(message.what!=1||message.replyTo==null)return true;
    String input=message.getData().getString("text","");if(input.length()>2048||input.isEmpty())return true;
    Job previous=current;if(previous!=null)cancel(previous);
    Job job=new Job();job.reply=message.replyTo;job.id=message.arg1;current=job;generation++;
    String requested=message.getData().getString("backend","auto");
    // Backend overrides are useful for reproducible, synthetic device benchmarks.
    if(!requested.matches("auto|cpu|npu|gpu"))requested="auto";final String backend=requested;
    worker.execute(()->{
      long start=SystemClock.elapsedRealtime();String output=input,error="";
      try(ProofEngine.Run run=new ProofEngine.Run()){
        job.run=run;if(job.cancelled)return;if(engine==null)engine=new ProofEngine(this);
        output=engine.correct(input,run,s->send(job,3,s,"",0),backend);
      }catch(Exception failure){error=failure.getClass().getSimpleName()+(backend.equals("auto")?"":": "+failure.getMessage());android.util.Log.i("LocalProof","Correction unavailable: "+failure.getClass().getSimpleName());}
      finally{job.run=null;}
      if(!job.cancelled)send(job,1,output,error,SystemClock.elapsedRealtime()-start);
      main.post(()->{if(!closed&&current==job){current=null;int epoch=++generation;main.postDelayed(()->{if(!closed&&current==null&&generation==epoch)worker.execute(()->{if(engine!=null){engine.close();engine=null;}});},30000);}});
    });return true;
  }));
  void send(Job j,int what,String text,String error,long ms){if(j.cancelled)return;Message m=Message.obtain(null,what,j.id,0);Bundle b=new Bundle();b.putString("text",text);b.putString("error",error);b.putLong("ms",ms);b.putString("backend",engine==null?"":engine.lastBackend);m.setData(b);try{j.reply.send(m);}catch(RemoteException e){cancel(j);}}
  void cancel(Job j){j.cancelled=true;ProofEngine.Run r=j.run;if(r!=null)r.cancel();}
  public IBinder onBind(Intent intent){return endpoint.getBinder();}
  public boolean onUnbind(Intent intent){Job j=current;if(j!=null)cancel(j);return false;}
  public void onDestroy(){closed=true;Job j=current;if(j!=null)cancel(j);main.removeCallbacksAndMessages(null);worker.execute(()->{if(engine!=null)engine.close();});worker.shutdown();super.onDestroy();}
}
