package local.gboard.enhanced;
import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class RimeBridge {
  static volatile boolean ready;
  private static final AtomicBoolean started=new AtomicBoolean();
  public static void prepare(Context context) {
    if(!started.compareAndSet(false,true))return;
    final Context app=context.getApplicationContext();
    new Thread(()->{
      try {
        File root=new File(app.getNoBackupFilesDir(),"local-rime");
        File shared=new File(root,"shared"), user=new File(root,"user");
        user.mkdirs(); copy(app,"local-rime",shared);
        WordIndex.load(new File(shared,"word-index-v3.bin"));
        LocalWords.load(app);
        System.loadLibrary("c++_shared");
        System.loadLibrary("Fcitx5Utils");
        System.loadLibrary("Fcitx5Config");
        System.loadLibrary("Fcitx5Core");
        System.loadLibrary("rime");
        System.loadLibrary("gboard_local_rime");
        String version=init(shared.getAbsolutePath(),user.getAbsolutePath());
        if(version.contains("dlopen")||version.contains("Missing"))throw new IOException(version);
        long session=create(true);
        if(session==0)throw new IOException("Cannot create Chinese session");
        key(session,'n');key(session,'i');
        String[] probe=state(session);
        if(probe.length<5)throw new IOException("Chinese dictionary not loaded");
        destroy(session);
        ready=true;new android.os.Handler(android.os.Looper.getMainLooper()).post(KeyboardGestures::refresh);Log.i("LocalChinese","Rime ready: "+version);
      }catch(Throwable failure){started.set(false);Log.e("LocalChinese","Offline engine initialization failed",failure);}
    },"LocalChinese-Prepare").start();
  }
  private static void copy(Context app,String name,File dest)throws IOException {
    String[] children=app.getAssets().list(name);
    if(children.length>0){dest.mkdirs();for(String child:children)copy(app,name+"/"+child,new File(dest,child));}
    else if(!dest.isFile()||name.endsWith("schema.yaml")||name.endsWith("default.yaml")){
      if(dest.isFile()){
        ByteArrayOutputStream desired=new ByteArrayOutputStream();try(InputStream in=app.getAssets().open(name)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)desired.write(buf,0,n);}
        if(java.util.Arrays.equals(desired.toByteArray(),java.nio.file.Files.readAllBytes(dest.toPath())))return;
      }
      dest.getParentFile().mkdirs();File tmp=new File(dest.getPath()+".tmp");
      try(InputStream in=app.getAssets().open(name);OutputStream out=new FileOutputStream(tmp)){
        byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
      }
      if(!tmp.renameTo(dest))throw new IOException("Cannot install local data");
    }
  }
  public static synchronized native String init(String shared,String user);
  public static synchronized native long create(boolean privateMode);
  public static synchronized native boolean key(long session,int code);
  public static synchronized native void clear(long session);
  public static synchronized native void destroy(long session);
  public static synchronized native void caret(long session,int position);
  public static synchronized native boolean select(long session,int index);
  public static synchronized native boolean forget(long session,int index);
  public static synchronized native boolean sync();
  public static synchronized native String[] state(long session);
}
