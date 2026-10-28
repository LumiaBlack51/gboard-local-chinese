package local.gboard.updater;

import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import org.json.*;

/** System scheduled, one-hour, plugged-in only; no alarm, polling or foreground service. */
public final class PhraseSync extends JobService {
  public static final int JOB_ID=74135;
  private static final Uri PROVIDER=Uri.parse("content://com.localx.android.inputmethod.latin.dictionary_sync");
  static volatile boolean running;
  private volatile boolean stopped;
  private volatile HttpsURLConnection connection;
  private Thread worker;
  private final BroadcastReceiver unplugged=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!allowBattery())cancel();}};
  static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("phrase-sync",0);}
  public static void configure(Context c,String json)throws Exception {
    JSONObject v=new JSONObject(json);URL endpoint=new URL(v.getString("endpoint"));
    if(!endpoint.getProtocol().equals("https")||endpoint.getUserInfo()!=null||endpoint.getQuery()!=null||!endpoint.getPath().equals("/v1/sync"))throw new IOException("HTTPS /v1/sync endpoint required");
    if((!v.optBoolean("systemTrust",false)&&!v.optString("certificateSha256").matches("[a-f0-9]{64}"))||!v.getString("token").matches("[A-Za-z0-9_-]{32,128}"))throw new IOException("Invalid pairing data");
    prefs(c).edit().putString("config",v.toString()).putBoolean("enabled",true).commit();schedule(c);
  }
  static void schedule(Context c){JobScheduler jobs=c.getSystemService(JobScheduler.class);
    if(!prefs(c).getBoolean("enabled",false)||!prefs(c).contains("config")){jobs.cancel(JOB_ID);return;}
    JobInfo current=jobs.getPendingJob(JOB_ID);if(current!=null)return;
    int result=jobs.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,PhraseSync.class)).setRequiresCharging(!prefs(c).getBoolean("allow_battery",true)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(3600000,300000).setPersisted(true).build());
    if(result!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("System rejected phrase sync job");
  }
  private boolean allowBattery(){return prefs(this).getBoolean("allow_battery",true);}
  private boolean powered(){Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));return b!=null&&b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;}
  private void guard()throws IOException {if(stopped||Thread.currentThread().isInterrupted()||(!allowBattery()&&!powered()))throw new InterruptedIOException("unplugged or cancelled");}
  private void cancel(){stopped=true;HttpsURLConnection c=connection;if(c!=null)c.disconnect();if(worker!=null)worker.interrupt();}
  @Override public boolean onStartJob(JobParameters params){
    if((!allowBattery()&&!powered())||!prefs(this).getBoolean("enabled",false)){finishProcess();return false;}
    stopped=false;running=true;new Handler(getMainLooper()).postDelayed(()->{if(running){cancel();jobFinished(params,false);running=false;stopSelf();finishProcess();}},60000);registerReceiver(unplugged,new IntentFilter(Intent.ACTION_POWER_DISCONNECTED),Context.RECEIVER_NOT_EXPORTED);
    worker=new Thread(()->{String outcome="deferred";try{sync(params);outcome="success";}catch(Exception error){outcome=stopped?"cancelled":"deferred";}
      finally{if(connection!=null)connection.disconnect();prefs(this).edit().putString("last_result",outcome).putLong("last_attempt",System.currentTimeMillis()).commit();
        new Handler(getMainLooper()).post(()->{try{unregisterReceiver(unplugged);}catch(IllegalArgumentException ignored){}if(!stopped)jobFinished(params,false);running=false;stopSelf();finishProcess();});}},"HourlyPhraseSync");worker.start();return true;
  }
  private void sync(JobParameters params)throws Exception {
    guard();Bundle status=getContentResolver().call(PROVIDER,"status",null,options());if(status==null||!status.getBoolean("ok"))throw new IOException("keyboard busy");
    byte[] upload;try(InputStream in=getContentResolver().openInputStream(Uri.withAppendedPath(PROVIDER,"export").buildUpon().appendQueryParameter("allow_battery",Boolean.toString(allowBattery())).build())){upload=read(in);}
    guard();JSONObject config=new JSONObject(prefs(this).getString("config","{}"));
    final String pin=config.optString("certificateSha256","");
    TrustManager[] trust={new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("client");}public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{
      try{if(chain==null||chain.length!=1)throw new CertificateException("chain");chain[0].checkValidity();String actual=UpdateActivity.hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));if(!MessageDigest.isEqual(actual.getBytes("UTF-8"),pin.getBytes("UTF-8")))throw new CertificateException("pin");}catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(e);}}}};
    SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,trust,new SecureRandom());
    URL endpoint=new URL(config.getString("endpoint"));
    connection=(HttpsURLConnection)(params==null||params.getNetwork()==null?endpoint.openConnection():params.getNetwork().openConnection(endpoint));
    connection.setSSLSocketFactory(config.optBoolean("systemTrust",false)?(SSLSocketFactory)SSLSocketFactory.getDefault():tls.getSocketFactory());connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
    connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(upload.length);
    connection.setRequestProperty("Authorization","Bearer "+config.getString("token"));connection.setRequestProperty("X-Phrase-Protocol","1");connection.setRequestProperty("Content-Type","text/tab-separated-values; charset=utf-8");
    guard();try(OutputStream out=connection.getOutputStream()){for(int pos=0;pos<upload.length;pos+=8192){guard();out.write(upload,pos,Math.min(8192,upload.length-pos));}}
    guard();if(connection.getResponseCode()!=200)throw new IOException("exchange failed");byte[] download;try(InputStream in=connection.getInputStream()){download=read(in);}guard();
    java.util.List<local.gboard.enhanced.DictionaryFile.Entry> words=local.gboard.enhanced.DictionaryFile.readStored(new ByteArrayInputStream(download));
    boolean learningSupported="1".equals(connection.getHeaderField("X-Phrase-Learning"));
    java.util.List<local.gboard.enhanced.LearningFile.Entry> learned=local.gboard.enhanced.LearningFile.read(new ByteArrayInputStream(download),true);
    for(int from=0;from<words.size();from+=500){guard();Bundle batch=options();batch.putByteArray("words",local.gboard.enhanced.DictionaryFile.encode(words.subList(from,Math.min(words.size(),from+500))));Bundle result=getContentResolver().call(PROVIDER,"merge",null,batch);if(result==null||!result.getBoolean("ok"))throw new IOException("merge deferred");}
    if(learningSupported)for(int from=0;from<learned.size();from+=500){guard();Bundle batch=options();batch.putByteArray("learning",local.gboard.enhanced.LearningFile.encode(learned.subList(from,Math.min(learned.size(),from+500)),false));Bundle result=getContentResolver().call(PROVIDER,"merge_learning",null,batch);if(result==null||!result.getBoolean("ok"))throw new IOException("learning merge deferred");}
    prefs(this).edit().putBoolean("learning_synced",learningSupported).putInt("learned_words",learningSupported?learned.size():0).commit();
  }
  private Bundle options(){Bundle b=new Bundle();b.putBoolean("allow_battery",allowBattery());return b;}
  private byte[] read(InputStream in)throws IOException {if(in==null)throw new IOException("no phrase stream");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){guard();if(out.size()+n>16*1024*1024)throw new IOException("too large");out.write(buffer,0,n);}return out.toByteArray();}
  @Override public boolean onStopJob(JobParameters p){running=false;cancel();stopSelf();finishProcess();return false;}
  @Override public void onDestroy(){cancel();try{unregisterReceiver(unplugged);}catch(IllegalArgumentException ignored){}super.onDestroy();}
  private void finishProcess(){new Handler(getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),500);}
}
