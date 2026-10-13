package local.gboard.enhanced;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Synthetic AVD editor and native Rime/Zinnia, with no personal input or recordings. */
public final class GestureCheckActivity extends Activity {
  private final Handler ui=new Handler(Looper.getMainLooper());private final JSONObject report=new JSONObject();private EditText editor;private TickTextView raw;private TextView status;private TestKeyboard keyboard;private FakeHmmPinyinQwertyDecodeProcessor processor;private final Gate gate=new Gate();
  private static Object get(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
  private static void put(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
  private static Object call(Object o,String n,Object...args)throws Exception{Class<?> cls=o instanceof Class?(Class<?>)o:o.getClass();for(Class<?> c=cls;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(o instanceof Class?null:o,args);}throw new NoSuchMethodException(n);}
  public static final class Gate {List<Object> last=Collections.emptyList();public void a(Object event)throws Exception{last=new ArrayList<>((List<Object>)get(event,"s"));}}
  public final class FakeHmmPinyinQwertyDecodeProcessor {public Context u=GestureCheckActivity.this;public Object A=gate;public boolean n;public void at(boolean clear){n=false;}public void au(CharSequence text){n=text.length()>0;raw.setText(text);}public void aJ(boolean enabled){}public void aK(CharSequence text,int cursor){editor.onCreateInputConnection(new EditorInfo()).commitText(text,cursor);}}
  public static final class FakeEnglishDecodeProcessor {}
  public static final class TestEvent {public int c;public Object e;TestEvent(int code,String text){c=code;e=text;}}
  public static final class TestAction {public Object[] d;TestAction(int code,String text){d=new Object[]{new TestEvent(code,text)};}}
  public static final class TestDefinition {public Object[] f;TestDefinition(int code,String text){f=new Object[]{new TestAction(code,text)};}}
  public final class TestKey extends TextView {public Object e;TestKey(int code,String text){super(GestureCheckActivity.this);e=new TestDefinition(code,text);setText(text);setGravity(Gravity.CENTER);setBackgroundColor(0xffcccccc);}}
  public final class TickTextView extends TextView {int ticks;TickTextView(){super(GestureCheckActivity.this);}public boolean performHapticFeedback(int constant){if(constant==HapticFeedbackConstants.TEXT_HANDLE_MOVE)ticks++;return true;}}
  public final class TestKeyboard extends FrameLayout {int ticks,downs,ups,cancels;TestKeyboard(){super(GestureCheckActivity.this);}public boolean performHapticFeedback(int constant){if(constant==HapticFeedbackConstants.TEXT_HANDLE_MOVE)ticks++;return true;}
    public boolean dispatchTouchEvent(MotionEvent event){if(KeyboardGestures.touch(this,event))return true;int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)downs++;if(action==MotionEvent.ACTION_CANCEL)cancels++;if(action==MotionEvent.ACTION_UP){ups++;if(EnhancedHook.chineseActive())for(int i=0;i<getChildCount();i++){TestKey k=(TestKey)getChildAt(i);if(event.getX()>=k.getLeft()&&event.getX()<k.getRight()&&event.getY()>=k.getTop()&&event.getY()<k.getBottom())try{TestEvent def=(TestEvent)((TestAction)((TestDefinition)k.e).f[0]).d[0];key(def.c,(String)def.e);}catch(Exception e){throw new RuntimeException(e);}}}return true;}}
  public static final class Composing {public View c;public TextView d;Composing(View overlay,TextView text){c=overlay;d=text;}}
  private Object event(int type)throws Exception{return call(Class.forName("otf"),"h",type,processor);}
  private boolean key(int code,String text)throws Exception{Object e=event(4);put(e,"i",call(Class.forName("oas"),"e",code,text));return EnhancedHook.dispatch(processor,e);}
  private void type(String text)throws Exception{for(char c:text.toCharArray())key(c,String.valueOf(c));}
  private void begin()throws Exception{processor.n=false;Object e=event(2);EditorInfo info=new EditorInfo();info.packageName="local.gboard.synthetic";info.inputType=1;put(e,"b",info);EnhancedHook.dispatch(processor,e);}
  private void check(String name,boolean pass)throws Exception{report.put(name,pass);if(!pass)throw new Exception(name);}
  private void touch(View view,int action,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);view.dispatchTouchEvent(e);e.recycle();}
  public void onCreate(Bundle state){super.onCreate(state);if(!Diagnostics.isDebug(this)){finish();return;}
    LocalSettings.prefs(this).edit().putBoolean("handwriting_continuous",false).putBoolean("handwriting",true).putBoolean("handwriting_board",false).putBoolean("caret_ticks",true).commit();
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);status=new TextView(this);status.setText("AVD pinyin gestures and offline handwriting");layout.addView(status);editor=new EditText(this);editor.setShowSoftInputOnFocus(false);layout.addView(editor);raw=new TickTextView();raw.setTextSize(16);raw.setMinHeight(64);layout.addView(raw);keyboard=new TestKeyboard();layout.addView(keyboard,new LinearLayout.LayoutParams(-1,400));setContentView(layout);processor=new FakeHmmPinyinQwertyDecodeProcessor();RimeBridge.prepare(this);HandwritingBridge.prepare(this);waitReady(SystemClock.elapsedRealtime()+600000);
  }
  private void waitReady(long deadline){if(RimeBridge.ready){ui.postDelayed(()->{try{populate(keyboard);ui.postDelayed(this::test,300);}catch(Throwable e){save(e);}},300);return;}if(SystemClock.elapsedRealtime()>deadline){save(new Exception("Rime timeout"));return;}ui.postDelayed(()->waitReady(deadline),300);}
  private void populate(ViewGroup root){int width=root.getWidth();String alphabet="qwertyuiopasdfghjklzxcvbnm";for(int i=0;i<26;i++){TestKey k=new TestKey(alphabet.charAt(i),alphabet.substring(i,i+1));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width/10-2,95);int row=i<10?0:i<19?1:2,col=i<10?i:i<19?i-10:i-19;p.leftMargin=col*width/10;p.topMargin=row*100;root.addView(k,p);}TestKey space=new TestKey(62," ");FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width/2,85);p.leftMargin=width/4;p.topMargin=310;root.addView(space,p);TestKey deletion=new TestKey(67,"");FrameLayout.LayoutParams d=new FrameLayout.LayoutParams(width/10-2,95);d.leftMargin=width*9/10;d.topMargin=200;root.addView(deletion,d);}
  private void test(){try{
    testLowerRow();
    begin();type("nihao");float x=keyboard.getWidth()/2f,y=350,step=14*getResources().getDisplayMetrics().density;int up=keyboard.ups;
    touch(keyboard,0,x,y);check("composing_space_down_is_captured",keyboard.downs==0);touch(keyboard,2,x-3.1f*step,y);check("space_moves_three_characters",EnhancedHook.caret()==2);check("one_haptic_per_crossed_character",keyboard.ticks==3);check("native_drag_cancelled",keyboard.cancels==1);touch(keyboard,2,x-3.2f*step,y);check("no_duplicate_haptic_within_character",keyboard.ticks==3);touch(keyboard,2,x-2.1f*step,y);check("space_reverse_moves_right",EnhancedHook.caret()==3&&keyboard.ticks==4);touch(keyboard,1,x-2.1f*step,y);check("drag_release_does_not_confirm",EnhancedHook.raw().equals("nihao")&&keyboard.ups==up);key('x',"x");check("typing_inserts_at_moved_caret",EnhancedHook.raw().equals("nihxao"));
    touch(keyboard,0,x,y);touch(keyboard,2,x-1000*step,y);int boundaryTicks=keyboard.ticks;check("caret_clamped_to_start",EnhancedHook.caret()==0);touch(keyboard,2,x-1001*step,y);check("boundary_does_not_vibrate",keyboard.ticks==boundaryTicks);touch(keyboard,2,x-999.9f*step,y);check("boundary_reverse_moves_without_dead_travel",EnhancedHook.caret()==1&&keyboard.ticks==boundaryTicks+1);touch(keyboard,1,x-999.9f*step,y);EnhancedHook.selectCaret(0,raw);
    ui.postDelayed(this::testLens,150);
  }catch(Throwable e){save(e);}}
  private void testLowerRow()throws Exception{
    float density=getResources().getDisplayMetrics().density;
    for(char letter:"cvbn".toCharArray())for(float y:new float[]{290,303,310+Math.min(10*density,85*.18f)}){
      begin();type("nihao");int index="zxcvbnm".indexOf(letter);float x=(index+.5f)*keyboard.getWidth()/10f;int before=editor.length();
      touch(keyboard,0,x,y);touch(keyboard,1,x,y);check("lower_edge_"+letter+"_"+(int)y,EnhancedHook.raw().equals("nihao"+letter)&&editor.length()==before);
    }
    begin();type("nihao");float x=keyboard.getWidth()/2f;touch(keyboard,0,x,370);touch(keyboard,1,x,370);check("space_center_still_confirms",EnhancedHook.raw().isEmpty());
    begin();
    keyboard.downs=keyboard.ups=keyboard.cancels=keyboard.ticks=0;
  }
  private void testLens(){try{float x=keyboard.getWidth()/2f,y=350,step=14*getResources().getDisplayMetrics().density;
    EnhancedHook.bindComposingView(new Composing(raw,raw));float pick=raw.getPaint().measureText("│nih")+raw.getTotalPaddingLeft()+1;touch(raw,0,pick,20);touch(raw,1,pick,20);report.put("tap_caret",EnhancedHook.caret());check("tap_selects_correct_pinyin_position",EnhancedHook.caret()==3);check("tap_opens_magnified_pinyin",ComposingLens.visible());
    touch(keyboard,0,x,y);touch(keyboard,2,x-step*1.1f,y);touch(keyboard,1,x-step*1.1f,y);check("space_fine_tunes_after_magnification",EnhancedHook.caret()==2&&ComposingLens.visible());EnhancedHook.selectCaret(4,raw);key(67,"");check("editing_repairs_pinyin",EnhancedHook.raw().equals("nihao"));EnhancedHook.selectCaret(5,raw);int committed=editor.length();touch(keyboard,0,x,y);touch(keyboard,1,x,y);check("short_space_tap_still_confirms",EnhancedHook.raw().isEmpty()&&editor.length()>committed);check("confirmation_closes_magnifier",!ComposingLens.visible());
    testNativeRoot();testWriting();
  }catch(Throwable e){save(e);}}
  private void testNativeRoot()throws Exception{
    android.view.ContextThemeWrapper theme=new android.view.ContextThemeWrapper(this,0x7f150402);theme.getTheme().applyStyle(0x7f150421,true);theme.getTheme().applyStyle(0x7f150439,true);
    ViewGroup root=(ViewGroup)Class.forName("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView").getConstructor(Context.class).newInstance(theme);((LinearLayout)keyboard.getParent()).addView(root,new LinearLayout.LayoutParams(-1,400));root.measure(View.MeasureSpec.makeMeasureSpec(keyboard.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));root.layout(0,keyboard.getBottom(),keyboard.getWidth(),keyboard.getBottom()+400);populate(root);root.measure(View.MeasureSpec.makeMeasureSpec(keyboard.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));root.layout(0,keyboard.getBottom(),keyboard.getWidth(),keyboard.getBottom()+400);
    begin();type("nihao");float x=root.getWidth()/2f,step=14*getResources().getDisplayMetrics().density;touch(root,0,x,350);touch(root,2,x-step*2.1f,350);touch(root,1,x-step*2.1f,350);check("original_soft_keyboard_routes_pinyin_drag",EnhancedHook.caret()==3&&EnhancedHook.raw().equals("nihao"));((ViewGroup)root.getParent()).removeView(root);begin();
  }
  private void testWriting()throws Exception{
    int width=keyboard.getWidth();int up=keyboard.ups;touch(keyboard,0,width*.18f,60);touch(keyboard,2,width*.75f,60);touch(keyboard,1,width*.75f,60);check("qwerty_stroke_does_not_emit_letters",EnhancedHook.raw().isEmpty()&&keyboard.ups==up);ui.postDelayed(()->waitInk(SystemClock.elapsedRealtime()+60000),100);
  }
  private void waitInk(long deadline){try{if(!gate.last.isEmpty()){List<String> words=new ArrayList<>();for(Object c:gate.last)words.add(get(get(c,"m"),"text").toString());report.put("horizontal_stroke_candidates",new JSONArray(words));check("offline_horizontal_stroke_recognized",words.contains("一"));Object pick=null;for(Object c:gate.last)if(get(get(c,"m"),"text").equals("一"))pick=c;Object e=event(10);put(e,"j",pick);put(e,"k",true);EnhancedHook.dispatch(processor,e);check("handwriting_candidate_commits_text",editor.getText().toString().endsWith("一"));
      LocalSettings.board(this,true);check("standalone_board_has_overlay",keyboard.getForeground()!=null);float deleteX=keyboard.getWidth()*.95f,deleteY=245;Bitmap board=Bitmap.createBitmap(keyboard.getWidth(),keyboard.getHeight(),Bitmap.Config.ARGB_8888);keyboard.draw(new Canvas(board));check("board_keeps_native_delete_visible",board.getPixel((int)deleteX,(int)deleteY)==0xffcccccc);board.recycle();touch(keyboard,0,keyboard.getWidth()*.2f,40);touch(keyboard,2,keyboard.getWidth()*.65f,40);touch(keyboard,1,keyboard.getWidth()*.65f,40);touch(keyboard,0,keyboard.getWidth()*.15f,170);touch(keyboard,2,keyboard.getWidth()*.8f,170);touch(keyboard,1,keyboard.getWidth()*.8f,170);int nativeUps=keyboard.ups;touch(keyboard,0,deleteX,deleteY);touch(keyboard,1,deleteX,deleteY);check("delete_undoes_last_handwriting_stroke",get(EnhancedHook.token(),"handwriting").equals(true)&&keyboard.ups==nativeUps+1);touch(keyboard,0,deleteX,deleteY);touch(keyboard,1,deleteX,deleteY);check("undo_all_restores_normal_candidates",!get(EnhancedHook.token(),"handwriting").equals(true));
      touch(keyboard,0,keyboard.getWidth()-15,15);touch(keyboard,1,keyboard.getWidth()-15,15);check("board_exit_button_restores_qwerty_without_letter",!LocalSettings.board(this)&&EnhancedHook.raw().isEmpty());begin();int beforeDown=keyboard.downs;touch(keyboard,0,keyboard.getWidth()*.15f,50);touch(keyboard,1,keyboard.getWidth()*.15f,50);check("ordinary_letter_tap_passes_to_native",keyboard.downs==beforeDown+1&&!EnhancedHook.raw().isEmpty());begin();type("nihao");touch(keyboard,0,keyboard.getWidth()/2f,350);touch(keyboard,3,keyboard.getWidth()/2f,350);check("cancel_does_not_confirm_pinyin",EnhancedHook.raw().equals("nihao"));
      begin();processor.n=true;int nativeBefore=keyboard.ups;touch(keyboard,0,keyboard.getWidth()*.18f,60);touch(keyboard,2,keyboard.getWidth()*.75f,60);touch(keyboard,1,keyboard.getWidth()*.75f,60);check("native_composition_not_interrupted_by_handwriting",processor.n&&EnhancedHook.raw().isEmpty()&&keyboard.ups==nativeBefore+1&&!get(EnhancedHook.token(),"handwriting").equals(true));processor.n=false;
      Object english=call(Class.forName("otf"),"h",2,new FakeEnglishDecodeProcessor());EnhancedHook.dispatch(new FakeEnglishDecodeProcessor(),english);check("english_clears_chinese_gesture_state",!EnhancedHook.chineseActive());int before=keyboard.ups;touch(keyboard,0,keyboard.getWidth()*.18f,60);touch(keyboard,2,keyboard.getWidth()*.75f,60);touch(keyboard,1,keyboard.getWidth()*.75f,60);check("english_drag_passes_to_native",keyboard.ups==before+1);testModels(0);return;}
    if(SystemClock.elapsedRealtime()>deadline)throw new Exception("Handwriting timeout");ui.postDelayed(()->waitInk(deadline),100);
  }catch(Throwable e){save(e);}}
  private void testModels(int index){String[] labels={"二","人","木","中"};int[][] points={
    {0,65,95,0,235,95,1,40,205,1,260,205},
    {0,160,45,0,140,100,0,90,190,0,45,245,1,145,100,1,180,185,1,250,245},
    {0,40,120,0,260,120,1,150,40,1,150,260,2,145,120,2,95,195,2,40,240,3,155,130,3,215,210,3,260,235},
    {0,75,75,0,75,215,1,75,80,1,235,80,1,235,215,2,75,215,2,235,215,3,150,30,3,150,270}};
    if(index==labels.length){try{testStrokeSampling();testContinuous();}catch(Throwable e){save(e);}return;}HandwritingBridge.recognize(points[index],words->{try{List<String> best=Arrays.asList(words).subList(0,Math.min(5,words.length));report.put("model_candidates_"+labels[index],new JSONArray(best));check("multi_stroke_"+labels[index]+"_in_top_five",best.contains(labels[index]));testModels(index+1);}catch(Throwable e){save(e);}});
  }
  private int inkPixel(int x,int y){Bitmap bitmap=Bitmap.createBitmap(keyboard.getWidth(),keyboard.getHeight(),Bitmap.Config.ARGB_8888);keyboard.draw(new Canvas(bitmap));int color=bitmap.getPixel(x,y);bitmap.recycle();return color;}
  private void testStrokeSampling()throws Exception{
    begin();LocalSettings.board(this,false);float x=keyboard.getWidth()*.45f,y=290;
    touch(keyboard,0,x,y);touch(keyboard,2,x+2,y-2);touch(keyboard,2,x+5,y-4);touch(keyboard,2,x+100,y-80);touch(keyboard,1,x+120,y-100);
    Field field=KeyboardGestures.class.getDeclaredField("roots");field.setAccessible(true);Object state=((Map<?,?>)field.get(null)).get(keyboard);
    List<List<PointF>> strokes=(List<List<PointF>>)get(state,"strokes");List<PointF> stroke=strokes.get(0);
    check("bottom_row_ink_keeps_physical_start",Math.abs(stroke.get(0).x-x)<1&&Math.abs(stroke.get(0).y-y)<1);
    check("pre_threshold_curve_points_preserved",stroke.size()>=5&&Math.abs(stroke.get(1).y-(y-2))<1);
    KeyboardGestures.reset();EnhancedHook.cancelInk();LocalSettings.board(this,true);begin();
    touch(keyboard,0,60,60);for(int i=1;i<=1200;i++)touch(keyboard,2,60+i*.5f,60+i*.2f);touch(keyboard,1,660,300);
    state=((Map<?,?>)field.get(null)).get(keyboard);strokes=(List<List<PointF>>)get(state,"strokes");stroke=strokes.get(0);PointF tail=stroke.get(stroke.size()-1);
    check("long_stroke_tail_is_not_truncated",Math.abs(tail.x-660)<1&&Math.abs(tail.y-300)<1);
    KeyboardGestures.reset();EnhancedHook.cancelInk();
  }
  private void testContinuous(){try{
    begin();LocalSettings.board(this,true);LocalSettings.continuous(this,true);editor.setText("");
    float width=keyboard.getWidth();touch(keyboard,0,width*.2f,100);touch(keyboard,2,width*.8f,100);touch(keyboard,1,width*.8f,100);
    final int fresh=inkPixel((int)(width*.5f),100);
    ui.postDelayed(()->{try{check("completed_strokes_fade",inkPixel((int)(width*.5f),100)!=fresh);}catch(Throwable e){save(e);}},500);
    ui.postDelayed(()->{try{check("continuous_auto_commits_first_character",editor.getText().toString().equals("一"));
    }catch(Throwable e){save(e);}},1300);
    ui.postDelayed(()->{try{
      check("old_character_remains_visible_while_next_begins",inkPixel((int)(width*.5f),100)!=0xff26282c);
      check("first_character_available_before_second",editor.getText().toString().equals("一"));
      touch(keyboard,0,width*.2f,140);touch(keyboard,2,width*.8f,140);touch(keyboard,1,width*.8f,140);
      ui.postDelayed(()->{try{check("continuous_separates_overlapping_characters",editor.getText().toString().equals("一一"));
        touch(keyboard,0,width*.2f,80);touch(keyboard,2,width*.8f,80);touch(keyboard,1,width*.8f,80);KeyboardGestures.reset();
        ui.postDelayed(()->{try{check("reset_cancels_pending_auto_commit",editor.getText().toString().equals("一一"));LocalSettings.continuous(this,false);save(null);}catch(Throwable e){save(e);}},1200);
      }catch(Throwable e){save(e);}},1300);
    }catch(Throwable e){save(e);}},1400);
  }catch(Throwable e){save(e);}}
  private void save(Throwable failure){KeyboardGestures.reset();try{if(failure!=null)report.put("error",android.util.Log.getStackTraceString(failure));report.put("passed",failure==null);try(java.io.FileOutputStream out=openFileOutput("gesture-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}status.setText(report.toString(2));}catch(Exception e){throw new RuntimeException(e);}}
}
