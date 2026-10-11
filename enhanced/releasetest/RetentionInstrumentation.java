package local.gboard.releasetest;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
/** AVD-only state preservation check under the target UID; never shipped. */
public final class RetentionInstrumentation extends Instrumentation {
  private String phase;
  public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","after");start();}
  private static String sha(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}StringBuilder result=new StringBuilder();for(byte value:hash.digest())result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
  private static void collect(File folder,String relative,JSONObject result)throws Exception{File[] files=folder.listFiles();if(files==null)return;for(File file:files)if(file.isDirectory())collect(file,relative+file.getName()+"/",result);else result.put(relative+file.getName(),sha(file));}
  public void onStart(){Bundle output=new Bundle();try{
    Context target=getTargetContext();File user=new File(target.getNoBackupFilesDir(),"local-rime/user"),snapshot=new File(target.getNoBackupFilesDir(),"avd-retention.json");
    SharedPreferences settings=target.getSharedPreferences("local-enhancement-settings",0);
    if(phase.equals("before")){if(!user.isDirectory())throw new IOException("Rime user directory missing");settings.edit().putBoolean("handwriting",true).putBoolean("handwriting_board",false).putBoolean("caret_ticks",false).commit();JSONObject hashes=new JSONObject();collect(user,"",hashes);if(hashes.length()==0)throw new IOException("No deployed Rime user files");try(FileOutputStream out=new FileOutputStream(snapshot)){out.write(hashes.toString().getBytes("UTF-8"));}output.putString("report",new JSONObject().put("user_files",hashes.length()).put("snapshot_created",true).put("passed",true).toString());}
    else {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(snapshot)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}JSONObject expected=new JSONObject(bytes.toString("UTF-8"));Iterator<String> names=expected.keys();while(names.hasNext()){String name=names.next();if(!sha(new File(user,name)).equals(expected.getString(name)))throw new IOException("Local user file changed during replacement");}if(!settings.getBoolean("handwriting",false)||settings.getBoolean("handwriting_board",true)||settings.getBoolean("caret_ticks",true))throw new IOException("Settings changed during replacement");output.putString("report",new JSONObject().put("rime_user_files_preserved",expected.length()).put("local_settings_preserved",true).put("passed",true).toString());}finish(0,output);
  }catch(Throwable error){output.putString("error",android.util.Log.getStackTraceString(error));finish(1,output);}}
}
