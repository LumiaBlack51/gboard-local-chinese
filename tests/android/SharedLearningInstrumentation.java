package local.gboard.learningcheck;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import local.gboard.enhanced.*;

/** Separate test UID and data directory; never opens the user's IME database. */
public final class SharedLearningInstrumentation extends Instrumentation {
  private Bundle args;
  public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
  private static void check(boolean yes,String message)throws Exception {if(!yes)throw new Exception(message);}
  private static void remove(File p){File[] files=p.listFiles();if(files!=null)for(File f:files)remove(f);p.delete();}
  private static Context isolated(Context base,String name){
    File root=new File(base.getNoBackupFilesDir(),name);root.mkdirs();
    return new ContextWrapper(base){public File getNoBackupFilesDir(){return root;}};
  }
  private static int count(List<LearningFile.Entry> rows,String word){for(LearningFile.Entry e:rows)if(e.word.equals(word)&&e.code.equals("zhi"))return e.count;return 0;}
  private static byte[] packet(List<LearningFile.Entry> rows)throws Exception {
    ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(DictionaryFile.encode(Collections.emptyList()));out.write(LearningFile.encode(rows,true));return out.toByteArray();
  }
  private static String first(Context c)throws Exception {
    String[] words={"只","知","直","至","之","值","凪"};int[] ids={0,1,2,3,4,5,-1};
    int[] order=SharedRanking.order(words,ids,SelectionLearning.match(c,"zhi"),SelectionLearning.preferred(c,"zhi"));
    Set<Integer> unique=new HashSet<>();for(int i:order)unique.add(i);check(unique.size()==words.length,"candidate permutation lost an identity");
    return words[order[0]];
  }
  public void onStart(){Bundle result=new Bundle();Context base=getTargetContext();Context c=null;
    try{
      String mode=args.getString("mode","self");
      c=isolated(base,mode.equals("self")?"self":"wire");
      if(mode.equals("send")||mode.equals("self")){LearningStore.reload(c);remove(c.getNoBackupFilesDir());c.getNoBackupFilesDir().mkdirs();}
      if(mode.equals("send")){
        for(int i=0;i<3;i++)SelectionLearning.record(c,"zhi","值");
        List<LearningFile.Entry> own=LearningStore.snapshotOwn(c);check(count(own,"值")==3,"local events were not counted exactly");
        result.putString("outgoing_base64",Base64.encodeToString(packet(own),Base64.NO_WRAP));
      }else if(mode.equals("receive")){
        byte[] incoming=Base64.decode(args.getString("learning"),Base64.DEFAULT);
        LearningStore.mergeShared(c,LearningFile.read(new ByteArrayInputStream(incoming),true));
        check(first(c).equals(args.getString("expected")),"remote learning did not change native ranking");
        List<LearningFile.Entry> own=LearningStore.snapshotOwn(c);check(count(own,"值")==3&&count(own,"知")==0,"downloaded totals leaked into local export");
        LearningStore.reload(c);check(first(c).equals(args.getString("expected")),"shared ranking did not survive store reload");
        result.putString("outgoing_base64",Base64.encodeToString(packet(LearningStore.snapshotOwn(c)),Base64.NO_WRAP));
        result.putBoolean("native_identity_preserved",true);result.putBoolean("no_feedback",true);result.putBoolean("persisted_shared_ranking",true);
      }else{
        List<LearningFile.Entry> remote=Collections.singletonList(new LearningFile.Entry("值","zhi",12,1));
        LearningStore.mergeShared(c,remote);check(first(c).equals("值"),"remote frequency ignored for native word");
        check(LearningStore.snapshotOwn(c).isEmpty(),"remote totals treated as local events");
        SelectionLearning.record(c,"zhi","只");check(first(c).equals("只"),"new local choice pinned by old remote total");
        check(count(LearningStore.snapshotOwn(c),"只")==1,"new local choice counted incorrectly");
        LearningStore.reload(c);check(first(c).equals("只"),"local choice lost on reload");
        for(int i=0;i<4;i++)LearningStore.mergeShared(c,remote);
        check(count(LearningStore.snapshotOwn(c),"值")==0,"retries inflated local counter");
        File snapshot=new File(c.getNoBackupFilesDir(),"local-rime/user/sync/test/rime_ice.userdb.txt");snapshot.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(snapshot)){out.write("zhi \t只\tc=5 d=1 t=1\nzhi \t凪\tc=0 d=1\nzhi \t知\tc=-1 d=1\n".getBytes(StandardCharsets.UTF_8));}
        try(FileOutputStream out=new FileOutputStream(new File(c.getNoBackupFilesDir(),"local-rime/user/installation.yaml"))){out.write("installation_id: test\n".getBytes(StandardCharsets.UTF_8));}
        LearningStore.reload(c);SelectionLearning.record(c,"zhi","只");
        check(count(LearningStore.snapshotOwn(c),"只")==6,"historical native counter was not continued");
        check(count(LearningStore.snapshotOwn(c),"凪")==0&&count(LearningStore.snapshotOwn(c),"知")==0,"unselected/deleted snapshot rows counted");
        File imported=new File(c.getNoBackupFilesDir(),"local-dictionary/words.tsv");imported.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(imported)){out.write("凪\tzhi\t1000000\n".getBytes(StandardCharsets.UTF_8));}
        check(count(LearningStore.snapshotOwn(c),"凪")==0,"import frequency treated as actual learning");
        LearningStore.mergeShared(c,Collections.singletonList(new LearningFile.Entry("值","zhi",14,System.currentTimeMillis()+1000)));
        check(first(c).equals("值"),"new remote learning could never replace an old local preference");
        SelectionLearning.record(c,"zhi","只");check(first(c).equals("只"),"a new local choice lost to clock rounding/skew");
        result.putBoolean("remote_frequency_changes_native_rank",true);result.putBoolean("new_local_choice_wins",true);
        result.putBoolean("native_history_continued",true);result.putBoolean("import_weights_excluded",true);
        result.putBoolean("retries_do_not_inflate_counts",true);result.putBoolean("new_remote_learning_can_update_preference",true);
      }
      result.putBoolean("passed",true);finish(-1,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(0,result);}
    finally{if(c!=null&&args.getString("mode","self").equals("self")){try{LearningStore.reload(c);}catch(Exception ignored){}remove(c.getNoBackupFilesDir());}}
  }
}
