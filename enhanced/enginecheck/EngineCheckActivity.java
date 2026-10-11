package local.gboard.enhanced;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.*;
import java.util.ArrayList;
import org.json.*;
public final class EngineCheckActivity extends Activity {
  TextView status;
  private String learned(File dir)throws IOException {
    ArrayList<String> lines=new ArrayList<>();collect(dir,lines);java.util.Collections.sort(lines);return String.join("\n",lines);
  }
  private void collect(File dir,java.util.List<String> lines)throws IOException {
    File[] files=dir.listFiles();if(files==null)return;
    for(File f:files)if(f.isDirectory())collect(f,lines);else if(f.getName().endsWith(".userdb.txt")){
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=reader.readLine())!=null)if(!line.isEmpty()&&!line.startsWith("#"))lines.add(line);}
    }
  }
  final String[][] cases={
    {"wangzifeibo","妄自菲薄"},{"zuojianfanke","作奸犯科"},
    {"yinyushiyi","引喻失义"},{"zhifazangpi","陟罚臧否"},
    {"jirierdai","计日而待"},{"zhenzhuosunyi","斟酌损益"},
    {"huadiweilao","画地为牢"},{"zhongyutaishan","重于泰山"},
    {"qingyuhongmao","轻于鸿毛"},{"shiyibuque","拾遗补阙"},
    {"zhaoxianjinneng","招贤进能"},{"yushifuyang","与时俯仰"},
    {"zhongwenyuce","中文预测"},{"yinsibaohu","隐私保护"},
    {"jintianxiawu","今天下午"},{"mingtianzaoshang","明天早上"},
    {"renjijiaohu","人机交互"},{"shurufayinqing","输入法引擎"},
    {"jiqixuexi","机器学习"},{"suanfatiaozheng","算法调整"},
    {"wangluoanquan","网络安全"},{"shengrikuaile","生日快乐"},
    {"xiangjianhenwan","相见恨晚"},{"buzhibujue","不知不觉"},
    {"zaixianhuiyi","在线会议"},{"yuanchengbangong","远程办公"},
    {"tuanduihezuo","团队合作"},{"shujubeifen","数据备份"},
    {"zidonggengxin","自动更新"},{"linghuoyingbian","灵活应变"},
    {"yinyusy","引喻失义"},{"yysy","引喻失义"},
    {"wzfb","妄自菲薄"},{"wl aq","网络安全"},
    {"yinsibh","隐私保护"},{"jintianx","今天下午"}
  };
  public void onCreate(Bundle state){
    super.onCreate(state);status=new TextView(this);status.setTextSize(20);
    status.setText("正在部署本地中文词库。测试仅使用公开短语，不联网。");setContentView(status);
    RimeBridge.prepare(this);
    new Thread(()->{try{
      long deadline=System.currentTimeMillis()+600000;
      while(!RimeBridge.ready&&System.currentTimeMillis()<deadline)Thread.sleep(200);
      if(!RimeBridge.ready)throw new IOException("Engine initialization did not finish");
      JSONArray rows=new JSONArray();long session=RimeBridge.create(true);
      for(String[] c:cases){
        RimeBridge.clear(session);long begin=System.nanoTime();
        for(char ch:c[0].replace(" ","").toCharArray())RimeBridge.key(session,ch);
        String[] snapshot=RimeBridge.state(session);
        JSONObject row=new JSONObject().put("pinyin",c[0]).put("target",c[1]).put("elapsed_ms",(System.nanoTime()-begin)/1000000.0);
        JSONArray candidates=new JSONArray();int rank=-1;
        for(int i=4;i<snapshot.length;i++){candidates.put(snapshot[i]);if(snapshot[i].equals(c[1]))rank=i-3;}
        row.put("rank",rank).put("candidates",candidates);rows.put(row);
      }
      RimeBridge.clear(session);for(char ch:"nihaoma".toCharArray())RimeBridge.key(session,ch);
      RimeBridge.caret(session,2);RimeBridge.key(session,'n');String[] inserted=RimeBridge.state(session);
      RimeBridge.key(session,0xff08);String[] deleted=RimeBridge.state(session);
      JSONObject cursor=new JSONObject().put("inserted_raw",inserted[0]).put("inserted_caret",inserted[1]).put("deleted_raw",deleted[0]).put("deleted_caret",deleted[1]);
      RimeBridge.destroy(session);
      JSONArray completion=new JSONArray(),following=new JSONArray();long lookupStart=System.nanoTime();
      for(WordIndex.Entry e:WordIndex.complete("jintianx"))completion.put(e.word);
      for(WordIndex.Entry e:WordIndex.following("隐私"))following.put(e.word);
      double lookupMs=(System.nanoTime()-lookupStart)/1000000.0;
      File user=new File(getNoBackupFilesDir(),"local-rime/user");RimeBridge.sync();String before=learned(user);
      long normal=RimeBridge.create(false);String chosen="";
      for(int repeat=0;repeat<4;repeat++){
        RimeBridge.clear(normal);for(char ch:"weilai".toCharArray())RimeBridge.key(normal,ch);
        String[] st=RimeBridge.state(normal);int idx=1;if(repeat==0)chosen=st[4+idx];else{idx=0;for(int i=4;i<st.length;i++)if(st[i].equals(chosen)){idx=i-4;break;}}
        RimeBridge.select(normal,idx);RimeBridge.state(normal);
      }
      RimeBridge.destroy(normal);RimeBridge.sync();String afterNormal=learned(user);
      long secret=RimeBridge.create(true);for(char ch:"weilai".toCharArray())RimeBridge.key(secret,ch);String[] reordered=RimeBridge.state(secret);RimeBridge.clear(secret);
      for(char ch:"zhongwenyuce".toCharArray())RimeBridge.key(secret,ch);RimeBridge.select(secret,0);RimeBridge.state(secret);RimeBridge.destroy(secret);RimeBridge.sync();String afterPrivate=learned(user);
      JSONObject learning=new JSONObject().put("selected",chosen).put("private_first",reordered.length>4?reordered[4]:"").put("normal_dictionary_changed",!before.equals(afterNormal)).put("private_dictionary_unchanged",afterNormal.equals(afterPrivate)).put("normal_export_bytes",afterNormal.length());
      long rec=VoiceBridge.create(VoiceBridge.prepare(this));if(rec==0)throw new IOException("Voice recognizer failed");
      StringBuilder transcript=new StringBuilder();long speechBegin=System.nanoTime();
      // RIFF fixture is generated locally with the installed Windows Chinese TTS.
      try(java.io.DataInputStream wav=new java.io.DataInputStream(getAssets().open("speech-fixture.wav"))){
        byte[] header=new byte[12];wav.readFully(header);
        while(true){byte[] tag=new byte[4];wav.readFully(tag);int bytes=Integer.reverseBytes(wav.readInt());
          if(new String(tag,"US-ASCII").equals("data")){
            short[] samples=new short[1600];while(bytes>0){int n=Math.min(samples.length,bytes/2);for(int i=0;i<n;i++)samples[i]=Short.reverseBytes(wav.readShort());bytes-=n*2;
              if(VoiceBridge.accept(rec,samples,n))transcript.append(VoiceBridge.parse(VoiceBridge.result(rec,1),"text"));}
            break;
          }else{int remaining=bytes+(bytes&1);while(remaining>0){int n=wav.skipBytes(remaining);if(n==0)throw new EOFException();remaining-=n;}}
        }
      }
      transcript.append(VoiceBridge.parse(VoiceBridge.result(rec,2),"text"));VoiceBridge.destroy(rec);
      JSONObject speech=new JSONObject().put("fixture","Windows Huihui local TTS, PCM16 mono 16000Hz").put("expected","今天下午召开会议请注意保护隐私").put("recognized",transcript.toString()).put("decode_ms",(System.nanoTime()-speechBegin)/1000000.0);
      JSONObject report=new JSONObject().put("cases",rows).put("cursor",cursor).put("completion",completion).put("following",following).put("lookup_ms",lookupMs).put("speech",speech).put("learning",learning);
      try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"engine-report.json"))){out.write(report.toString(2).getBytes("UTF-8"));}
      runOnUiThread(()->status.setText("本地测试完成，结果已保存。未确认拼音插入："+inserted[0]+"；删除："+deleted[0]));
    }catch(Throwable error){android.util.Log.e("LocalChinese","Engine check failed",error);runOnUiThread(()->status.setText(error.toString()));}},"EngineCheck").start();
  }
}
