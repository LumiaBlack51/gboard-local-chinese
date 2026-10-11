package local.gboard.releasetest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import org.json.*;
/** Actual private receiver and foreground Activity; synthetic failure statuses only on AVD. */
public final class UpdaterResultInstrumentation extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  private static boolean contains(View view,String expected){if(view instanceof TextView&&((TextView)view).getText().toString().contains(expected))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),expected))return true;return false;}
  public void onStart(){Bundle result=new Bundle();SharedPreferences preferences=null;String original=null;try{
    Context target=getTargetContext();preferences=target.getSharedPreferences("updates",0);original=preferences.getString("last_result",null);
    Activity activity=startActivitySync(new Intent().setClassName(target.getPackageName(),"local.gboard.updater.UpdateActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();JSONObject report=new JSONObject();
    int[] statuses={PackageInstaller.STATUS_FAILURE_BLOCKED,PackageInstaller.STATUS_FAILURE_ABORTED};String[] expected={"系统安装验证未通过","安装已取消"};
    for(int i=0;i<statuses.length;i++){target.sendBroadcast(new Intent().setClassName(target.getPackageName(),"local.gboard.updater.InstallReceiver").putExtra(PackageInstaller.EXTRA_STATUS,statuses[i]));boolean[] shown={false};long deadline=SystemClock.elapsedRealtime()+10000;String text=expected[i];while(!shown[0]&&SystemClock.elapsedRealtime()<deadline){runOnMainSync(()->shown[0]=contains(activity.getWindow().getDecorView(),text));if(!shown[0])SystemClock.sleep(100);}if(!shown[0])throw new Exception("Foreground result did not refresh: "+text);report.put(i==0?"verification_failure_shown_without_reopening":"cancellation_shown_without_reopening",true);}
    result.putString("report",report.put("passed",true).toString());finish(0,result);
  }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}finally{if(preferences!=null){SharedPreferences.Editor edit=preferences.edit();if(original==null)edit.remove("last_result");else edit.putString("last_result",original);edit.commit();}}}
}
