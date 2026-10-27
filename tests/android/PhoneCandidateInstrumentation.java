package local.gboard.phonecandidatecheck;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only, headless checks of the shipped Android adapter and candidate IDs. */
public final class PhoneCandidateInstrumentation extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  static Field field(Class<?> c,String name)throws Exception{for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
  static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
  static void put(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
  public static final class Processor {
    public Context u;public boolean n;public Object A=new Object();
    Processor(Context c){u=c;}public void au(CharSequence raw){n=raw.length()>0;}public void aJ(boolean b){}public void aK(CharSequence text,int cursor){}
  }
  public void onStart(){final Bundle result=new Bundle();final Throwable[] failure={null};
    runOnMainSync(()->{try{
      Context context=getTargetContext();Class<?> store=Class.forName("local.gboard.enhanced.DictionaryStore");store.getMethod("load",Context.class).invoke(null,context);
      List<?> saved=(List<?>)store.getMethod("snapshot",Context.class).invoke(null,context);
      Method publish=store.getDeclaredMethod("publish",List.class);publish.setAccessible(true);
      Class<?> entry=Class.forName("local.gboard.enhanced.DictionaryFile$Entry");Constructor<?> make=entry.getDeclaredConstructor(String.class,String.class,int.class);make.setAccessible(true);
      Class<?> session=Class.forName("local.gboard.enhanced.EnhancedHook$Session");Constructor<?> create=session.getDeclaredConstructor(Object.class);create.setAccessible(true);
      Class<?> hook=Class.forName("local.gboard.enhanced.EnhancedHook");Method update=hook.getDeclaredMethod("update",session,String[].class);update.setAccessible(true);
      String[] nativeText={"是","时","事","室","诗","市","师","石","识","十"};int checked=0;
      try{
        ArrayList<String> codes=new ArrayList<>();for(char c='a';c<='z';c++)codes.add(String.valueOf(c));Collections.addAll(codes,"shi","nihao","mingtian","zhongguo","lv","nv","nh","mt");
        for(String code:codes)for(boolean privacy:new boolean[]{false,true}){
          List<Object> imported=new ArrayList<>();imported.add(make.newInstance("诗",code,1000000));imported.add(make.newInstance("云词哨兵",code,100));publish.invoke(null,imported);
          Object state=create.newInstance(new Processor(context));put(state,"privateMode",privacy);
          String[] data=new String[4+nativeText.length];data[0]=code;data[1]=Integer.toString(code.length());data[2]="";data[3]=code;System.arraycopy(nativeText,0,data,4,nativeText.length);
          update.invoke(null,state,(Object)data);
          List<?> candidates=(List<?>)get(state,"candidates");HashSet<String> seen=new HashSet<>();int novel=-1,previous=-1;
          for(int i=0;i<candidates.size();i++){
            Object candidate=get(candidates.get(i),"m");String text=(String)get(candidate,"text");int index=(Integer)get(candidate,"index");
            if(!seen.add(text))throw new Exception("duplicate candidate");
            int nativeIndex=Arrays.asList(nativeText).indexOf(text);
            if(nativeIndex>=0){if(index!=nativeIndex||nativeIndex<=previous)throw new Exception("native candidate ID/order changed");previous=nativeIndex;}
            if(text.equals("云词哨兵"))novel=i;
          }
          if(privacy&&novel!=-1)throw new Exception("personal word visible in private mode");
          if(!privacy&&novel<0)throw new Exception("new cloud word absent");
          if(!privacy&&code.length()==1&&novel<5)throw new Exception("one-letter native first page masked");
          checked++;
        }
      }finally{publish.invoke(null,saved);}
      result.putInt("adapter_cases",checked);result.putBoolean("native_learning_ids_preserved",true);result.putBoolean("known_shared_words_do_not_mask_native_rank",true);result.putBoolean("single_initial_first_page_is_native",true);result.putBoolean("private_mode_excludes_personal_words",true);result.putBoolean("passed",true);
    }catch(Throwable error){failure[0]=error;}});
    if(failure[0]!=null)result.putString("error",android.util.Log.getStackTraceString(failure[0]));finish(failure[0]==null?-1:0,result);
  }
}
