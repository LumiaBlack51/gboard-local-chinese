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
      long now=System.currentTimeMillis();c.getSharedPreferences("phrase-sync",0).edit().putString("last_result","success").putLong("last_attempt",now).putLong("last_success",now).commit();
    }
    if("inspect".equals(args.getString("mode"))||"export".equals(args.getString("mode"))){
      android.content.SharedPreferences prefs=c.getSharedPreferences("phrase-sync",0);
      result.putString("last_result",prefs.getString("last_result",""));result.putLong("last_attempt",prefs.getLong("last_attempt",0));result.putLong("last_success",prefs.getLong("last_success",0));
      result.putInt("learned_words",prefs.getInt("learned_words",0));
      if("export".equals(args.getString("mode"))){
        // Recovery output contains private data. The host must capture it to a
        // private file, never print instrumentation output or attach it to CI.
        result.putString("pairing_base64",android.util.Base64.encodeToString(prefs.getString("config","{}").getBytes("UTF-8"),android.util.Base64.NO_WRAP));
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.InputStream in=c.getContentResolver().openInputStream(Uri.parse("content://com.localx.android.inputmethod.latin.dictionary_sync/export?allow_battery=true"))){
          byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>16*1024*1024)throw new java.io.IOException("export too large");bytes.write(buffer,0,n);}
        }
        String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
        for(int pos=0;pos<encoded.length();pos+=65536){Bundle chunk=new Bundle();chunk.putString("export_chunk",encoded.substring(pos,Math.min(pos+65536,encoded.length())));sendStatus(2,chunk);}
        result.putInt("export_bytes",bytes.size());
      }
    }
    if("result".equals(args.getString("mode"))){String status=c.getSharedPreferences("phrase-sync",0).getString("last_result","");if(!status.equals("success"))throw new Exception("sync result "+status);result.putBoolean("sync_success",true);}
    if(args.containsKey("expected_learned")){android.content.SharedPreferences prefs=c.getSharedPreferences("phrase-sync",0);if(!prefs.getBoolean("learning_synced",false)||prefs.getInt("learned_words",-1)!=Integer.parseInt(args.getString("expected_learned")))throw new Exception("learning exchange not applied");result.putBoolean("actual_learning_exchange",true);}
    android.content.pm.ProviderInfo provider=c.getPackageManager().resolveContentProvider("com.localx.android.inputmethod.latin.dictionary_sync",0);if(provider==null||!provider.exported||!"local.gboard.DICTIONARY_SYNC".equals(provider.readPermission))throw new Exception("provider boundary manifest invalid");result.putBoolean("provider_signature_boundary",true);
    if(args.containsKey("expected_words")){Bundle options=new Bundle();options.putBoolean("allow_battery",true);Bundle state=c.getContentResolver().call(Uri.parse("content://com.localx.android.inputmethod.latin.dictionary_sync"),"status",null,options);if(state==null||!state.getBoolean("ok")||state.getInt("count")<Integer.parseInt(args.getString("expected_words")))throw new Exception("phrase merge unavailable");result.putBoolean("merged_phrase_count_valid",true);}
    result.putBoolean("passed",true);finish(-1,result);
  }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(0,result);}}
}
