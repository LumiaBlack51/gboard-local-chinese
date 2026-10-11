package local.gboard.enhanced;
import android.content.Context;
import java.io.*;
import java.util.concurrent.*;
/** Bundled model; pen coordinates are transient and never written to storage. */
public final class HandwritingBridge {
  private static final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"LocalHandwriting"));
  private static long model;private static Context app;
  public static synchronized void prepare(Context context){if(app!=null)return;app=context.getApplicationContext();worker.execute(()->{try{load();}catch(Throwable e){android.util.Log.e("LocalHandwriting","Model initialization failed",e);}});}
  private static void load()throws IOException {
    if(model!=0)return;File folder=new File(app.getNoBackupFilesDir(),"local-handwriting");folder.mkdirs();File target=new File(folder,"zh-CN-v0.3.model");
    if(!target.isFile()||target.length()!=26834816L){File temp=new File(folder,"model.tmp");try(InputStream in=app.getAssets().open("local-handwriting/handwriting-zh_CN.model");OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(!temp.renameTo(target))throw new IOException("Cannot install handwriting model");}
    System.loadLibrary("gboard_local_handwriting");model=open(target.getAbsolutePath());if(model==0)throw new IOException("Cannot load handwriting model");
  }
  interface Result {void complete(String[] words);}
  static void recognize(int[] points,Result result){worker.execute(()->{String[] words;try{load();words=classify(model,points,300,300);}catch(Throwable e){android.util.Log.e("LocalHandwriting","Recognition failed",e);words=new String[0];}final String[] output=words;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->result.complete(output));});}
  static native long open(String path);
  static native String[] classify(long model,int[] points,int width,int height);
}
