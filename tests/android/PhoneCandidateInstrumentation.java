package local.gboard.phonecandidatecheck;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Headless synthetic checks; use a test AVD or a phone with only the probe editor active. */
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
      Context target=getTargetContext();Class<?> store=Class.forName("local.gboard.enhanced.DictionaryStore");store.getMethod("load",Context.class).invoke(null,target);
      final java.io.File isolated=new java.io.File(target.getCacheDir(),"candidate-learning-check-"+System.nanoTime());isolated.mkdirs();
      final String scope="candidate-check-"+System.nanoTime()+"-";
      Context context=new android.content.ContextWrapper(target){public java.io.File getNoBackupFilesDir(){return isolated;}public android.content.SharedPreferences getSharedPreferences(String n,int m){return super.getSharedPreferences(scope+n,m);}};
      android.content.SharedPreferences choices=context.getSharedPreferences("shared-word-choices",0);
      Map<String,?> savedChoices=new HashMap<>(choices.getAll());choices.edit().clear().commit();
      List<?> saved=(List<?>)store.getMethod("snapshot",Context.class).invoke(null,target);
      Method publish=store.getDeclaredMethod("publish",List.class);publish.setAccessible(true);
      Class<?> entry=Class.forName("local.gboard.enhanced.DictionaryFile$Entry");Constructor<?> make=entry.getDeclaredConstructor(String.class,String.class,int.class);make.setAccessible(true);
      Class<?> session=Class.forName("local.gboard.enhanced.EnhancedHook$Session");Constructor<?> create=session.getDeclaredConstructor(Object.class);create.setAccessible(true);
      Class<?> hook=Class.forName("local.gboard.enhanced.EnhancedHook");Method update=hook.getDeclaredMethod("update",session,String[].class);update.setAccessible(true);
      Method selectedCode=hook.getDeclaredMethod("selectedCode",String.class,String[].class,String.class);selectedCode.setAccessible(true);
      if(!"zhongguo".equals(selectedCode.invoke(null,"zhongguoren",new String[]{"zhongguoren","11","","中国ren"},"中国")))throw new Exception("partial selection learned the unconverted suffix");
      if(!"zhi".equals(selectedCode.invoke(null,"zhi",new String[]{"","0","只",""},"只")))throw new Exception("full selection code lost");
      if(!"".equals(selectedCode.invoke(null,"zhi",new String[]{"zhi","3","","zhi"},"只")))throw new Exception("uncommitted selection counted");
      String[] nativeText={"是","时","事","室","诗","市","师","石","识","十"};int checked=0;
      try{
        ArrayList<String> codes=new ArrayList<>();for(char c='a';c<='z';c++)codes.add(String.valueOf(c));Collections.addAll(codes,"zhi","de","shi","nihao","mingtian","zhongguo","lv","nv","nh","mt");
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
          if(!privacy&&novel<5)throw new Exception("native first page masked: "+code);
          if(!privacy){
            Class<?> files=Class.forName("local.gboard.enhanced.LearningFile$Entry");
            Object remote=files.getConstructor(String.class,String.class,int.class,long.class).newInstance("诗",code,100,System.currentTimeMillis());
            Class<?> learningStore=Class.forName("local.gboard.enhanced.LearningStore");
            learningStore.getMethod("mergeShared",Context.class,List.class).invoke(null,context,Collections.singletonList(remote));
            update.invoke(null,state,(Object)data);
            Object first=get(((List<?>)get(state,"candidates")).get(0),"m");
            if(!"诗".equals(get(first,"text"))||!Integer.valueOf(4).equals(get(first,"index")))throw new Exception("remote native learning not consumed: "+code);
            Class<?> learning=Class.forName("local.gboard.enhanced.SelectionLearning");
            Method record=learning.getMethod("record",Context.class,String.class,String.class);
            record.invoke(null,context,code,"云词哨兵");
            update.invoke(null,state,(Object)data);
            List<?> learned=(List<?>)get(state,"candidates");
            if(!"云词哨兵".equals(get(get(learned.get(0),"m"),"text")))throw new Exception("new shared word did not learn");
            record.invoke(null,context,code,nativeText[0]);
            update.invoke(null,state,(Object)data);
            learned=(List<?>)get(state,"candidates");
            if(!nativeText[0].equals(get(get(learned.get(0),"m"),"text")))throw new Exception("later native choice did not replace shared choice");
          }
          checked++;
        }
      }finally{publish.invoke(null,saved);android.content.SharedPreferences.Editor restore=choices.edit().clear();for(Map.Entry<String,?> row:savedChoices.entrySet())restore.putString(row.getKey(),(String)row.getValue());restore.commit();target.deleteSharedPreferences(scope+"shared-word-choices");remove(isolated);}
      result.putInt("adapter_cases",checked);result.putBoolean("remote_counts_reorder_native_candidates",true);result.putBoolean("native_learning_ids_preserved",true);result.putBoolean("known_shared_words_do_not_mask_native_rank",true);result.putBoolean("all_native_first_pages_preserved",true);result.putBoolean("novel_shared_selection_learns",true);result.putBoolean("later_native_choice_clears_shared_boost",true);result.putBoolean("private_mode_excludes_personal_words",true);result.putBoolean("passed",true);
    }catch(Throwable error){failure[0]=error;}});
    if(failure[0]!=null)result.putString("error",android.util.Log.getStackTraceString(failure[0]));finish(failure[0]==null?-1:0,result);
  }
  static void remove(java.io.File file){java.io.File[] children=file.listFiles();if(children!=null)for(java.io.File child:children)remove(child);file.delete();}
}
