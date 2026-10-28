package local.gboard.enhanced;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Signature-only phrase boundary. No input connection, audio, settings or database bytes. */
public final class DictionarySyncProvider extends ContentProvider {
  public static final String AUTHORITY="com.localx.android.inputmethod.latin.dictionary_sync";
  private boolean powered(){Intent battery=getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));return battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;}
  private void allowed(boolean allowBattery)throws IOException {
    if(!allowBattery&&!powered())throw new IOException("unplugged");
    // Imported phrase files and the volatile lookup index are independent of input state.
  }
  @Override public boolean onCreate(){return true;}
  @Override public Bundle call(String method,String arg,Bundle extras){
    // Provider.call does not enforce the manifest's read/write permission itself.
    getContext().enforceCallingOrSelfPermission("local.gboard.DICTIONARY_SYNC","Signature required");
    Bundle result=new Bundle();
    boolean allowBattery=extras!=null&&extras.getBoolean("allow_battery",false);
    try{allowed(allowBattery);
      if("merge".equals(method)){
        byte[] raw=extras==null?null:extras.getByteArray("words");if(raw==null||raw.length>512*1024)throw new IOException("invalid batch");
        List<DictionaryFile.Entry> words=DictionaryFile.readStored(new ByteArrayInputStream(raw));allowed(allowBattery);DictionaryStore.merge(getContext(),words);
      }else if("merge_learning".equals(method)){
        byte[] raw=extras==null?null:extras.getByteArray("learning");if(raw==null||raw.length>512*1024)throw new IOException("invalid learning batch");
        List<LearningFile.Entry> rows=LearningFile.read(new ByteArrayInputStream(raw),false);allowed(allowBattery);LearningStore.mergeShared(getContext(),rows);
      }else if(!"status".equals(method))throw new IOException("unsupported method");
      result.putBoolean("ok",true);result.putInt("count",DictionaryStore.count(getContext()));
    }catch(IOException error){result.putBoolean("ok",false);result.putString("error",error.getMessage());}
    return result;
  }
  @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
    if(!"r".equals(mode)||!"/export".equals(uri.getPath()))throw new FileNotFoundException("read-only export");
    final boolean allowBattery=Boolean.parseBoolean(uri.getQueryParameter("allow_battery"));
    try{allowed(allowBattery);ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();
      new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
        allowed(allowBattery);List<DictionaryFile.Entry> words=DictionaryStore.snapshot(getContext());
        // Rime owns its live LevelDB; only its supported text snapshot crosses this boundary.
        if(RimeBridge.ready&&!ToolbarHide.inputVisible()&&!EnhancedHook.composing()){RimeBridge.sync();allowed(allowBattery);}
        File sync=new File(getContext().getNoBackupFilesDir(),"local-rime/user/sync");
        words=readSnapshots(sync,words,0,allowBattery);allowed(allowBattery);out.write(DictionaryFile.encode(words));
        out.write(LearningFile.encode(LearningStore.snapshotOwn(getContext()),true));
      }catch(Throwable error){try{pipe[1].closeWithError("phrase export deferred");}catch(IOException ignored){}}},"PhraseExport").start();return pipe[0];
    }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
  }
  private List<DictionaryFile.Entry> readSnapshots(File root,List<DictionaryFile.Entry> words,int depth,boolean allowBattery)throws IOException {
    if(depth>2)return words;File[] files=root.listFiles();if(files==null)return words;
    for(File file:files){allowed(allowBattery);if(file.isDirectory()){words=readSnapshots(file,words,depth+1,allowBattery);continue;}
      if(!file.getName().equals("rime_ice.userdb.txt")||file.length()>DictionaryFile.MAX_BYTES)continue;
      ArrayList<DictionaryFile.Entry> learned=new ArrayList<>();
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){String line;
        while((line=reader.readLine())!=null){if(line.startsWith("#"))continue;String[] f=line.split("\t");if(f.length!=3)continue;
          java.util.regex.Matcher count=java.util.regex.Pattern.compile("(?:^| )c=([0-9]+)(?: |$)").matcher(f[2]);
          if(!count.find()||Long.parseLong(count.group(1))<=0)continue;
          String code=DictionaryFile.normalize(f[0]);String word=f[1];
          if(code.matches("[a-z]{1,128}")&&word.matches("[\\p{IsHan}]{1,32}"))learned.add(new DictionaryFile.Entry(word,code,(int)Math.min(1000000,100+Long.parseLong(count.group(1)))));
          if(learned.size()>DictionaryFile.MAX_WORDS)throw new IOException("too many learned words");
        }
      }words=DictionaryFile.merge(words,learned);
    }return words;
  }
  @Override public String getType(Uri uri){return "text/tab-separated-values";}
  @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
  @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
  @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
