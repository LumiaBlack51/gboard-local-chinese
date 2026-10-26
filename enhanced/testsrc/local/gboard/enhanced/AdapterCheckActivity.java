package local.gboard.enhanced;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
/** AVD integration checks execute the shipped adapter, Rime and Gboard candidate objects. */
public final class AdapterCheckActivity extends Activity {
  private JSONObject report=new JSONObject();private TextView status;private EditText editor;private View candidateBar;private Gate gate=new Gate();private Handler ui=new Handler();private FakeHmmPinyinQwertyDecodeProcessor processor;private boolean chineseBar;
  private View close(){ViewGroup strip=(ViewGroup)candidateBar;for(int i=0;i<strip.getChildCount();i++){View v=strip.getChildAt(i);if("关闭联想候选，恢复工具栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;}throw new IllegalStateException("No cross");}
  static Object get(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
  static void put(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
  static Object call(Object target,String name,Object...args)throws Exception{for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}throw new NoSuchMethodException(name);}
  public final class Gate {List<Object> last=Collections.emptyList();Object selected;boolean more;public void a(Object event)throws Exception{last=new ArrayList<>((List<Object>)get(event,"s"));selected=get(event,"t");more=(Boolean)get(event,"r");}}
  public final class FakeHmmPinyinQwertyDecodeProcessor {
    public Context u=AdapterCheckActivity.this;public Object A=gate;public boolean n;
    public void at(boolean clear){n=false;}public void au(CharSequence raw){n=raw.length()>0;}
    public void aJ(boolean enabled){}public void aK(CharSequence text,int cursor){InputConnection input=editor.onCreateInputConnection(new EditorInfo());input.commitText(text,cursor);}
  }
  private Object event(int type)throws Exception{return call(Class.forName("otf"),"h",type,processor);}
  private boolean key(int code,String text)throws Exception{Object e=event(4);put(e,"i",call(Class.forName("oas"),"e",code,text));return EnhancedHook.dispatch(processor,e);}
  private void type(String raw)throws Exception{for(char c:raw.toCharArray())key(c,String.valueOf(c));}
  private void publish(int count)throws Exception{Object e=event(8);put(e,"m",count);if(!EnhancedHook.dispatch(processor,e))throw new Exception("Candidate request not handled");}
  private void begin()throws Exception{processor.n=false;Object e=event(2);EditorInfo info=new EditorInfo();info.packageName="local.gboard.synthetic";info.inputType=1;put(e,"b",info);EnhancedHook.dispatch(processor,e);}
  private void check(String name,boolean pass)throws Exception{report.put(name,pass);if(!pass)throw new Exception("Failed: "+name);}
  public void onCreate(Bundle state){super.onCreate(state);chineseBar=getIntent().getBooleanExtra("chinese_bar",false);if(!Diagnostics.isDebug(this)){finish();return;}
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);status=new TextView(this);status.setText("AVD adapter integration");layout.addView(status);editor=new EditText(this);editor.setShowSoftInputOnFocus(false);editor.setSingleLine();editor.setImeOptions(EditorInfo.IME_ACTION_SEARCH);layout.addView(editor);
    // Real Gboard XML and original candidate strip class, running without its incompatible ARM decoder.
    try{int id=0;for(int i=0x7f0e0000;i<0x7f0e1000;i++)try{android.util.TypedValue value=new android.util.TypedValue();getResources().getValue(i,value,true);if(value.string!=null&&value.string.toString().equals(chineseBar?"res/-Z2.xml":"res/1Fy.xml")){id=i;break;}}catch(Exception ignored){}
      if(id==0)throw new Exception("Candidate XML unavailable");android.view.ContextThemeWrapper themed=new android.view.ContextThemeWrapper(this,0x7f150402);themed.getTheme().applyStyle(0x7f150421,true);themed.getTheme().applyStyle(0x7f150439,true);if(chineseBar){android.content.res.XmlResourceParser xml=getResources().getLayout(id);while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(xml.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG&&xml.getName().endsWith(".FixedSizeCandidatesHolderView")){candidateBar=(View)Class.forName(xml.getName()).getConstructor(Context.class,android.util.AttributeSet.class).newInstance(themed,android.util.Xml.asAttributeSet(xml));break;}}xml.close();}else candidateBar=LayoutInflater.from(themed).inflate(id,layout,false);LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.addView(candidateBar,new LinearLayout.LayoutParams(0,144,1));layout.addView(row);report.put("original_candidate_class",candidateBar.getClass().getName());
    }catch(Throwable e){try{report.put("candidate_view_error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
    setContentView(layout);processor=new FakeHmmPinyinQwertyDecodeProcessor();RimeBridge.prepare(this);waitReady(SystemClock.elapsedRealtime()+600000);
  }
  private void waitReady(long deadline){if(RimeBridge.ready){ui.postDelayed(this::test,300);return;}if(SystemClock.elapsedRealtime()>deadline){save(new Exception("Rime timeout"));return;}ui.postDelayed(()->waitReady(deadline),300);}
  private void selectionProbe()throws Exception{
    report.put("probe_id",getIntent().getStringExtra("selection_probe_id"));
    String raw=getIntent().getStringExtra("selection_probe_raw");begin();type(raw);publish(100);
    report.put("probe_raw_before",EnhancedHook.raw());report.put("probe_candidate_count",gate.last.size());
    JSONArray before=new JSONArray();for(Object c:gate.last)before.put(String.valueOf(get(c,"a")));report.put("probe_candidates_before",before);
    if(!gate.last.isEmpty()){
      Object first=gate.last.get(0);String expected=getIntent().getStringExtra("selection_probe_text");if(expected!=null){first=null;for(Object c:gate.last)if(expected.equals(String.valueOf(get(c,"a")))){first=c;break;}if(first==null)throw new Exception("Requested candidate unavailable");}
      report.put("probe_first",String.valueOf(get(first,"a")));
      if(getIntent().getBooleanExtra("selection_probe_space",false))key(62," ");else{Object choice=event(10);put(choice,"j",first);put(choice,"k",true);EnhancedHook.dispatch(processor,choice);}
      report.put("probe_raw_after",EnhancedHook.raw());report.put("probe_editor_after",editor.getText().toString());report.put("probe_composing_after",EnhancedHook.composing());
      publish(4);report.put("probe_candidates_after",gate.last.size());
      JSONArray after=new JSONArray();for(Object c:gate.last)after.put(String.valueOf(get(c,"a")));report.put("probe_words_after",after);
    }
    save(null);
  }
  private void test(){try{
    if(getIntent().hasExtra("selection_probe_raw")){selectionProbe();return;}
    testTypoSelections();
    begin();type("nihao");check("enter_consumed",key(66,"\n"));check("enter_commits_literal",editor.getText().toString().equals("nihao"));check("next_enter_falls_through",!key(66,"\n"));
    begin();type("hello");Object enter=call(Class.forName("oas"),"e",66,"\n");check("front_enter_consumed",EnhancedHook.handleEnter(enter));check("front_enter_commits_literal",editor.getText().toString().equals("nihaohello"));check("front_next_enter_falls_through",!EnhancedHook.handleEnter(enter));
    begin();type("shi");publish(3);check("first_batch_selected",gate.selected!=null);Set<Object> first=new HashSet<>(gate.last);publish(6);check("additional_batch_does_not_select",gate.selected==null);check("additional_batch_no_duplicates",Collections.disjoint(first,gate.last));publish(Integer.MAX_VALUE);check("large_request_no_overflow",!gate.more);
    begin();type("yinsi");publish(3);
    if(candidateBar!=null){candidateBar.setVisibility(View.VISIBLE);EnhancedHook.bindCandidateBar(candidateBar);ViewGroup strip=(ViewGroup)candidateBar;View close=close();check("composing_has_no_dismiss",close.getVisibility()==View.GONE);check("composing_keeps_full_candidate_width",EnhancedHook.candidateWidth(candidateBar,600)==600);
      Object choice=event(10);put(choice,"j",gate.last.get(0));put(choice,"k",true);EnhancedHook.dispatch(processor,choice);report.put("editor_after_choice",editor.getText().toString());check("dismiss_button_visible",close.getVisibility()==View.VISIBLE);check("dismiss_is_candidate_strip_child",close.getParent()==candidateBar);close.performClick();check("dismiss_button_hidden",close.getVisibility()==View.GONE);check("dismiss_clears_candidates",gate.last.isEmpty()&&!gate.more);editor.setSelection(0,2);check("editor_selection_after_dismiss",editor.getSelectionStart()==0&&editor.getSelectionEnd()==2);
      begin();put(candidateBar,chineseBar?"e":"f",3);processor.n=true;EnhancedHook.bindCandidateBar(candidateBar);check("native_composing_has_no_dismiss",close.getVisibility()==View.GONE);processor.n=false;EnhancedHook.bindCandidateBar(candidateBar);check("native_post_commit_has_dismiss",close.getVisibility()==View.VISIBLE);close.performClick();check("dismissed_native_cache_cannot_reopen",close.getVisibility()==View.GONE);put(candidateBar,chineseBar?"e":"f",0);
    }
    else check("original_candidate_view_loaded",false);
    testFrame();
  }catch(Throwable e){save(e);}}
  private void testTypoSelections()throws Exception{
    for(boolean space:new boolean[]{false,true}){
      begin();editor.setText("");type("ranhiuwohui");publish(100);
      check("typo_corrected_candidate_"+space,!gate.last.isEmpty()&&"然后我会".equals(String.valueOf(get(gate.last.get(0),"a"))));
      if(space)key(62," ");else{Object e=event(10);put(e,"j",gate.last.get(0));put(e,"k",true);EnhancedHook.dispatch(processor,e);}
      check("typo_choice_committed_"+space,"然后我会".equals(editor.getText().toString()));
      publish(3);check("typo_choice_keeps_predictions_"+space,!gate.last.isEmpty());
      Object next=gate.last.get(0);String predicted=String.valueOf(get(next,"a"));Object e=event(10);put(e,"j",next);put(e,"k",true);EnhancedHook.dispatch(processor,e);
      check("following_choice_committed_"+space,("然后我会"+predicted).equals(editor.getText().toString()));
    }
    begin();editor.setText("");type("nihaoma");publish(100);Object prefix=null;for(Object c:gate.last)if("你好".equals(String.valueOf(get(c,"a")))){prefix=c;break;}
    check("partial_candidate_available",prefix!=null);Object e=event(10);put(e,"j",prefix);put(e,"k",true);EnhancedHook.dispatch(processor,e);
    check("partial_candidate_committed","你好".equals(editor.getText().toString()));check("partial_input_retained","ma".equals(EnhancedHook.raw()));publish(3);check("partial_candidates_retained",!gate.last.isEmpty());
    key(67,"");check("partial_input_editable","m".equals(EnhancedHook.raw()));
    begin();editor.setText("");type("nihaovvvvvv");publish(100);Object old=gate.last.get(0);e=event(10);put(e,"j",old);put(e,"k",true);EnhancedHook.dispatch(processor,e);
    check("invalid_tail_prefix_committed","你好".equals(editor.getText().toString()));check("invalid_tail_retained","vvvvvv".equals(EnhancedHook.raw()));publish(3);
    check("invalid_tail_replaces_previous_candidates",gate.last.size()==1&&"vvvvvv".equals(String.valueOf(get(gate.last.get(0),"a"))));
    Object literal=gate.last.get(0);e=event(10);put(e,"j",old);put(e,"k",true);EnhancedHook.dispatch(processor,e);check("stale_candidate_ignored","你好".equals(editor.getText().toString())&&"vvvvvv".equals(EnhancedHook.raw()));
    e=event(10);put(e,"j",literal);put(e,"k",true);EnhancedHook.dispatch(processor,e);check("invalid_tail_literal_committed","你好vvvvvv".equals(editor.getText().toString())&&!EnhancedHook.composing());
    begin();editor.setText("");
  }
  private void testFrame()throws Exception{
    LocalSettings.prefs(this).edit().remove("candidate_controls").commit();check("default_control_replaces_expand",LocalSettings.candidateControls(this)==LocalSettings.CANDIDATE_REPLACE);LocalSettings.candidateControls(this,LocalSettings.CANDIDATE_BOTH);
    ViewGroup old=(ViewGroup)candidateBar.getParent();old.removeView(candidateBar);FrameLayout frame=new FrameLayout(this);frame.addView(candidateBar,new FrameLayout.LayoutParams(-1,-1));((LinearLayout)editor.getParent()).addView(frame,new LinearLayout.LayoutParams(-1,144));
    View expansion=(View)Class.forName("com.google.android.libraries.inputmethod.widgets.SoftKeyView").getConstructor(Context.class).newInstance(this);int size=(int)(48*getResources().getDisplayMetrics().density+0.5f);FrameLayout.LayoutParams arrowParams=new FrameLayout.LayoutParams(size,-1,Gravity.RIGHT);frame.addView(expansion,arrowParams);call(candidateBar,"K",expansion);final int[] expansions={0};expansion.setOnClickListener(v->expansions[0]++);
    begin();type("yinsi");publish(3);Object choice=event(10);put(choice,"j",gate.last.get(0));put(choice,"k",true);EnhancedHook.dispatch(processor,choice);EnhancedHook.bindCandidateBar(candidateBar);
    publish(3);
    ui.postDelayed(()->{try{call(candidateBar,"l");call(candidateBar,"k",gate.last);candidateBar.measure(View.MeasureSpec.makeMeasureSpec(frame.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(frame.getHeight(),View.MeasureSpec.EXACTLY));candidateBar.layout(0,0,frame.getWidth(),frame.getHeight());ViewGroup strip=(ViewGroup)candidateBar;View close=close();check("frame_dismiss_visible",close.getVisibility()==View.VISIBLE);check("original_strip_contains_predictions",(Integer)get(candidateBar,chineseBar?"e":"f")>0);check("expansion_identity_preserved",get(candidateBar,"a")==expansion);check("dismiss_and_expansion_do_not_overlap",close.getRight()<=expansion.getLeft());check("dismiss_reserves_own_candidate_width",EnhancedHook.candidateWidth(candidateBar,600)==600-size);
      if(!chineseBar){int nativeWidth=Math.max((Integer)get(candidateBar,"m"),candidateBar.getWidth())-expansion.getWidth();check("native_width_calculation_reserves_dismiss",(Integer)call(candidateBar,"Q")==Math.max(1,nativeWidth-size));}
      check("expansion_command_falls_through",!key(-10044,""));check("expansion_reset_preserves_predictions",EnhancedHook.dispatch(processor,event(15)));check("expansion_command_keeps_cross",close.getVisibility()==View.VISIBLE);
      int count=(Integer)get(candidateBar,chineseBar?"e":"f");for(int i=0;i<count;i++){View item=chineseBar?(View)((Object[])get(candidateBar,"f"))[i]:(View)call(candidateBar,"B",i);if(item!=null)check("candidate_"+i+"_does_not_overlap_dismiss",item.getRight()<=close.getLeft());}
      check("parent_padding_unchanged",frame.getPaddingRight()==0);expansion.performClick();check("native_expansion_remains_clickable",expansions[0]==1);
      LocalSettings.candidateControls(this,LocalSettings.CANDIDATE_REPLACE);check("replace_mode_hides_expansion",expansion.getVisibility()==View.INVISIBLE&&close.getVisibility()==View.VISIBLE);
      LocalSettings.candidateControls(this,LocalSettings.CANDIDATE_EXPAND);check("expand_only_has_no_cross",expansion.getVisibility()==View.VISIBLE&&close.getVisibility()==View.GONE);
      LocalSettings.candidateControls(this,LocalSettings.CANDIDATE_BOTH);check("both_controls_restored",expansion.getVisibility()==View.VISIBLE&&close.getVisibility()==View.VISIBLE);
      long now=SystemClock.uptimeMillis();float x=(close.getLeft()+close.getRight())/2f,y=frame.getHeight()/2f;MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);frame.dispatchTouchEvent(down);frame.dispatchTouchEvent(up);down.recycle();up.recycle();
      // Android posts the click from ACTION_UP; assert after that UI work has executed.
      ui.postDelayed(()->{try{check("real_touch_closes_predictions",close.getVisibility()==View.GONE);EnhancedHook.voiceCommitted("隐私");check("voice_post_commit_has_dismiss",close.getVisibility()==View.VISIBLE);type("shi");check("new_composition_hides_dismiss",close.getVisibility()==View.GONE);check("new_composition_restores_candidate_width",EnhancedHook.candidateWidth(candidateBar,600)==600);check("composition_keeps_expansion",expansion.getVisibility()==View.VISIBLE&&get(candidateBar,"a")==expansion);testPaging();}catch(Throwable e){save(e);}},100);
    }catch(Throwable e){save(e);}},400);
  }
  private void testPaging()throws Exception{
    Object metrics=call(Class.forName("tfj"),"F");((android.graphics.Rect)get(metrics,"b")).set(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);((android.graphics.Rect)get(metrics,"c")).setEmpty();
    call(call(Class.forName("qew"),"b"),"i",metrics);
    begin();type("shi");publish(Integer.MAX_VALUE);report.put("paging_candidate_count",gate.last.size());
    int id=0;for(int i=0x7f0e0000;i<0x7f0e1000;i++)try{android.util.TypedValue value=new android.util.TypedValue();getResources().getValue(i,value,true);if(value.string!=null&&value.string.toString().equals("res/PEE.xml")){id=i;break;}}catch(Exception ignored){}
    android.view.ContextThemeWrapper theme=new android.view.ContextThemeWrapper(this,0x7f150402);theme.getTheme().applyStyle(0x7f150421,true);theme.getTheme().applyStyle(0x7f150439,true);
    final View pager=LayoutInflater.from(theme).inflate(id,null);
    Class<?> listener=Class.forName("phc");Object callbacks=java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(p,m,args)->null);call(pager,"D",callbacks);
    ((LinearLayout)editor.getParent()).addView(pager,new LinearLayout.LayoutParams(-1,600));pager.setVisibility(View.VISIBLE);call(pager,"k",gate.last);
    ui.postDelayed(()->{try{int before=(Integer)get(get(pager,"n"),"a");report.put("first_page_item_count",get(get(pager,"n"),"d"));report.put("view_width",pager.getWidth());report.put("max_page_items",call(pager,"z"));check("expanded_initial_page_zero",before==0);call(pager,"G");ui.postDelayed(()->{try{int after=(Integer)get(get(pager,"n"),"a");report.put("after_down_page",after);check("expanded_down_one_page",after==before+1);call(pager,"H");ui.postDelayed(()->{try{check("expanded_up_returns_first",(Integer)get(get(pager,"n"),"a")==before);save(null);}catch(Throwable e){save(e);}},400);}catch(Throwable e){save(e);}},500);}catch(Throwable e){save(e);}},500);
  }
  private void save(Throwable error){try{if(error!=null)report.put("error",android.util.Log.getStackTraceString(error));report.put("passed",error==null);try(java.io.FileOutputStream out=openFileOutput(chineseBar?"chinese-bar-report.json":"adapter-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}status.setText(report.toString(2));}catch(Exception e){throw new RuntimeException(e);}}
}
