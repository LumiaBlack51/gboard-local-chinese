package local.gboard.voice;
import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
public final class PermissionActivity extends Activity {
  public void onCreate(Bundle state){super.onCreate(state);if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)finish();else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);}
  public void onRequestPermissionsResult(int request,String[] names,int[] results){super.onRequestPermissionsResult(request,names,results);Toast.makeText(this,results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"请再次点击键盘麦克风，使用离线语音":"未授予麦克风权限",Toast.LENGTH_LONG).show();finish();}
}
