package local.gboard.updater;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
public final class InstallReceiver extends BroadcastReceiver {
  public void onReceive(Context context,Intent intent){
    int result=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
    if(result==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);if(confirm!=null)context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
    String message=result==PackageInstaller.STATUS_SUCCESS?"Gboard 中文增强已更新":"更新未完成；原版本仍可使用";
    context.getSharedPreferences("updates",0).edit().putString("last_result",message).apply();Toast.makeText(context,message,Toast.LENGTH_LONG).show();
    if(result==PackageInstaller.STATUS_SUCCESS)new Thread(()->{java.io.File[] folders=context.getCacheDir().listFiles();if(folders!=null)for(java.io.File folder:folders)if(folder.isDirectory()&&folder.getName().startsWith("update-")){java.io.File[] files=folder.listFiles();if(files!=null)for(java.io.File file:files)if(file.isFile()&&(file.getName().endsWith(".apk")||file.getName().endsWith(".part")))file.delete();folder.delete();}},"UpdateCacheCleanup").start();
  }
}
