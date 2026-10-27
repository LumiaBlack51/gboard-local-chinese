package local.gboard.synccheck;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.lang.reflect.*;

/** No Activity, keyboard switching, input events or personal text in this checker. */
public final class PhraseSyncInstrumentation extends Instrumentation {
  Bundle args;
  public void onCreate(Bundle bundle){super.onCreate(bundle);args=bundle;start();}
  public void onStart(){Bundle result=new Bundle();try{
    Context c=getTargetContext();Class<?> sync=c.getClassLoader().loadClass("local.gboard.updater.PhraseSync");
    if(args.containsKey("config"))sync.getMethod("configure",Context.class,String.class).invoke(null,c,new String(android.util.Base64.decode(args.getString("config"),0),"UTF-8"));
    JobScheduler jobs=c.getSystemService(JobScheduler.class);JobInfo job=jobs.getPendingJob(74135);
    if(job==null||job.getIntervalMillis()!=3600000||job.isRequireCharging()||!job.isPersisted())throw new Exception("default phone policy mismatch");
    result.putBoolean("hourly",true);result.putBoolean("default_runs_on_battery",true);result.putBoolean("persisted_system_job",true);
    if("policy".equals(args.getString("mode"))){
      Method schedule=sync.getDeclaredMethod("schedule",Context.class);schedule.setAccessible(true);
      c.getSharedPreferences("phrase-sync",0).edit().putBoolean("allow_battery",false).commit();jobs.cancel(74135);schedule.invoke(null,c);
      if(!jobs.getPendingJob(74135).isRequireCharging())throw new Exception("optional charging restriction missing");
      c.getSharedPreferences("phrase-sync",0).edit().putBoolean("allow_battery",true).commit();jobs.cancel(74135);schedule.invoke(null,c);
      result.putBoolean("optional_charging_policy",true);
    }
    if("exchange".equals(args.getString("mode"))){
      Object service=sync.getConstructor().newInstance();Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(service,c);
      Method exchange=sync.getDeclaredMethod("sync",JobParameters.class);exchange.setAccessible(true);exchange.invoke(service,new Object[]{null});
      result.putBoolean("actual_phrase_exchange",true);
    }
    if("result".equals(args.getString("mode"))){String status=c.getSharedPreferences("phrase-sync",0).getString("last_result","");if(!status.equals("success"))throw new Exception("sync result "+status);result.putBoolean("sync_success",true);}
    android.content.pm.ProviderInfo provider=c.getPackageManager().resolveContentProvider("com.localx.android.inputmethod.latin.dictionary_sync",0);if(provider==null||!provider.exported||!"local.gboard.DICTIONARY_SYNC".equals(provider.readPermission))throw new Exception("provider boundary manifest invalid");result.putBoolean("provider_signature_boundary",true);
    if(args.containsKey("expected_words")){Bundle options=new Bundle();options.putBoolean("allow_battery",true);Bundle state=c.getContentResolver().call(Uri.parse("content://com.localx.android.inputmethod.latin.dictionary_sync"),"status",null,options);if(state==null||!state.getBoolean("ok")||state.getInt("count")<Integer.parseInt(args.getString("expected_words")))throw new Exception("phrase merge unavailable");result.putBoolean("merged_phrase_count_valid",true);}
    result.putBoolean("passed",true);finish(-1,result);
  }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(0,result);}}
}
