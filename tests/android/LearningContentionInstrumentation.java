package local.gboard.contentioncheck;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import local.gboard.enhanced.*;
import org.json.*;

/** Synthetic files in a disposable UID, including an intentionally slow fsync. */
public final class LearningContentionInstrumentation extends Instrumentation {
  private boolean requireFixed;
  public void onCreate(Bundle args){super.onCreate(args);requireFixed="true".equals(args.getString("require_fixed"));start();}
  private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  private static DictionaryFile.Entry imported(int weight)throws Exception {java.lang.reflect.Constructor<DictionaryFile.Entry> make=DictionaryFile.Entry.class.getDeclaredConstructor(String.class,String.class,int.class);make.setAccessible(true);return make.newInstance("测试","ceshi",weight);}
  private static String code(int n){StringBuilder out=new StringBuilder("fixture");do{out.append((char)('a'+n%26));n/=26;}while(n>0);return out.toString();}
  private static void write(File file,byte[] bytes)throws Exception{File parent=file.getParentFile();check(parent.isDirectory()||parent.mkdirs(),"fixture directory unavailable: "+parent);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
  private static final class SlowFile extends AtomicFile {
    final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);int writes;
    SlowFile(File file){super(file);}
    @Override public void finishWrite(FileOutputStream out){writes++;entered.countDown();try{check(release.await(10,TimeUnit.SECONDS),"slow write release timed out");}catch(InterruptedException e){throw new RuntimeException(e);}super.finishWrite(out);}
  }
  private JSONObject checkSyncStatus(Context c)throws Exception {
    final Throwable[] error={null};runOnMainSync(()->{try{
      Class<?> sync=Class.forName("local.gboard.updater.PhraseSync"),settings=Class.forName("local.gboard.updater.PhraseSyncSettings");
      Object service=sync.getConstructor().newInstance(),activity=settings.getConstructor().newInstance();
      Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(service,c);attach.invoke(activity,c);
      Method save=sync.getDeclaredMethod("saveResult",String.class,String.class),display=settings.getDeclaredMethod("syncStatus");save.setAccessible(true);display.setAccessible(true);
      android.content.SharedPreferences prefs=c.getSharedPreferences("phrase-sync",0);prefs.edit().clear().putString("last_result","deferred").putLong("last_attempt",1000).commit();
      check(((String)display.invoke(activity)).contains("暂无可确认记录"),"old deferred result implied a successful exchange");
      Field stage=sync.getDeclaredField("stage");stage.setAccessible(true);stage.set(service,"network");
      prefs.edit().putString("last_result","success").putLong("last_attempt",1000).commit();save.invoke(service,"failed","tls");check(prefs.getLong("last_success",0)==1000,"upgrade lost legacy successful timestamp");
      save.invoke(service,"success","");long success=prefs.getLong("last_success",0);check(success>1000,"success timestamp absent");
      save.invoke(service,"failed","tls");check(prefs.getLong("last_success",0)==success,"failure overwrote last success");
      String message=(String)display.invoke(activity);check(message.contains("失败")&&message.contains("TLS 连接失败")&&message.contains("连接／交换"),"failure stage missing from UI");
      Method kind=sync.getDeclaredMethod("failureKind",Throwable.class);kind.setAccessible(true);
      check("tls".equals(kind.invoke(null,new IOException(new javax.net.ssl.SSLException("private data")))),"wrapped TLS error classification lost");
      for(Object value:prefs.getAll().values())check(!String.valueOf(value).contains("private data"),"exception message persisted");
      save.invoke(service,"cancelled","deadline");check(prefs.getLong("last_success",0)==success,"cancelled job marked successful");
    }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception("sync status regression",error[0]);
    return new JSONObject().put("last_success_preserved_after_failure_and_cancel",true).put("tls_failure_stage_displayed",true).put("legacy_deferred_not_reported_as_success",true);
  }
  public void onStart(){Bundle result=new Bundle();try{
    Context c=getTargetContext();JSONObject statusChecks=checkSyncStatus(c);File root=c.getNoBackupFilesDir();StringBuilder history=new StringBuilder();
    List<DictionaryFile.Entry> imported=Collections.singletonList(imported(100));DictionaryStore.merge(c,imported);
    File dictionary=new File(root,"local-dictionary/words.tsv");check(dictionary.setLastModified(1000000),"cannot timestamp fixture");DictionaryStore.merge(c,imported);boolean dictionaryUnchanged=dictionary.lastModified()==1000000;
    DictionaryStore.merge(c,Collections.singletonList(imported(200)));check(DictionaryStore.count(c)==1&&dictionary.lastModified()!=1000000,"new import weight was not saved");
    final int historyRows=12000,localFiles=600;
    for(int i=0;i<historyRows;i++){
      String word="测试"+(char)(0x4e00+i),code=code(i);history.append(code).append(" \t").append(word).append("\tc=5 d=1 t=1\n");
      if(i<localFiles)write(new File(root,"selection-learning/"+code+".tsv"),LearningFile.encode(Collections.singletonList(new LearningFile.Entry(word,code,7,2)),false));
    }
    write(new File(root,"local-rime/user/installation.yaml"),"installation_id: fixture\n".getBytes("UTF-8"));
    write(new File(root,"local-rime/user/sync/fixture/rime_ice.userdb.txt"),history.toString().getBytes("UTF-8"));
    write(new File(root,"shared-learning.tsv"),LearningFile.encode(Collections.singletonList(new LearningFile.Entry("值","zhi",12,1)),false));
    LearningStore.preload(c);SelectionLearning.match(c,"zhi");
    JSONArray samples=new JSONArray();
    for(int repeat=0;repeat<2;repeat++){
      final List<?>[] snapshot={null};final Throwable[] failure={null};final long[] elapsed={0};
      Thread exporter=new Thread(()->{try{long t=System.nanoTime();snapshot[0]=LearningStore.snapshotOwn(c);elapsed[0]=System.nanoTime()-t;}catch(Throwable e){failure[0]=e;}},"Synthetic-Export");exporter.start();
      Thread.sleep(20);final long[] query={0};runOnMainSync(()->{long t=System.nanoTime();check(SelectionLearning.match(c,"zhi").size()==1,"ranking unavailable during export");query[0]=System.nanoTime()-t;});
      exporter.join(60000);check(!exporter.isAlive()&&failure[0]==null,"export failed: "+failure[0]);check(snapshot[0].size()==historyRows,"export lost own rows or uploaded remote totals");
      int total=0;for(Object row:snapshot[0])total+=((LearningFile.Entry)row).count;check(total==historyRows*5+localFiles*2,"local/history max merge changed counters");
      samples.put(new JSONObject().put("export_ms",elapsed[0]/1e6).put("main_query_during_export_ms",query[0]/1e6));
    }
    Field states=LearningStore.class.getDeclaredField("states");states.setAccessible(true);Object state=((Map<?,?>)states.get(null)).get(root.getCanonicalPath());
    Field sharedFile=state.getClass().getDeclaredField("sharedFile");sharedFile.setAccessible(true);SlowFile slow=new SlowFile(new File(root,"shared-learning.tsv"));sharedFile.set(state,slow);
    final Throwable[] writeError={null};List<LearningFile.Entry> remote=Collections.singletonList(new LearningFile.Entry("值","zhi",27,3));
    Thread merger=new Thread(()->{try{LearningStore.mergeShared(c,remote);}catch(Throwable e){writeError[0]=e;}},"Synthetic-Slow-Merge");merger.start();check(slow.entered.await(10,TimeUnit.SECONDS),"merge did not reach disk write");
    Thread releaser=new Thread(()->{try{Thread.sleep(800);}catch(InterruptedException e){throw new RuntimeException(e);}slow.release.countDown();});releaser.start();
    final long[] query={0};runOnMainSync(()->{long t=System.nanoTime();check(SelectionLearning.match(c,"zhi").size()==1,"ranking lost during write");query[0]=System.nanoTime()-t;});
    merger.join(10000);releaser.join();check(!merger.isAlive()&&writeError[0]==null,"merge failed");
    check(SelectionLearning.match(c,"zhi").get(0).count==27,"remote update not published after commit");
    int writes=slow.writes;LearningStore.mergeShared(c,remote);boolean unchangedSkipped=slow.writes==writes;
    LearningStore.reload(c);check(SelectionLearning.match(c,"zhi").get(0).count==27,"remote update not durable");
    if(requireFixed){check(query[0]<300000000L,"main lookup waited for background fsync");check(unchangedSkipped,"unchanged download rewrote shared file");check(dictionaryUnchanged,"unchanged download rewrote imported file");}
    result.putString("report",new JSONObject().put("samples",samples).put("sync_status_checks",statusChecks).put("history_rows",historyRows).put("local_files",localFiles).put("slow_write_delay_ms",800).put("main_query_during_shared_write_ms",query[0]/1e6).put("unchanged_shared_write_skipped",unchangedSkipped).put("unchanged_dictionary_write_skipped",dictionaryUnchanged).put("own_counts_and_remote_exclusion_verified",true).put("durable_remote_update_verified",true).put("passed",true).toString());finish(-1,result);
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
