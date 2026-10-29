package local.gboard.startupcheck;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import local.gboard.enhanced.*;
import org.json.*;

/** Fixed synthetic data in a disposable UID; never opens an installed IME store. */
public final class StartupInstrumentation extends Instrumentation {
  public void onCreate(Bundle b){super.onCreate(b);start();}
  private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  private static String code(int n){StringBuilder s=new StringBuilder("fixture");do{s.append((char)('a'+n%26));n/=26;}while(n>0);return s.toString();}
  private static void write(File p,byte[] b)throws Exception{p.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(p)){out.write(b);}}
  public void onStart(){Bundle result=new Bundle();try{
    Context c=getTargetContext();File root=c.getNoBackupFilesDir();ArrayList<LearningFile.Entry> entries=new ArrayList<>();StringBuilder history=new StringBuilder();
    for(int i=0;i<20000;i++){String word="测试"+(char)(0x4e00+i);String code=code(i);entries.add(new LearningFile.Entry(word,code,2,1));history.append(code).append(" \t").append(word).append("\tc=5 d=1 t=1\n");}
    entries.add(new LearningFile.Entry("值","zhi",12,1));
    File shared=new File(root,"shared-learning.tsv");write(shared,LearningFile.encode(entries,false));
    write(new File(root,"local-rime/user/installation.yaml"),"installation_id: fixture\n".getBytes("UTF-8"));
    write(new File(root,"local-rime/user/sync/fixture/rime_ice.userdb.txt"),history.toString().getBytes("UTF-8"));
    JSONArray samples=new JSONArray();
    for(int repeat=0;repeat<3;repeat++){
      LearningStore.reload(c);final long[] cold={0};runOnMainSync(()->{long t=System.nanoTime();List<LearningFile.Entry> rows=SelectionLearning.match(c,"zhi");check(rows.size()==1&&rows.get(0).word.equals("值"),"cold ranking lost");cold[0]=System.nanoTime()-t;});
      LearningStore.reload(c);long[] preload={0};Throwable[] error={null};Thread worker=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);try{long t=System.nanoTime();LearningStore.preload(c);preload[0]=System.nanoTime()-t;}catch(Throwable e){error[0]=e;}},"OneShot-Preload-Check");worker.start();worker.join(30000);check(!worker.isAlive()&&error[0]==null,"preload worker failed");
      final long[] warm={0};runOnMainSync(()->{long t=System.nanoTime();List<LearningFile.Entry> rows=SelectionLearning.match(c,"zhi");check(rows.size()==1&&rows.get(0).count==12,"preloaded rank lost");warm[0]=System.nanoTime()-t;});
      samples.put(new JSONObject().put("cold_main_ms",cold[0]/1e6).put("background_preload_ms",preload[0]/1e6).put("warm_main_ms",warm[0]/1e6));
    }
    // A second preload must reuse the published snapshot, even if the file is
    // absent. This also catches accidentally turning preload into a reload.
    File saved=new File(root,"shared-learning.saved");check(shared.renameTo(saved),"fixture rename");LearningStore.preload(c);check(SelectionLearning.match(c,"zhi").size()==1,"preload re-read instead of reusing cache");check(saved.renameTo(shared),"fixture restore");
    EmojiSuggestions.load(c.getAssets().open("emoji.txt"));
    check(EmojiSuggestions.suggest("ok",Collections.emptyList()).contains("👌"),"ok emoji missing");
    check(EmojiSuggestions.suggest("xiexie",Arrays.asList("谢谢")).contains("🙏"),"Chinese emoji missing");
    check(LearningStore.snapshotOwn(c).size()==20000,"preload changed own history or fed back remote totals");
    result.putString("report",new JSONObject().put("samples",samples).put("synthetic_shared_rows",20001).put("synthetic_history_rows",20000).put("cached_preload_idempotent",true).put("shared_counts_not_reexported",true).put("offline_emoji_lookup_verified",true).put("passed",true).toString());finish(-1,result);
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
