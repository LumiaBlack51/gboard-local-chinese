package local.gboard.splitpinyincheck;

import android.app.Instrumentation;
import android.content.Context;
import android.os.*;
import android.view.inputmethod.EditorInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;

/** Run on a disposable AVD. Exercises real Gboard candidate messages and Rime with a synthetic editor. */
public final class SplitPinyinInstrumentation extends Instrumentation {
  private Bundle args;
  private final Bundle report=new Bundle();
  private Method dispatch;
  private FakeHmmPinyinQwertyDecodeProcessor processor;

  public static final class Event {
    public int B,m;public Object i,j;public boolean k;public EditorInfo b;
    Event(int type){B=type;}
  }
  public static final class Key {
    public int c;public String e;
    Key(int code,String text){c=code;e=text;}
    public Key g(){return this;}
    public int a(){return c;}
  }
  static Object field(Object o,String name)throws Exception{
    Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
  }
  public static final class Gate {
    List<?> items;boolean more;
    public void a(Object event)throws Exception{items=(List<?>)field(event,"s");more=(Boolean)field(event,"r");}
  }
  public static final class FakeHmmPinyinQwertyDecodeProcessor {
    public Context u;public boolean n;public Gate A=new Gate();String text="";
    public void at(boolean clear){n=false;}
    public void au(CharSequence value){n=value.length()>0;}
    public void aJ(boolean enabled){}
    public void aK(CharSequence value,int cursor){text+=value;}
  }

  public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
  private void begin(boolean privacy)throws Exception{
    processor.n=false;
    Event e=new Event(2);e.b=new EditorInfo();e.b.inputType=1;
    if(privacy)e.b.imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    dispatch.invoke(null,processor,e);
  }
  private boolean key(int code,String text)throws Exception{
    Event e=new Event(4);e.i=new Key(code,text);return (Boolean)dispatch.invoke(null,processor,e);
  }
  private void type(String spelling)throws Exception{
    for(char c:spelling.toCharArray())require(key(c,String.valueOf(c)),"letter not consumed");
  }
  private List<?> candidates()throws Exception{
    ArrayList<Object> all=new ArrayList<>();Set<String> words=new HashSet<>();
    do{
      Event e=new Event(8);e.m=23;require((Boolean)dispatch.invoke(null,processor,e),"page not handled");
      for(Object item:processor.A.items){require(words.add((String)field(item,"a")),"duplicate across pages");all.add(item);}
      require(!processor.A.more||!processor.A.items.isEmpty(),"empty intermediate page");
    }while(processor.A.more);
    return all;
  }
  private int rank(List<?> choices,String word)throws Exception{
    for(int i=0;i<choices.size();i++)if(word.equals(field(choices.get(i),"a")))return i+1;
    return 0;
  }
  private void choose(List<?> choices,String word)throws Exception{
    int rank=rank(choices,word);require(rank>0,"missing "+word);
    String before=processor.text;
    Event e=new Event(10);e.j=choices.get(rank-1);e.k=true;
    require((Boolean)dispatch.invoke(null,processor,e)&&processor.text.equals(before+word),"wrong commit for "+word);
  }
  private void part(String code,String word)throws Exception{type(code);choose(candidates(),word);}
  private boolean recordedWholeWord(Context context)throws Exception{
    Class<?> learning=Class.forName("local.gboard.enhanced.SelectionLearning");
    List<?> rows=(List<?>)learning.getMethod("match",Context.class,String.class).invoke(null,context,"xiaojing");
    for(Object row:rows)if("小鲸".equals(field(row,"word")))return true;
    return false;
  }
  private static void require(boolean pass,String message){if(!pass)throw new AssertionError(message);}

  private void auditTables(Context context,Class<?> bridge)throws Exception{
    Map<String,Set<String>> readings=new TreeMap<>();Set<String> characters=new HashSet<>();
    for(String table:new String[]{"8105","41448"}){
      String asset="local-rime/cn_dicts/"+table+".dict.yaml";
      try(BufferedReader in=new BufferedReader(new InputStreamReader(context.getAssets().open(asset),StandardCharsets.UTF_8))){
        String line;boolean data=false;
        while((line=in.readLine())!=null){
          if(!data){data=line.equals("...");continue;}
          if(line.startsWith("#"))continue;
          String[] cells=line.split("\t");
          if(cells.length<2||cells[0].codePointCount(0,cells[0].length())!=1)continue;
          require(cells[1].matches("[a-z]+"),"unsupported table spelling "+cells[1]);
          readings.computeIfAbsent(cells[1],unused->new HashSet<>()).add(cells[0]);
          characters.add(cells[0]);
        }
      }
    }
    Method key=bridge.getMethod("key",long.class,int.class),state=bridge.getMethod("state",long.class);
    Method page=bridge.getDeclaredMethod("candidatePage",long.class,int.class,int.class);page.setAccessible(true);
    Method clear=bridge.getMethod("clear",long.class);
    long session=(Long)bridge.getMethod("create",boolean.class).invoke(null,true);
    int checked=0,missing=0;List<String> examples=new ArrayList<>();
    try{
      for(Map.Entry<String,Set<String>> entry:readings.entrySet()){
        clear.invoke(null,session);
        for(char c:entry.getKey().toCharArray())key.invoke(null,session,(int)c);
        String[] head=(String[])state.invoke(null,session);
        Set<String> found=new HashSet<>(Arrays.asList(head).subList(4,head.length));
        for(int offset=head.length-4;offset>0;){
          String[] next=(String[])page.invoke(null,session,offset,100);
          Collections.addAll(found,next);if(next.length<100)break;offset+=next.length;
        }
        for(String word:entry.getValue()){
          checked++;
          if(!found.contains(word)){missing++;if(examples.size()<12)examples.add(word+"/"+entry.getKey());}
        }
      }
    }finally{bridge.getMethod("destroy",long.class).invoke(null,session);}
    report.putInt("characters",characters.size());report.putInt("spellings",readings.size());
    report.putInt("character_readings",checked);report.putInt("unreachable",missing);
    require(missing==0,"missing table readings "+examples);
  }

  public void onStart(){
    try{
      require(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"disposable AVD required");
      Context context=getTargetContext();Class<?> bridge=Class.forName("local.gboard.enhanced.RimeBridge");
      bridge.getMethod("prepare",Context.class).invoke(null,context);
      Field ready=bridge.getDeclaredField("ready");ready.setAccessible(true);
      long deadline=SystemClock.elapsedRealtime()+300000;
      while(!ready.getBoolean(null)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
      require(ready.getBoolean(null),"Rime startup timeout");
      dispatch=Class.forName("local.gboard.enhanced.EnhancedHook").getMethod("dispatch",Object.class,Object.class);
      processor=new FakeHmmPinyinQwertyDecodeProcessor();processor.u=context;
      final String mode=args.getString("mode","verify");
      if(mode.equals("audit")){
        auditTables(context,bridge);
      }else if(mode.equals("selected")){
        begin(false);type("xiaojing");require(rank(candidates(),"小鲸")==1,"explicit phrase choice did not persist");
      }else if(mode.equals("restart")){
        begin(false);type("xiaojing");List<?> choices=candidates();int inferred=rank(choices,"小鲸");
        require(inferred>0&&inferred<=3,"split suggestion did not persist");
        require(!recordedWholeWord(context),"inference counted as explicit choice");
        choose(choices,"小鲸");require(recordedWholeWord(context),"missing explicit selection");
        begin(false);type("xiaojing");require(key(62," ")&&processor.text.endsWith("小鲸小鲸"),"space did not select learned phrase");
      }else{
        boolean baseline=mode.equals("baseline");
        begin(true);type("cheng");List<?> choices=candidates();
        int cheng=rank(choices,"铖");report.putInt("cheng_rank",cheng);report.putInt("cheng_candidates",choices.size());
        choose(choices,"铖");
        begin(true);type("yi");choices=candidates();int deep=rank(choices,"㐹");report.putInt("deep_rank",deep);
        if(!baseline){
          require(deep>0,"large-table character missing");choose(choices,"㐹");
          begin(true);type("yi");choices=candidates();
          Object last=choices.get(choices.size()-1);
          require((Integer)field(field(last,"m"),"index")>=100,"deep native candidate missing");
          choose(choices,(String)field(last,"a"));
        }
        begin(false);type("xiaojing");require(rank(candidates(),"小鲸")==0,"start from clean AVD");
        for(int i=0;i<2;i++){begin(false);part("xiao","小");require(!key(62," "),"literal space consumed");part("jing","鲸");}
        for(int i=0;i<2;i++){begin(true);part("xiao","小");part("jing","鲸");}
        for(int i=0;i<2;i++){begin(false);part("xiao","小");begin(false);part("jing","鲸");}
        begin(false);part("xiao","小");part("jing","鲸");
        begin(false);type("xiaojing");require(rank(candidates(),"小鲸")==0,"one observation or interrupted pair promoted");
        begin(false);part("xiao","小");part("jing","鲸");
        begin(false);type("xiaojing");choices=candidates();int inferred=rank(choices,"小鲸");report.putInt("split_phrase_rank",inferred);
        if(baseline)require(inferred==0,"baseline already learns split phrase");
        else{
          require(inferred>0&&inferred<=3,"repeated split absent from first choices");
          begin(true);type("xiaojing");require(rank(candidates(),"小鲸")==0,"private suggestions leaked");
        }
      }
      report.putBoolean("passed",true);finish(-1,report);
    }catch(Throwable e){report.putString("error",android.util.Log.getStackTraceString(e));finish(0,report);}
  }
}
