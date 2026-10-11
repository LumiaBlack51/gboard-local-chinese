package local.gboard.enhanced;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;

/** Adapter only for Gboard's Chinese QWERTY decode processor. */
public final class EnhancedHook {
  private static final Map<Object,Session> sessions=new WeakHashMap<>();
  private static Session active;
  private static class Candidate {
    final Session session;final int index;final String text,code;
    Candidate(Session s,int i,String t){this(s,i,t,"");}
    Candidate(Session s,int i,String t,String c){session=s;index=i;text=t;code=c;}
  }
  private static class Session {
    final Object processor;boolean privateMode;long id;String raw="";int caret;
    List<Object> candidates=Collections.emptyList();int page;boolean composing,suggesting;String context="";
    Session(Object p){processor=p;}
  }
  private static Field field(Class<?> cls,String name)throws Exception {
    for(Class<?> c=cls;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException missing){}
    throw new NoSuchFieldException(name);
  }
  private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
  private static void put(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
  private static Object call(Object o,String name,Object...args)throws Exception {
    for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())
      for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){
        Class<?>[] types=m.getParameterTypes();boolean match=true;
        for(int i=0;i<types.length;i++)if(args[i]!=null&&!types[i].isPrimitive()&&!types[i].isInstance(args[i]))match=false;
        if(match){m.setAccessible(true);return m.invoke(o instanceof Class?null:o,args);}
      }
    throw new NoSuchMethodException(name);
  }
  public static synchronized boolean dispatch(Object processor,Object event) {
    try {
      int type=(Integer)get(event,"B");
      if(type==2)RimeBridge.prepare((Context)get(processor,"u"));
      if(!processor.getClass().getName().endsWith("HmmPinyinQwertyDecodeProcessor"))return false;
      Session s=sessions.get(processor);
      if(s==null){s=new Session(processor);sessions.put(processor,s);}
      if(type==2){
        if(s.id!=0){RimeBridge.destroy(s.id);s.id=0;}
        EditorInfo editor=(EditorInfo)get(event,"b");
        int variation=editor.inputType&0xfff;
        s.privateMode=(editor.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0||variation==0x81||variation==0x91||variation==0xe1||variation==0x12;
        s.raw="";s.context="";s.composing=false;s.suggesting=false;s.candidates=Collections.emptyList();active=s;return false;
      }
      if(!RimeBridge.ready)return false;
      if(s.id==0){s.id=RimeBridge.create(s.privateMode);if(s.id==0)return false;}
      if(type==4){active=s;return key(s,get(event,"i"));}
      if(type==8&&(s.composing||s.suggesting)){publish(s,(Integer)get(event,"m"));return true;}
      if(type==10||type==14||type==22){
        Object candidate=get(event,"j");if(candidate==null)return false;
        Object data=get(candidate,"m");if(!(data instanceof Candidate))return false;
        Candidate chosen=(Candidate)data;if(chosen.session!=s)return true;
        if(type==22){if(!s.privateMode&&chosen.index>=0){RimeBridge.forget(s.id,chosen.index);update(s);}return true;}
        if((Boolean)get(event,"k")){
          choose(s,chosen);
        }
        return true;
      }
      if(type==15||type==20||type==25||type==26){
        if(s.composing)finish(s);
        if(type==25||type==26){RimeBridge.destroy(s.id);s.id=0;if(active==s)active=null;}
        return false;
      }
      // Do not allow the native HMM callback to overwrite the Rime candidate list.
      if((s.composing||s.suggesting)&&(type==31||type==6))return true;
      return false;
    }catch(Throwable error){Log.e("LocalChinese","Adapter failure",error);return false;}
  }
  private static void choose(Session s,Candidate chosen)throws Exception {
    if(chosen.index>=0){RimeBridge.select(s.id,chosen.index);update(s);}
    else if(chosen.index==-1){
            RimeBridge.clear(s.id);for(char ch:chosen.code.toCharArray())RimeBridge.key(s.id,ch);
            String[] state=RimeBridge.state(s.id);int found=-1;
            for(int i=4;i<state.length;i++)if(state[i].equals(chosen.text)){found=i-4;break;}
            if(found>=0){RimeBridge.select(s.id,found);update(s);}
            else{RimeBridge.clear(s.id);commit(s,chosen.text);update(s);}
    }else{commit(s,chosen.text);update(s);}
  }
  private static boolean key(Session s,Object event)throws Exception {
    Object key=call(event,"g");if(key==null)return false;
    int code=(Integer)get(key,"c");Object value=get(key,"e");
    String text=value instanceof CharSequence?value.toString():"";
    if(text.length()==1&&((text.charAt(0)>='a'&&text.charAt(0)<='z')||text.charAt(0)=='\'')){
      if(!s.composing){
        // An in-progress composition that started before initialization stays
        // with the original decoder until it has been committed or cleared.
        if((Boolean)get(s.processor,"n"))return false;
        call(s.processor,"at",true);active=s;
      }
      RimeBridge.key(s.id,text.charAt(0));update(s);return true;
    }
    if(!s.composing)return false;
    if(code==67){RimeBridge.key(s.id,0xff08);update(s);return true;}
    if(code==21||code==22){move(s,s.caret+(code==21?-1:1));return true;}
    if(code==62){chooseFirst(s);return true;}
    if(code==66){finish(s);return false;}
    if(text.length()==1){finish(s);return false;}
    return false;
  }
  private static void finish(Session s)throws Exception {
    if(!s.composing)return;
    chooseFirst(s);
    // Rime may have selected only the first segment; complete remaining segments.
    int count=0;while(s.composing&&count++<64){RimeBridge.key(s.id,' ');update(s);}
  }
  private static void chooseFirst(Session s)throws Exception {
    if(!s.candidates.isEmpty())choose(s,(Candidate)get(s.candidates.get(0),"m"));
    else{commit(s,s.raw);RimeBridge.clear(s.id);update(s);}
  }
  public static synchronized void finishActive()throws Exception {if(active!=null&&active.composing)finish(active);}
  private static void update(Session s)throws Exception {
    String[] result=RimeBridge.state(s.id);s.raw=result[0];s.caret=Integer.parseInt(result[1]);
    if(!result[2].isEmpty())commit(s,result[2]);
    s.composing=!s.raw.isEmpty();
    String display=s.raw.substring(0,Math.min(s.caret,s.raw.length()))+"│"+s.raw.substring(Math.min(s.caret,s.raw.length()));
    call(s.processor,"au",s.composing?display:"");
    ArrayList<Object> list=new ArrayList<>();HashSet<String> seen=new HashSet<>();
    if(s.composing)for(WordIndex.Entry e:LocalWords.match(s.raw))if(seen.add(e.word))list.add(candidate(new Candidate(s,-1,e.word,e.code),list.size()));
    for(int i=4;i<Math.min(result.length,6);i++)if(seen.add(result[i]))list.add(candidate(new Candidate(s,i-4,result[i]),list.size()));
    if(s.composing)for(WordIndex.Entry e:WordIndex.complete(s.raw))if(seen.add(e.word))list.add(candidate(new Candidate(s,-1,e.word,e.code),list.size()));
    for(int i=6;i<result.length;i++)if(seen.add(result[i]))list.add(candidate(new Candidate(s,i-4,result[i]),list.size()));
    if(!s.composing)for(WordIndex.Entry e:WordIndex.following(s.context))if(seen.add(e.word))list.add(candidate(new Candidate(s,-2,e.word),list.size()));
    s.suggesting=!s.composing&&!list.isEmpty();
    s.candidates=list;s.page=0;
    call(s.processor,"aJ",!list.isEmpty());
    // Normal candidate requests flow through dispatch(type=8).
    if(list.isEmpty())sendCandidates(s,Collections.emptyList(),false);
  }
  private static void commit(Session s,String text)throws Exception {
    call(s.processor,"aK",text,1);s.context+=text;if(s.context.length()>32)s.context=s.context.substring(s.context.length()-32);
  }
  private static Object candidate(Candidate data,int rank)throws Exception {
    Object b=Class.forName("oqd").getDeclaredConstructor().newInstance();
    put(b,"a",data.text);put(b,"m",data);put(b,"k",rank);put(b,"l",rank);
    if(data.index==-2)put(b,"e",getStatic("oqf","c"));return call(b,"a");
  }
  private static Object getStatic(String cls,String name)throws Exception{return field(Class.forName(cls),name).get(null);}
  private static void publish(Session s,int count)throws Exception {
    int end=Math.min(s.page+Math.max(1,count),s.candidates.size());
    List<Object> page=new ArrayList<>(s.candidates.subList(s.page,end));s.page=end;
    sendCandidates(s,page,end<s.candidates.size());
  }
  private static void sendCandidates(Session s,List<Object> list,boolean more)throws Exception {
    Object e=call(Class.forName("otf"),"a",list,list.isEmpty()?null:list.get(0),more,s.processor);
    call(get(s.processor,"A"),"a",e);
  }
  private static void move(Session s,int position)throws Exception {
    RimeBridge.caret(s.id,Math.max(0,Math.min(s.raw.length(),position)));update(s);
  }
  public static synchronized void bindComposingView(Object composingView) {
    try {
      final View overlay=(View)get(composingView,"c");final TextView text=(TextView)get(composingView,"d");
      final Session s=active;
      if(s==null||!s.composing){overlay.setOnTouchListener(null);overlay.setClickable(false);overlay.setEnabled(false);return;}
      overlay.setEnabled(true);overlay.setClickable(true);
      overlay.setOnTouchListener((view,event)->{
        if(event.getAction()==MotionEvent.ACTION_DOWN||event.getAction()==MotionEvent.ACTION_MOVE){
          synchronized(EnhancedHook.class){
            try {
              int nearest=0;android.text.Layout layout=text.getLayout();
              if(layout!=null){
                int[] location=new int[2];text.getLocationOnScreen(location);
                float x=event.getRawX()-location[0]-text.getTotalPaddingLeft()+text.getScrollX();
                int y=(int)(event.getRawY()-location[1]-text.getTotalPaddingTop()+text.getScrollY());
                int offset=layout.getOffsetForHorizontal(layout.getLineForVertical(y),x);
                CharSequence visible=text.getText();
                // Ignore the displayed caret and any formatting separators.
                for(int i=0;i<Math.min(offset,visible.length())&&nearest<s.raw.length();i++)if(Character.toLowerCase(visible.charAt(i))==s.raw.charAt(nearest))nearest++;
              }else{
                float x=event.getX()-text.getPaddingLeft(),best=Float.MAX_VALUE;
                for(int i=0;i<=s.raw.length();i++){float distance=Math.abs(text.getPaint().measureText(s.raw,0,i)-x);if(distance<best){best=distance;nearest=i;}}
              }
              move(s,nearest);
            }catch(Throwable error){Log.e("LocalChinese","Caret editing failed",error);}
          }
        }
        return true;
      });
    }catch(Throwable error){Log.e("LocalChinese","Composing view binding failed",error);}
  }
}
