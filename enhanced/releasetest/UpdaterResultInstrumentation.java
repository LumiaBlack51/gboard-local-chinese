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
  public void onStart(){Bundle result=new Bundle();int outcome=0;SharedPreferences preferences=null;String original=null;try{
    Context target=getTargetContext();preferences=target.getSharedPreferences("updates",0);original=preferences.getString("last_result",null);
    Activity activity=startActivitySync(new Intent().setClassName(target.getPackageName(),"local.gboard.updater.UpdateActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();JSONObject report=new JSONObject();
    Class<?> updater=Class.forName("local.gboard.updater.UpdateActivity",true,target.getClassLoader());java.lang.reflect.Method validate=updater.getDeclaredMethod("validateManifest",JSONObject.class,String.class,java.util.Set.class),all=updater.getDeclaredMethod("allComponents",JSONObject.class);validate.setAccessible(true);all.setAccessible(true);
    String cert="0000000000000000000000000000000000000000000000000000000000000000";JSONObject manifest=new JSONObject().put("format",1).put("certificateSha256",cert);JSONArray core=new JSONArray();java.util.Set<String> names=new java.util.HashSet<>();String[] packages={"com.localx.android.inputmethod.latin","local.gboard.voice","local.gboard.updater","local.gboard.proofread"};String[][] assets={{"base.apk","split_brella_feature_split.apk","split_config.xxxhdpi.apk","split_tenoranimation_feature_split.apk"},{"voice-helper.apk"},{"updater.apk"},{"proofread.apk"}};JSONObject extension=null;
    for(int i=0;i<4;i++){JSONArray files=new JSONArray();for(String name:assets[i]){names.add(name);files.put(new JSONObject().put("name",name).put("size",100).put("sha256",cert));}JSONObject part=new JSONObject().put("package",packages[i]).put("versionCode",1).put("assets",files);if(i<3)core.put(part);else extension=part;}
    manifest.put("components",core);validate.invoke(null,manifest,cert,names);report.put("legacy_three_components_accepted",((JSONArray)all.invoke(null,manifest)).length()==3);
    manifest.put("optionalComponents",new JSONArray().put(extension));validate.invoke(null,manifest,cert,names);report.put("proofreading_extension_accepted",((JSONArray)all.invoke(null,manifest)).length()==4);
    names.remove("proofread.apk");boolean rejected=false;try{validate.invoke(null,manifest,cert,names);}catch(java.lang.reflect.InvocationTargetException expectedFailure){rejected=true;}if(!rejected)throw new Exception("Missing extension asset accepted");report.put("missing_extension_rejected",true);names.add("proofread.apk");extension.put("package","untrusted.package");rejected=false;try{validate.invoke(null,manifest,cert,names);}catch(java.lang.reflect.InvocationTargetException expectedFailure){rejected=true;}if(!rejected)throw new Exception("Unknown extension accepted");report.put("unknown_extension_rejected",true);
    int[] statuses={PackageInstaller.STATUS_FAILURE_BLOCKED,PackageInstaller.STATUS_FAILURE_ABORTED};String[] expected={"系统安装验证未通过","安装已取消"};
    for(int i=0;i<statuses.length;i++){target.sendBroadcast(new Intent().setClassName(target.getPackageName(),"local.gboard.updater.InstallReceiver").putExtra(PackageInstaller.EXTRA_STATUS,statuses[i]));boolean[] shown={false};long deadline=SystemClock.elapsedRealtime()+10000;String text=expected[i];while(!shown[0]&&SystemClock.elapsedRealtime()<deadline){runOnMainSync(()->shown[0]=contains(activity.getWindow().getDecorView(),text));if(!shown[0])SystemClock.sleep(100);}if(!shown[0])throw new Exception("Foreground result did not refresh: "+text);report.put(i==0?"verification_failure_shown_without_reopening":"cancellation_shown_without_reopening",true);}
    result.putString("report",report.put("passed",true).toString());
  }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));outcome=1;}finally{if(preferences!=null){SharedPreferences.Editor edit=preferences.edit();if(original==null)edit.remove("last_result");else edit.putString("last_result",original);edit.commit();}}finish(outcome,result);}
}
