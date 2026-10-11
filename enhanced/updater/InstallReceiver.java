package local.gboard.updater;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
public final class InstallReceiver extends BroadcastReceiver {
  public void onReceive(Context context,Intent intent){
    int result=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
    if(result==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);if(confirm!=null)context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
    if(result!=PackageInstaller.STATUS_SUCCESS){String cause=result==PackageInstaller.STATUS_FAILURE_BLOCKED?"系统安装验证未通过":result==PackageInstaller.STATUS_FAILURE_ABORTED?"安装已取消":result==PackageInstaller.STATUS_FAILURE_STORAGE?"安装空间不足":"更新未完成";save(context,cause+"；原版本仍可使用。请重新检查并安装。");return;}
    PendingResult pending=goAsync();new Thread(()->{try{String expected=context.getSharedPreferences("updates",0).getString("pending_manifest",null);if(expected==null){InstalledRelease.legacyVersions(context);save(context,"已安装 "+ReleaseIdentity.VERSION+"；组件版本和签名已核对。打开检查更新可继续核验全部安装文件。词库和设置保留。");return;}org.json.JSONObject manifest=new org.json.JSONObject(expected);InstalledRelease.verify(context,manifest);save(context,"已安装并核验 "+manifest.getString("version")+"；本地词库和设置保留。");context.getSharedPreferences("updates",0).edit().remove("pending_manifest").apply();
      java.io.File[] folders=context.getCacheDir().listFiles();if(folders!=null)for(java.io.File folder:folders)if(folder.isDirectory()&&folder.getName().startsWith("update-")){java.io.File[] files=folder.listFiles();if(files!=null)for(java.io.File file:files)if(file.isFile()&&(file.getName().endsWith(".apk")||file.getName().endsWith(".part")))file.delete();folder.delete();}
    }catch(Exception e){save(context,"系统已完成安装，但安装核验未通过："+e.getMessage()+"。请检查更新后重新安装此版本。");}finally{pending.finish();}},"VerifyInstalledUpdate").start();
  }
  private static void save(Context context,String message){context.getSharedPreferences("updates",0).edit().putString("last_result",message).commit();new android.os.Handler(android.os.Looper.getMainLooper()).post(()->Toast.makeText(context,message,Toast.LENGTH_LONG).show());}
}
