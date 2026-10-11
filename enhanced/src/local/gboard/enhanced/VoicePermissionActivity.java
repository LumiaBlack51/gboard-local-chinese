package local.gboard.enhanced;
import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
public final class VoicePermissionActivity extends Activity {
  public void onCreate(Bundle state){super.onCreate(state);if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)finish();else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);}
  public void onRequestPermissionsResult(int request,String[] names,int[] results){super.onRequestPermissionsResult(request,names,results);Toast.makeText(this,results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"请再次点击键盘麦克风，开始离线语音":"未获得麦克风权限；文字输入仍可使用",Toast.LENGTH_LONG).show();finish();}
}
