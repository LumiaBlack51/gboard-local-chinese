package local.gboard.enhanced;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Small per-input aggregates; downloaded totals never become local counters. */
public final class LearningStore {
  private static final Map<String,State> states=new HashMap<>();
  // Serialize shared-file commits without making candidate reads wait for fsync.
  private static final Object sharedUpdates=new Object();
  private static final class State {
    final File base,directory;final AtomicFile sharedFile;
    final Map<String,List<LearningFile.Entry>> local=new HashMap<>();
    final Map<String,String> legacy=new HashMap<>();
    Map<String,List<LearningFile.Entry>> shared=new HashMap<>();
    final Map<String,LearningFile.Entry> history=new HashMap<>();
    State(File root){base=root;directory=new File(root,"selection-learning");sharedFile=new AtomicFile(new File(root,"shared-learning.tsv"));}
  }
  private static Map<String,List<LearningFile.Entry>> group(List<LearningFile.Entry> rows){
    Map<String,List<LearningFile.Entry>> result=new HashMap<>();
    for(LearningFile.Entry e:rows){List<LearningFile.Entry> g=result.get(e.code);if(g==null){g=new ArrayList<>();result.put(e.code,g);}g.add(e);}
    return result;
  }
  private static List<LearningFile.Entry> read(AtomicFile file)throws IOException {
    try(InputStream in=file.openRead()){return LearningFile.read(in,false);}catch(FileNotFoundException error){return Collections.emptyList();}
  }
  private static State state(Context context)throws IOException {
    File base=context.getNoBackupFilesDir();String key=base.getCanonicalPath();State s=states.get(key);
    if(s==null){s=new State(base);s.shared=group(read(s.sharedFile));s.history.putAll(readHistory(s));
      for(Map.Entry<String,?> e:context.getSharedPreferences("shared-word-choices",0).getAll().entrySet())if(e.getKey().matches("[a-z]{1,128}")&&e.getValue() instanceof String&&LearningFile.validWord((String)e.getValue()))s.legacy.put(e.getKey(),(String)e.getValue());
      states.put(key,s);}return s;
  }
  private static void historyFiles(Map<String,LearningFile.Entry> history,File folder,int depth)throws IOException {
    if(depth>2)return;File[] files=folder.listFiles();if(files==null)return;
    for(File file:files){if(file.isDirectory()){historyFiles(history,file,depth+1);continue;}
      if(!file.getName().equals("rime_ice.userdb.txt")||file.length()>DictionaryFile.MAX_BYTES)continue;
      for(LearningFile.Entry e:LearningFile.rimeSnapshot(new FileInputStream(file))){LearningFile.Entry before=history.get(e.key());if(before==null||before.count<e.count)history.put(e.key(),e);}
    }
  }
  private static Map<String,LearningFile.Entry> readHistory(State s)throws IOException {
    Map<String,LearningFile.Entry> history=new HashMap<>();
    File user=new File(s.base,"local-rime/user"),installation=new File(user,"installation.yaml");
    if(!installation.isFile()||installation.length()>65536)return history;
    java.util.regex.Pattern id=java.util.regex.Pattern.compile("^installation_id:\\s*[\"']?([a-zA-Z0-9][a-zA-Z0-9_.-]{0,127})[\"']?\\s*$");
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(installation),java.nio.charset.StandardCharsets.UTF_8))){String line;
      while((line=reader.readLine())!=null){java.util.regex.Matcher match=id.matcher(line);if(match.matches()){historyFiles(history,new File(user,"sync/"+match.group(1)),0);break;}}
    }return history;
  }
  private static List<LearningFile.Entry> local(State s,String code)throws IOException {
    List<LearningFile.Entry> rows=s.local.get(code);
    if(rows==null){rows=read(new AtomicFile(new File(s.directory,code+".tsv")));for(LearningFile.Entry e:rows)if(!e.code.equals(code))throw new IOException("learning filename mismatch");
      String legacy=s.legacy.get(code);if(legacy!=null){boolean found=false;for(LearningFile.Entry e:rows)if(e.word.equals(legacy))found=true;
        if(!found)rows=LearningFile.merge(rows,Collections.singletonList(new LearningFile.Entry(legacy,code,1,0)));}
      s.local.put(code,rows);}
    return rows;
  }
  private static void write(AtomicFile target,List<LearningFile.Entry> rows)throws IOException {
    File parent=target.getBaseFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("learning directory unavailable");
    FileOutputStream stream=null;
    try{stream=target.startWrite();stream.write(LearningFile.encode(rows,false));target.finishWrite(stream);}
    catch(IOException error){if(stream!=null)target.failWrite(stream);throw error;}
  }
  public static synchronized void record(Context context,String raw,String word)throws IOException {
    String code=DictionaryFile.normalize(raw);if(!code.matches("[a-z]{1,128}")||!LearningFile.validWord(word))return;
    State s=state(context);List<LearningFile.Entry> rows=local(s,code);int count=0;long latest=0;
    for(LearningFile.Entry e:rows){latest=Math.max(latest,e.last);if(e.word.equals(word))count=e.count;}
    List<LearningFile.Entry> remote=s.shared.get(code);if(remote!=null)for(LearningFile.Entry e:remote)latest=Math.max(latest,e.last);
    LearningFile.Entry baseline=s.history.get(word+"\t"+code);if(baseline!=null)count=Math.max(count,baseline.count);
    long now=System.currentTimeMillis();LearningFile.Entry e=new LearningFile.Entry(word,code,Math.min(1000000,count+1),Math.max(now,Math.min(now+300000,latest+1)));
    List<LearningFile.Entry> changed=LearningFile.merge(rows,Collections.singletonList(e));
    write(new AtomicFile(new File(s.directory,code+".tsv")),changed);s.local.put(code,changed);
  }
  public static synchronized List<LearningFile.Entry> match(Context context,String raw)throws IOException {
    String code=DictionaryFile.normalize(raw);if(!code.matches("[a-z]{1,128}"))return Collections.emptyList();
    State s=state(context);List<LearningFile.Entry> shared=s.shared.get(code);
    return LearningFile.merge(shared==null?Collections.emptyList():shared,local(s,code));
  }
  public static synchronized String preferred(Context context,String raw)throws IOException {
    String code=DictionaryFile.normalize(raw);if(!code.matches("[a-z]{1,128}"))return "";
    State s=state(context);long newestShared=0,newestOwn=0;String selected="";
    List<LearningFile.Entry> remote=s.shared.get(code);
    if(remote!=null)for(LearningFile.Entry e:remote)newestShared=Math.max(newestShared,e.last);
    for(LearningFile.Entry e:local(s,code))if(e.last>newestOwn){newestOwn=e.last;selected=e.word;}
    if(newestOwn==0&&newestShared==0)return context.getSharedPreferences("shared-word-choices",0).getString(code,"");
    return newestOwn>=newestShared?selected:"";
  }
  private static boolean covered(Map<String,List<LearningFile.Entry>> shared,List<LearningFile.Entry> rows){
    for(LearningFile.Entry row:rows){boolean found=false;List<LearningFile.Entry> existing=shared.get(row.code);
      if(existing!=null)for(LearningFile.Entry e:existing)if(e.word.equals(row.word)&&e.count>=row.count&&e.last>=row.last){found=true;break;}
      if(!found)return false;
    }return true;
  }
  public static void mergeShared(Context context,List<LearningFile.Entry> rows)throws IOException {
    synchronized(sharedUpdates){
      State s;Map<String,List<LearningFile.Entry>> shared;
      synchronized(LearningStore.class){s=state(context);shared=s.shared;}
      if(covered(shared,rows))return;
      List<LearningFile.Entry> before=new ArrayList<>();for(List<LearningFile.Entry> g:shared.values())before.addAll(g);
      List<LearningFile.Entry> changed=LearningFile.merge(before,rows);Map<String,List<LearningFile.Entry>> next=group(changed);
      write(s.sharedFile,changed);
      synchronized(LearningStore.class){s.shared=next;}
    }
  }
  public static List<LearningFile.Entry> snapshotOwn(Context context)throws IOException {
    State s;synchronized(LearningStore.class){s=state(context);}
    // Snapshot parsing and the final merge can take seconds on a cold device.
    // Keep them outside the monitor used by preferred()/match()/record().
    Map<String,LearningFile.Entry> history=readHistory(s);
    Set<String> codes=new HashSet<>(s.legacy.keySet());
    File[] files=s.directory.listFiles();if(files!=null)for(File f:files){if(!f.getName().matches("[a-z]{1,128}\\.tsv(?:\\.bak|\\.new)?"))continue;codes.add(f.getName().replaceFirst("\\.tsv(?:\\.bak|\\.new)?$",""));}
    for(String code:codes)synchronized(LearningStore.class){local(s,code);}
    List<LearningFile.Entry> result;
    synchronized(LearningStore.class){
      for(LearningFile.Entry e:history.values()){LearningFile.Entry before=s.history.get(e.key());if(before==null||before.count<e.count)s.history.put(e.key(),e);}
      result=new ArrayList<>(s.history.values());for(List<LearningFile.Entry> rows:s.local.values())result.addAll(rows);
    }
    // s.shared is deliberately excluded, preventing count feedback on retries.
    // Flatten first: repeatedly sorting the growing full history for every
    // pinyin file made this quadratic in the number of local learning files.
    return LearningFile.merge(result,Collections.emptyList());
  }
  public static void reload(Context context)throws IOException {synchronized(sharedUpdates){synchronized(LearningStore.class){states.remove(context.getNoBackupFilesDir().getCanonicalPath());}}}
  /** Reuse the IME's one-shot initialization worker; no timer or keep-alive. */
  public static synchronized void preload(Context context)throws IOException {state(context);}
}
