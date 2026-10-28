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
    List<Object> candidates=Collections.emptyList();int page;boolean composing,suggesting,dismissed,handwriting,openingCandidates;String context="";
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
      if(!processor.getClass().getName().endsWith("HmmPinyinQwertyDecodeProcessor")){if(type==2){active=null;KeyboardGestures.reset();}return false;}
      if(type==2){RimeBridge.prepare((Context)get(processor,"u"));HandwritingBridge.prepare((Context)get(processor,"u"));}
      Session s=sessions.get(processor);
      if(s==null){s=new Session(processor);sessions.put(processor,s);}
      if(type==2){
        if(s.id!=0){RimeBridge.destroy(s.id);s.id=0;}
        EditorInfo editor=(EditorInfo)get(event,"b");
        int variation=editor.inputType&0xfff;
        s.privateMode=(editor.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0||variation==0x81||variation==0x91||variation==0xe1||variation==0x12;
        s.raw="";s.context="";s.composing=false;s.suggesting=false;s.dismissed=false;s.handwriting=false;s.candidates=Collections.emptyList();active=s;KeyboardGestures.reset();refreshCandidateBars();return false;
      }
      if(!RimeBridge.ready)return false;
      if(s.id==0){s.id=RimeBridge.create(s.privateMode);if(s.id==0)return false;}
      if(type==4){active=s;return key(s,get(event,"i"));}
      // User selection/cursor movement invalidates predictions from the old position.
      if(type==18&&!s.composing&&!(Boolean)call(Class.forName("owv"),"c",get(event,"e"))){if(s.handwriting)cancelInk();dismiss(s);return false;}
      if(type==8&&(s.composing||s.suggesting||s.handwriting)){publish(s,(Integer)get(event,"m"));return true;}
      if(type==10||type==14||type==22){
        Object candidate=get(event,"j");if(candidate==null)return false;
        Object data=get(candidate,"m");if(!(data instanceof Candidate))return false;
        Candidate chosen=(Candidate)data;if(chosen.session!=s)return true;
        boolean current=false;for(Object item:s.candidates)if(get(item,"m")==chosen){current=true;break;}
        if(!current)return true;
        if(type==22){if(!s.privateMode&&chosen.index>=0){RimeBridge.forget(s.id,chosen.index);update(s);}return true;}
        if((Boolean)get(event,"k")){
          choose(s,chosen);
        }
        return true;
      }
      if(type==15||type==20||type==25||type==26){
        // The native expansion command also sends a decoder-reset event.
        // Keep the same candidates while its panel is being opened.
        if(type==15&&(s.openingCandidates||isExpansionEvent(get(event,"i")))){s.openingCandidates=false;return true;}
        if(s.handwriting)cancelInk();
        if(s.composing)finish(s);
        if(s.suggesting)dismiss(s);
        if(type==25||type==26){RimeBridge.destroy(s.id);s.id=0;if(active==s)active=null;KeyboardGestures.reset();}
        return false;
      }
      // Do not allow the native HMM callback to overwrite the Rime candidate list.
      if((s.composing||s.suggesting||s.handwriting)&&(type==31||type==6))return true;
      return false;
    }catch(Throwable error){Log.e("LocalChinese","Adapter failure",error);return false;}
  }
  private static void choose(Session s,Candidate chosen)throws Exception {
    String learningCode=s.composing&&!s.privateMode?s.raw:"";
    if(chosen.index==-3){s.handwriting=false;KeyboardGestures.clearInk();commit(s,chosen.text);s.dismissed=false;update(s);return;}
    if(chosen.index==-4){RimeBridge.clear(s.id);commit(s,chosen.text);update(s);return;}
    if(chosen.index>=0){learningCode=select(s,chosen.index,chosen.text);}
    else if(chosen.index==-1){
            RimeBridge.clear(s.id);for(char ch:chosen.code.toCharArray())RimeBridge.key(s.id,ch);
            String[] state=RimeBridge.state(s.id);int found=-1;
            for(int i=4;i<state.length;i++)if(state[i].equals(chosen.text)){found=i-4;break;}
            if(found>=0){learningCode=select(s,found,chosen.text);}
            else{RimeBridge.clear(s.id);commit(s,chosen.text);update(s);}
    }else{commit(s,chosen.text);update(s);}
    if(!s.privateMode&&!learningCode.isEmpty())SelectionLearning.record((Context)get(s.processor,"u"),learningCode,chosen.text);
  }
  private static String selectedCode(String raw,String[] state,String text){
    if(!state[2].isEmpty())return raw;
    if(state[3].startsWith(text)){
      String tail=state[3].substring(text.length()).replace(" ","");
      if(!tail.isEmpty()&&tail.matches("[a-z']+")&&state[0].endsWith(tail)&&tail.length()<state[0].length())return state[0].substring(0,state[0].length()-tail.length());
    }
    return "";
  }
  private static String select(Session s,int index,String text)throws Exception {
    String raw=RimeBridge.state(s.id)[0];
    if(!RimeBridge.select(s.id,index))return "";
    String[] state=RimeBridge.state(s.id);
    String learned=selectedCode(raw,state,text);
    // Rime keeps a selected prefix inside its composition until every segment
    // is converted. Commit that prefix now, retaining the exact unconverted
    // input. Derive the boundary from Rime's preedit, never from word length or
    // a guessed pronunciation. This also handles an invalid trailing syllable.
    if(state[2].isEmpty()&&state[3].startsWith(text)){
      String tail=state[3].substring(text.length()).replace(" ","");
      if(!tail.isEmpty()&&tail.matches("[a-z']+")&&state[0].endsWith(tail)&&tail.length()<state[0].length()){
        int consumed=state[0].length()-tail.length();
        int caret=Math.max(0,Integer.parseInt(state[1])-consumed);
        RimeBridge.clear(s.id);commit(s,text);
        for(char ch:tail.toCharArray())RimeBridge.key(s.id,ch);
        RimeBridge.caret(s.id,Math.min(caret,tail.length()));
        state=RimeBridge.state(s.id);
      }
    }
    update(s,state);
    return learned;
  }
  private static boolean key(Session s,Object event)throws Exception {
    s.openingCandidates=isExpansionEvent(event);if(s.openingCandidates)return false;
    Object key=call(event,"g");if(key==null)return false;
    int code=(Integer)get(key,"c");Object value=get(key,"e");
    String text=value instanceof CharSequence?value.toString():"";
    if(s.handwriting){if(code==67){KeyboardGestures.undo();return true;}if(code==62||code==66||code==-10009){if(!s.candidates.isEmpty())chooseFirst(s);return true;}cancelInk();}
    if(text.length()==1&&((text.charAt(0)>='a'&&text.charAt(0)<='z')||text.charAt(0)=='\'')){
      s.dismissed=false;
      if(!s.composing){
        // An in-progress composition that started before initialization stays
        // with the original decoder until it has been committed or cleared.
        if((Boolean)get(s.processor,"n"))return false;
        call(s.processor,"at",true);active=s;
      }
      RimeBridge.key(s.id,text.charAt(0));update(s);return true;
    }
    if(!s.composing){if(s.suggesting)dismiss(s);return false;}
    if(code==67){RimeBridge.key(s.id,0xff08);update(s);return true;}
    if(code==21||code==22){move(s,s.caret+(code==21?-1:1));return true;}
    if(code==62){chooseFirst(s);return true;}
    if(code==66||code==-10009){
      // Enter confirms the literal spelling; a subsequent Enter performs the editor action.
      String literal=s.raw;RimeBridge.clear(s.id);commit(s,literal);s.dismissed=true;s.context="";update(s);return true;
    }
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
  public static synchronized void finishActive()throws Exception {if(active!=null&&active.handwriting)cancelInk();if(active!=null&&active.composing)finish(active);}
  public static synchronized void voiceCommitted(String text){
    try{if(active==null||!RimeBridge.ready)return;Session s=active;if(s.id==0)s.id=RimeBridge.create(s.privateMode);if(s.id==0)return;s.dismissed=false;
      s.context+=text;if(s.context.length()>32)s.context=s.context.substring(s.context.length()-32);
      RimeBridge.clear(s.id);update(s);
    }catch(Throwable error){Log.e("LocalChinese","Voice predictions failed",error);}
  }
  public static synchronized boolean handleEnter(Object event){
    try{Object key=call(event,"g");if(key==null||active==null||!active.composing&&!active.handwriting)return false;int code=(Integer)get(key,"c");if(code!=66&&code!=-10009)return false;return key(active,event);}
    catch(Throwable error){Log.e("LocalChinese","Literal Enter failed",error);return false;}
  }
  private static void update(Session s)throws Exception {
    update(s,RimeBridge.state(s.id));
  }
  private static void update(Session s,String[] result)throws Exception {
    s.raw=result[0];s.caret=Integer.parseInt(result[1]);
    if(!result[2].isEmpty())commit(s,result[2]);
    s.composing=!s.raw.isEmpty();
    String display=s.raw.substring(0,Math.min(s.caret,s.raw.length()))+"│"+s.raw.substring(Math.min(s.caret,s.raw.length()));
    call(s.processor,"au",s.composing?display:"");
    ArrayList<Candidate> choices=new ArrayList<>();HashSet<String> seen=new HashSet<>();
    HashSet<String> nativeWords=new HashSet<>();for(int i=4;i<result.length;i++)nativeWords.add(result[i]);
    int nativeLead=5;
    // Keep genuine Rime candidate IDs and ordering for shared known words.
    // They carry selection learning; a static imported row must not mask them.
    for(int i=4;i<Math.min(result.length,4+nativeLead);i++)if(seen.add(result[i]))choices.add(new Candidate(s,i-4,result[i]));
    if(s.composing&&!s.privateMode)for(WordIndex.Entry e:LocalWords.match(s.raw))if(!nativeWords.contains(e.word)&&seen.add(e.word))choices.add(new Candidate(s,-1,e.word,e.code));
    if(s.composing)for(WordIndex.Entry e:WordIndex.complete(s.raw))if(!nativeWords.contains(e.word)&&seen.add(e.word))choices.add(new Candidate(s,-1,e.word,e.code));
    for(int i=4+nativeLead;i<result.length;i++)if(seen.add(result[i]))choices.add(new Candidate(s,i-4,result[i]));
    if(s.composing&&!s.privateMode){
      Context app=(Context)get(s.processor,"u");String preferred=SelectionLearning.preferred(app,s.raw);
      List<LearningFile.Entry> learned=SelectionLearning.match(app,s.raw);
      if(!learned.isEmpty()){
        int added=0;
        for(int i=0;i<learned.size();i++){LearningFile.Entry e=learned.get(i);
          if(!nativeWords.contains(e.word)&&added<20&&seen.add(e.word)){choices.add(new Candidate(s,-1,e.word,e.code));added++;}}
        String[] texts=new String[choices.size()];int[] ids=new int[choices.size()];
        for(int i=0;i<choices.size();i++){texts[i]=choices.get(i).text;ids[i]=choices.get(i).index;}
        int[] order=SharedRanking.order(texts,ids,learned,preferred);ArrayList<Candidate> sorted=new ArrayList<>();
        for(int i:order)sorted.add(choices.get(i));choices=sorted;
      }
    }
    // Keep an unrecognized remainder visible without leaving the previous
    // segment's candidate objects on screen. Selecting it commits it literally.
    if(s.composing&&choices.isEmpty())choices.add(new Candidate(s,-4,s.raw));
    if(!s.composing&&!s.dismissed)for(WordIndex.Entry e:WordIndex.following(s.context))if(seen.add(e.word))choices.add(new Candidate(s,-2,e.word));
    ArrayList<Object> list=new ArrayList<>();for(Candidate c:choices)list.add(candidate(c,list.size()));
    s.suggesting=!s.composing&&!list.isEmpty();
    s.candidates=list;s.page=0;
    // An unrecognized spelling must remain visible and editable even when
    // the engine cannot offer a word for its current segment.
    call(s.processor,"aJ",s.composing||!list.isEmpty());
    refreshCandidateBars();
    ComposingLens.refresh();
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
    int start=s.page;
    int end=start+(int)Math.min((long)Math.max(0,count),(long)s.candidates.size()-start);
    List<Object> page=new ArrayList<>(s.candidates.subList(s.page,end));s.page=end;
    // Gboard retains previously loaded pages. Re-selecting each batch's first item
    // moves the viewport to that item and can cascade to the final page.
    sendCandidates(s,page,end<s.candidates.size(),start==0&&!page.isEmpty()?page.get(0):null);
  }
  private static void sendCandidates(Session s,List<Object> list,boolean more)throws Exception {
    sendCandidates(s,list,more,null);
  }
  private static void sendCandidates(Session s,List<Object> list,boolean more,Object selected)throws Exception {
    Object e=call(Class.forName("otf"),"a",list,selected,more,s.processor);
    call(get(s.processor,"A"),"a",e);
  }
  private static void dismiss(Session s)throws Exception {
    if(s.composing)return;
    // Also clear native next-word predictions (for example after a voice commit).
    call(s.processor,"at",true);
    s.dismissed=true;s.suggesting=false;s.context="";s.page=0;s.candidates=Collections.emptyList();
    call(s.processor,"aJ",false);sendCandidates(s,Collections.emptyList(),false);refreshCandidateBars();
  }
  private static final Map<View,BarControl> bars=new WeakHashMap<>();
  private static class BarControl {
    final java.lang.ref.WeakReference<android.widget.TextView> button;final int size;
    java.lang.ref.WeakReference<View> replaced=new java.lang.ref.WeakReference<>(null);
    BarControl(android.widget.TextView b,int s){button=new java.lang.ref.WeakReference<>(b);size=s;}
  }
  /** Keep native expansion handling intact; the extra close control owns its own touch area. */
  public static synchronized void bindCandidateBar(View bar){
    if(!(bar instanceof android.view.ViewGroup))return;
    BarControl previous=bars.get(bar);
    if(previous!=null){android.widget.TextView button=previous.button.get();
      if(button!=null&&button.getParent()!=null){placeDismiss(bar,button,previous.size);refreshCandidateBars();return;}
      if(button!=null&&button.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)button.getParent()).removeView(button);
      bars.remove(bar);
    }
    android.widget.TextView close=new android.widget.TextView(bar.getContext());
    close.setText("×");close.setTextSize(25);close.setGravity(android.view.Gravity.CENTER);
    close.setContentDescription("关闭联想候选，恢复工具栏");close.setFocusable(true);
    close.setVisibility(View.GONE);
    int size=(int)(48*bar.getResources().getDisplayMetrics().density+0.5f);
    android.util.TypedValue color=new android.util.TypedValue();bar.getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary,color,true);
    if(color.resourceId!=0)close.setTextColor(bar.getContext().getColor(color.resourceId));
    // Native candidate holders cast every child to SoftKeyView. Our control must
    // live outside that typed child list, including while the list shrinks.
    if(!placeDismiss(bar,close,size))return;
    bars.put(bar,new BarControl(close,size));
    close.setOnClickListener(v->{synchronized(EnhancedHook.class){try{if(active!=null)dismiss(active);}catch(Throwable error){Log.e("LocalChinese","Dismiss failed",error);}}});
    refreshCandidateBars();
  }
  public static synchronized int candidateWidth(View bar,int nativeWidth){
    BarControl control=bars.get(bar);TextView close=control==null?null:control.button.get();
    // Native Q() may still reserve the invisible arrow until its visibility listener runs.
    try{View more=control==null?null:control.replaced.get();if(more!=null&&(Boolean)get(bar,"b"))nativeWidth+=more.getWidth()>0?more.getWidth():more.getMeasuredWidth();}catch(Exception ignored){}
    return Math.max(1,nativeWidth-(close!=null&&close.getVisibility()==View.VISIBLE?control.size:0));
  }
  public static synchronized int candidateExpansionVisibility(View bar,int nativeVisibility){
    BarControl control=bars.get(bar);TextView close=control==null?null:control.button.get();
    return close!=null&&close.getVisibility()==View.VISIBLE&&LocalSettings.candidateControls(bar.getContext())==LocalSettings.CANDIDATE_REPLACE?View.INVISIBLE:nativeVisibility;
  }
  public static synchronized void layoutDismissButton(View bar){
    // Native candidate binding and visibility changes can settle after dispatch returns.
    if(!refreshingBars)refreshCandidateBars();
    BarControl control=bars.get(bar);TextView close=control==null?null:control.button.get();
    if(close==null||close.getVisibility()!=View.VISIBLE)return;
    int expansion=0;
    try{View more=(View)get(bar,"a");if(more!=null&&more.getVisibility()==View.VISIBLE)expansion=more.getWidth()>0?more.getWidth():more.getMeasuredWidth();}catch(Exception ignored){}
    int right=Math.max(0,bar.getWidth()-expansion),left=Math.max(0,right-control.size);
    placeDismiss(bar,close,control.size);
  }
  private static boolean placeDismiss(View bar,TextView close,int size){
    android.view.ViewParent ancestor=bar.getParent();
    while(ancestor instanceof View&&!(ancestor instanceof android.widget.FrameLayout))ancestor=ancestor.getParent();
    if(!(ancestor instanceof android.widget.FrameLayout))return false;
    android.widget.FrameLayout host=(android.widget.FrameLayout)ancestor;
    android.graphics.Rect bounds=new android.graphics.Rect(0,0,bar.getWidth(),bar.getHeight());
    host.offsetDescendantRectToMyCoords(bar,bounds);
    int expansion=0;try{View more=(View)get(bar,"a");if(more!=null&&more.getVisibility()==View.VISIBLE)expansion=more.getWidth();}catch(Exception ignored){}
    if(close.getParent()!=host){if(close.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)close.getParent()).removeView(close);host.addView(close,new android.widget.FrameLayout.LayoutParams(size,Math.max(0,bounds.height())));}
    android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)close.getLayoutParams();
    int left=Math.max(bounds.left,bounds.right-expansion-size);
    if(params.width!=size||params.height!=bounds.height()||params.leftMargin!=left||params.topMargin!=bounds.top){params.width=size;params.height=Math.max(0,bounds.height());params.leftMargin=left;params.topMargin=bounds.top;params.gravity=android.view.Gravity.TOP|android.view.Gravity.LEFT;close.setLayoutParams(params);}
    close.layout(left,bounds.top,left+size,bounds.bottom);return true;
  }
  private static java.lang.ref.WeakReference<TextView> dismissTouch=new java.lang.ref.WeakReference<>(null);
  static synchronized boolean candidateTouch(View root,android.view.MotionEvent event){
    if(!(root instanceof android.view.ViewGroup))return false;
    TextView target=dismissTouch.get();
    if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){target=null;
      for(BarControl control:bars.values()){TextView view=control.button.get();if(view==null||!view.isShown())continue;
        android.graphics.Rect r=new android.graphics.Rect(0,0,view.getWidth(),view.getHeight());
        try{((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(view,r);}catch(IllegalArgumentException outside){continue;}
        if(r.contains((int)event.getX(),(int)event.getY())){target=view;break;}}
      dismissTouch=new java.lang.ref.WeakReference<>(target);
    }
    if(target==null)return false;
    android.graphics.Rect r=new android.graphics.Rect(0,0,target.getWidth(),target.getHeight());
    try{((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(target,r);}catch(IllegalArgumentException outside){dismissTouch.clear();return false;}
    android.view.MotionEvent local=android.view.MotionEvent.obtain(event);local.offsetLocation(-r.left,-r.top);
    try{target.dispatchTouchEvent(local);}finally{local.recycle();}
    if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)dismissTouch.clear();return true;
  }
  private static boolean refreshingBars;
  private static TextView candidateLabel(View root,View except){if(root==except)return null;if(root instanceof TextView&&((TextView)root).length()>0)return (TextView)root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView label=candidateLabel(group.getChildAt(i),except);if(label!=null)return label;}}return null;}
  private static boolean fixedChineseBar(View bar){return bar.getClass().getName().equals("com.google.android.libraries.inputmethod.widgets.FixedSizeCandidatesHolderView");}
  static synchronized void refreshCandidateBars(){
    if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){
      new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{synchronized(EnhancedHook.class){refreshCandidateBars();}});return;
    }
    if(refreshingBars)return;refreshingBars=true;
    try{for(Map.Entry<View,BarControl> entry:bars.entrySet()){
      android.widget.TextView close=entry.getValue().button.get();if(close==null)continue;
      TextView label=candidateLabel(entry.getKey(),close);if(label!=null)close.setTextColor(label.getCurrentTextColor());
      boolean nativeSuggestions=false,nativeComposing=false;
      try{nativeSuggestions=(Integer)get(entry.getKey(),fixedChineseBar(entry.getKey())?"e":"f")>0;if(active!=null)nativeComposing=(Boolean)get(active.processor,"n");}catch(Exception ignored){}
      boolean predictions=active!=null&&!active.dismissed&&!active.composing&&!active.handwriting&&(active.suggesting||!nativeComposing&&nativeSuggestions)&&entry.getKey().isShown();
      int mode=LocalSettings.candidateControls(entry.getKey().getContext());
      boolean visible=predictions&&mode!=LocalSettings.CANDIDATE_EXPAND;
      BarControl control=entry.getValue();View replaced=control.replaced.get();
      boolean replace=predictions&&mode==LocalSettings.CANDIDATE_REPLACE;
      boolean arrowChanged=false;
      try{View more=(View)get(entry.getKey(),"a");
        if(replaced!=null&&(!replace||more!=replaced)){
          control.replaced=new java.lang.ref.WeakReference<>(null);
          if(replaced.getVisibility()==View.INVISIBLE){replaced.setVisibility(View.VISIBLE);arrowChanged=true;}
        }
        if(replace&&more!=null&&more.getVisibility()==View.VISIBLE){control.replaced=new java.lang.ref.WeakReference<>(more);more.setVisibility(View.INVISIBLE);arrowChanged=true;}
      }catch(Exception ignored){}
      boolean changed=(close.getVisibility()==View.VISIBLE)!=visible;
      close.setVisibility(visible?View.VISIBLE:View.GONE);
      if(changed||arrowChanged){try{call(entry.getKey(),fixedChineseBar(entry.getKey())?"H":"N");}catch(Exception ignored){}entry.getKey().requestLayout();}
      layoutDismissButton(entry.getKey());
    }}finally{refreshingBars=false;}
  }
  private static boolean isExpansionEvent(Object event){
    if(event==null)return false;
    try{int code=(Integer)call(event,"a");
      // Gboard 18.4.1 sends this toolbar expansion command after replacing its view definition.
      if(code==-10044)return true;Object source=get(event,"c");
      for(View bar:bars.keySet())try{Object more=get(bar,"a");if(more==null)continue;Object def=get(more,"e");if(def==null)continue;
        if(source==def)return true;Object[] actions=(Object[])get(def,"f");if(actions==null)continue;
        for(Object action:actions){if(action==null)continue;Object[] keys=(Object[])get(action,"d");if(keys!=null)for(Object key:keys)if(key!=null&&(Integer)get(key,"c")==code)return true;}
      }catch(Exception ignored){}
    }catch(Exception ignored){}return false;
  }
  private static void move(Session s,int position)throws Exception {
    RimeBridge.caret(s.id,Math.max(0,Math.min(s.raw.length(),position)));update(s);
  }
  static synchronized boolean chineseActive(){return active!=null;}
  static synchronized boolean composing(){return active!=null&&active.composing;}
  static synchronized boolean handwritingReady(){try{return active!=null&&RimeBridge.ready&&(active.composing||!(Boolean)get(active.processor,"n"));}catch(Exception ignored){return false;}}
  static synchronized Object token(){return active;}
  static synchronized String raw(){return active==null?"":active.raw;}
  static synchronized int caret(){return active==null?0:active.caret;}
  static synchronized void confirmSpace(Object session){try{if(active==session&&active.composing)chooseFirst(active);}catch(Throwable e){Log.e("LocalChinese","Space confirmation failed",e);}}
  static synchronized void selectCaret(int position,View source){try{if(active!=null&&active.composing){int before=active.caret;move(active,position);if(before!=active.caret)tick(source);}}catch(Throwable e){Log.e("LocalChinese","Caret selection failed",e);}}
  static synchronized void selectCaretWithTicks(int position,View source){try{if(active==null||!active.composing)return;Session s=active;int target=Math.max(0,Math.min(s.raw.length(),position));while(s.caret!=target){int before=s.caret;RimeBridge.caret(s.id,before+(target>before?1:-1));s.caret=Integer.parseInt(RimeBridge.state(s.id)[1]);if(s.caret==before)break;tick(source);}update(s);}catch(Throwable e){Log.e("LocalChinese","Space caret failed",e);}}
  private static void tick(View view){if(LocalSettings.ticks(view.getContext()))view.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE);}
  static synchronized void undoPress(String before,int caret,String letter)throws Exception{if(active==null||!active.composing||letter.length()!=1)return;String inserted=before.substring(0,Math.min(caret,before.length()))+letter+before.substring(Math.min(caret,before.length()));if(active.raw.equals(inserted)){RimeBridge.key(active.id,0xff08);update(active);}}
  static synchronized void beginInk()throws Exception{if(active==null||!RimeBridge.ready)throw new IllegalStateException("Chinese engine is loading");Session s=active;if(s.handwriting)return;finish(s);if(s.id==0)s.id=RimeBridge.create(s.privateMode);RimeBridge.clear(s.id);call(s.processor,"at",true);s.handwriting=true;s.composing=s.suggesting=false;s.dismissed=false;s.raw="";s.caret=0;s.page=0;s.candidates=Collections.emptyList();call(s.processor,"au","");call(s.processor,"aJ",false);sendCandidates(s,s.candidates,false);ComposingLens.hide();refreshCandidateBars();}
  static synchronized void inkCandidates(String[] words){try{if(active==null||!active.handwriting)return;Session s=active;ArrayList<Object> list=new ArrayList<>();HashSet<String> seen=new HashSet<>();for(String word:words)if(!word.isEmpty()&&seen.add(word))list.add(candidate(new Candidate(s,-3,word),list.size()));s.candidates=list;s.page=0;call(s.processor,"aJ",!list.isEmpty());publish(s,3);refreshCandidateBars();}catch(Throwable e){Log.e("LocalChinese","Handwriting candidates failed",e);}}
  static synchronized boolean confirmInk(Object token){try{if(active!=token||active==null||!active.handwriting||active.candidates.isEmpty())return false;chooseFirst(active);return true;}catch(Throwable e){Log.e("LocalChinese","Continuous handwriting commit failed",e);return false;}}
  static synchronized void cancelInk(){try{if(active!=null&&active.handwriting){active.handwriting=false;KeyboardGestures.clearInk();update(active);}}catch(Throwable e){Log.e("LocalChinese","Handwriting cancellation failed",e);}}
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
              selectCaret(nearest,view);ComposingLens.show(text);
            }catch(Throwable error){Log.e("LocalChinese","Caret editing failed",error);}
          }
        }
        return true;
      });
    }catch(Throwable error){Log.e("LocalChinese","Composing view binding failed",error);}
  }
}
