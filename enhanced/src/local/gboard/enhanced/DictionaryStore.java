package local.gboard.enhanced;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Imported words are kept apart from Rime learning and the system dictionary. */
public final class DictionaryStore {
  private static List<DictionaryFile.Entry> entries=Collections.emptyList();
  private static volatile Map<String,List<WordIndex.Entry>> index=Collections.emptyMap();
  private static boolean loaded;
  private static AtomicFile file(Context context){
    return new AtomicFile(new File(context.getNoBackupFilesDir(),"local-dictionary/words.tsv"));
  }
  public static synchronized void load(Context context)throws IOException {
    if(loaded)return;
    AtomicFile source=file(context);
    if(source.getBaseFile().exists()||new File(source.getBaseFile()+".bak").exists())
      try(InputStream in=source.openRead()){publish(DictionaryFile.readStored(in));}
    loaded=true;
  }
  private static void publish(List<DictionaryFile.Entry> words){
    HashMap<String,List<WordIndex.Entry>> next=new HashMap<>();
    for(DictionaryFile.Entry e:words){List<WordIndex.Entry> group=next.get(e.code);
      if(group==null){group=new ArrayList<>();next.put(e.code,group);}group.add(new WordIndex.Entry(e.code,e.word,e.weight));}
    for(List<WordIndex.Entry> group:next.values()){group.sort((a,b)->Integer.compare(b.weight,a.weight));}
    entries=words;index=next;
  }
  public static synchronized int count(Context context)throws IOException {load(context);return entries.size();}
  public static synchronized int previewCount(Context context,List<DictionaryFile.Entry> words)throws IOException {
    load(context);return DictionaryFile.merge(entries,words).size();
  }
  public static synchronized void merge(Context context,List<DictionaryFile.Entry> words)throws IOException {
    load(context);List<DictionaryFile.Entry> merged=DictionaryFile.merge(entries,words);
    AtomicFile target=file(context);FileOutputStream stream=null;
    try{stream=target.startWrite();stream.write(DictionaryFile.encode(merged));target.finishWrite(stream);}
    catch(IOException error){if(stream!=null)target.failWrite(stream);throw error;}
    publish(merged);
  }
  public static synchronized void clear(Context context)throws IOException {
    AtomicFile target=file(context);FileOutputStream stream=null;
    // Commit an empty marker atomically; deleting several AtomicFile paths is not atomic.
    try{stream=target.startWrite();stream.write("# empty\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));target.finishWrite(stream);}
    catch(IOException error){if(stream!=null)target.failWrite(stream);throw error;}
    publish(Collections.emptyList());loaded=true;
  }
  public static List<WordIndex.Entry> match(String code){List<WordIndex.Entry> found=index.get(code);return found==null?Collections.emptyList():found;}
}
