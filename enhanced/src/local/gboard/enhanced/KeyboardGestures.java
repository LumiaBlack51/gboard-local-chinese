package local.gboard.enhanced;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;

/** Intercept Chinese drags before the native glide/cursor handlers; ordinary taps pass through. */
public final class KeyboardGestures {
  private static final Map<View,State> roots=new WeakHashMap<>();
  private static final Map<String,Field> fields=new HashMap<>();
  private static class Key {View view;int code;String text="";Rect bounds=new Rect();}
  private static final long NEXT_CHARACTER_MS=900,FADE_MS=1400;
  private static class Ghost {final List<List<PointF>> strokes;final long born=SystemClock.uptimeMillis();Ghost(List<List<PointF>> source){strokes=new ArrayList<>(source);}}
  private static class State {
    final WeakReference<View> root;final Drawable original;final InkDrawable drawing;
    Rect letters=new Rect(),deletion=new Rect();boolean bypass,space,spaceDragged,letter,captured,ink,board,blocked;float downX,downY,step;int startCaret;long downTime;int generation;
    final List<PointF> pending=new ArrayList<>();final Map<List<PointF>,Long> ended=new IdentityHashMap<>();final List<Ghost> ghosts=new ArrayList<>();
    Runnable next;boolean keepGhost;int recognized;long penUp;
    MotionEvent down;Runnable hold;Key key;String rawBefore="";Object session;
    boolean routed,routing;float routeX,routeY;
    final List<List<PointF>> strokes=new ArrayList<>();List<PointF> stroke;
    State(View v){root=new WeakReference<>(v);original=v.getForeground();drawing=new InkDrawable(this);}
  }
  private static Object get(Object o,String name)throws Exception{String id=o.getClass().getName()+"#"+name;Field known=fields.get(id);if(known!=null)return known.get(o);for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);fields.put(id,f);return f.get(o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
  private static Key key(View view){try{
    Object definition=get(view,"e");if(definition==null)return null;Object[] actions=(Object[])get(definition,"f");
    for(Object action:actions)for(Object event:(Object[])get(action,"d")){int code=(Integer)get(event,"c");Object data=get(event,"e");String text=data instanceof CharSequence?data.toString():"";
      if(code==62||code==67||text.length()==1&&Character.isLetter(text.charAt(0))&&text.charAt(0)<128){Key k=new Key();k.view=view;k.code=code;k.text=text;return k;}}
  }catch(Exception ignored){}return null;}
  private static void keys(View v,View root,List<Key> found){
    if(v.getVisibility()!=View.VISIBLE)return;
    Key k=key(v);if(k!=null){
      // MotionEvent coordinates are local to SoftKeyboardView. Global visible
      // rectangles are in the window, while getLocationOnScreen includes the
      // IME window's offset; subtracting the two moves hit targets to another row.
      Rect r=new Rect(0,0,v.getWidth(),v.getHeight());
      if(v!=root)((ViewGroup)root).offsetDescendantRectToMyCoords(v,r);
      if(r.intersect(0,0,root.getWidth(),root.getHeight())){k.bounds=r;found.add(k);}return;
    }
    if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)keys(group.getChildAt(i),root,found);}
  }
  private static List<Key> keys(View root,State s){List<Key> keys=new ArrayList<>();keys(root,root,keys);s.letters.setEmpty();s.deletion.setEmpty();int letters=0;for(Key k:keys){if(k.code==67)s.deletion.union(k.bounds);if(k.text.length()==1&&Character.isLetter(k.text.charAt(0))){s.letters.union(k.bounds);letters++;}}if(letters<20)s.letters.setEmpty();return keys;}
  public static void attach(View root){if(roots.containsKey(root))return;State state=new State(root);roots.put(root,state);NumericMode.attach(root);root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->render(v,state));root.post(()->render(root,state));}
  private static void render(View root,State state){if(root==null)return;if(!EnhancedHook.chineseActive()){state.board=false;if(root.getForeground()==state.drawing){root.setForeground(state.original);root.invalidate();}return;}keys(root,state);state.board=LocalSettings.handwriting(root.getContext())&&LocalSettings.board(root.getContext());
    state.board=state.board&&EnhancedHook.handwritingReady();root.setForeground((state.board||!state.strokes.isEmpty()||!state.ghosts.isEmpty())&&!state.letters.isEmpty()?state.drawing:state.original);root.invalidate();}
  public static void refresh(){if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(KeyboardGestures::refresh);return;}for(Map.Entry<View,State> entry:roots.entrySet())render(entry.getKey(),entry.getValue());}
  public static void reset(){if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(KeyboardGestures::reset);return;}for(Map.Entry<View,State> entry:roots.entrySet()){State s=entry.getValue();clear(s);stopHold(s);recycleDown(s);s.captured=s.space=s.letter=s.ink=s.blocked=s.routed=false;render(entry.getKey(),s);}ComposingLens.hide();}
  public static void detached(View root){State s=roots.remove(root);NumericMode.detached(root);CapsMode.detached(root);if(s!=null){clear(s);stopHold(s);recycleDown(s);root.setForeground(s.original);}}
  private static boolean nativeDispatch;
  static void nativeTouch(View root,MotionEvent event){State state=roots.get(root);if(state!=null)state.bypass=true;nativeDispatch=true;try{root.dispatchTouchEvent(event);}finally{nativeDispatch=false;if(state!=null)state.bypass=false;}}
  private static void recycleDown(State s){if(s.down!=null){s.down.recycle();s.down=null;}}
  private static void stopHold(State s){View root=s.root.get();if(root!=null&&s.hold!=null)root.removeCallbacks(s.hold);if(s.space&&s.key!=null)s.key.view.setPressed(false);s.hold=null;}
  private static void cancelNative(View root,State s,MotionEvent event){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);s.bypass=true;try{root.dispatchTouchEvent(cancel);}finally{s.bypass=false;cancel.recycle();}}
  /** Give the lower letter edge and the narrow gap above space to the bottom row.
   * Forward the whole gesture at a fixed offset so native tapping and handwriting agree. */
  private static Key lowerLetter(View root,State s,float x,float y){
    List<Key> found=keys(root,s);Key space=null;for(Key k:found)if(k.code==62)space=k;
    if(space==null||s.letters.isEmpty()||LocalSettings.board(root.getContext()))return null;
    float extension=Math.min(12*root.getResources().getDisplayMetrics().density,space.bounds.height()*.22f);
    if(y>=s.letters.bottom&&(x<space.bounds.left||x>=space.bounds.right))return null;
    for(Key k:found)if(k.text.length()==1&&"zxcvbnm".contains(k.text.toLowerCase(java.util.Locale.ROOT))&&k.bounds.bottom==s.letters.bottom
      &&x>=k.bounds.left&&x<k.bounds.right&&y>=k.bounds.exactCenterY()&&y<space.bounds.top+extension)return k;
    return null;
  }
  public static boolean touch(View root,MotionEvent event){
    try{State s=roots.get(root);if(nativeDispatch||s!=null&&s.bypass)return false;CapsMode.touch(root,event);if(s==null&&!EnhancedHook.chineseActive())return false;if(s==null){attach(root);s=roots.get(root);}final State state=s;int action=event.getActionMasked();
      if(!s.routing){
        if(action==MotionEvent.ACTION_DOWN){s.routed=false;if(EnhancedHook.chineseActive()){Key target=lowerLetter(root,s,event.getX(),event.getY());if(target!=null){s.routed=true;s.routeX=target.bounds.exactCenterX()-event.getX();s.routeY=target.bounds.exactCenterY()-event.getY();}}}
        if(s.routed){MotionEvent routed=MotionEvent.obtain(event);routed.offsetLocation(s.routeX,s.routeY);s.routing=true;
          try{root.dispatchTouchEvent(routed);}finally{s.routing=false;routed.recycle();if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)s.routed=false;}return true;}
      }
      if(!EnhancedHook.chineseActive()){if(s.captured){s.blocked=true;if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){s.captured=false;s.blocked=false;}return true;}render(root,s);return false;}
      if(action==MotionEvent.ACTION_DOWN){stopNext(s);s.pending.clear();s.pending.add(inkPoint(s,event.getX(),event.getY()));stopHold(s);recycleDown(s);s.down=MotionEvent.obtain(event);s.downTime=event.getDownTime();s.downX=event.getX();s.downY=event.getY();s.captured=s.ink=s.blocked=false;s.space=s.spaceDragged=s.letter=false;s.stroke=null;s.session=EnhancedHook.token();s.rawBefore=EnhancedHook.raw();s.startCaret=EnhancedHook.caret();
        List<Key> keys=keys(root,s);render(root,s);s.key=null;for(Key k:keys)if(k.bounds.contains((int)s.downX,(int)s.downY)){s.key=k;break;}
        float density=root.getResources().getDisplayMetrics().density;s.step=14*density;
        if(s.board&&s.letters.contains((int)s.downX,(int)s.downY)&&!s.deletion.contains((int)s.downX,(int)s.downY)){if(exitBounds(s).contains((int)s.downX,(int)s.downY)){LocalSettings.board(root.getContext(),false);s.captured=s.blocked=true;return true;}beginInk(root,s,event,false);return true;}
        s.space=s.key!=null&&s.key.code==62&&EnhancedHook.composing();
        s.letter=s.key!=null&&s.key.text.length()==1&&Character.isLetter(s.key.text.charAt(0))&&LocalSettings.handwriting(root.getContext())&&EnhancedHook.handwritingReady();
        if(s.letter&&!s.strokes.isEmpty()){beginInk(root,s,event,false);return true;}
        if(s.space){s.captured=true;s.key.view.setPressed(true);s.key.view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);return true;}
        else if(s.letter){s.hold=()->{if(state.down!=null&&state.letter&&!state.captured)try{beginInk(root,state,state.down,true);}catch(Exception e){android.util.Log.e("LocalChinese","Dot stroke failed",e);}};root.postDelayed(s.hold,220);}
        return false;
      }
      if(event.getPointerCount()>1||action==MotionEvent.ACTION_POINTER_DOWN){stopHold(s);if(s.captured){cancelNative(root,s,event);if(s.stroke!=null){s.strokes.remove(s.stroke);s.stroke=null;}s.blocked=true;render(root,s);return true;}s.space=s.letter=false;return false;}
      if(s.blocked){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){s.blocked=s.captured=false;recycleDown(s);}return true;}
      if(s.space&&action==MotionEvent.ACTION_MOVE){float dx=event.getX()-s.downX;if(!s.spaceDragged&&Math.abs(dx)>8*root.getResources().getDisplayMetrics().density){cancelNative(root,s,event);s.spaceDragged=true;}if(s.spaceDragged){int steps=(int)(dx/s.step),target=s.startCaret+steps;EnhancedHook.selectCaretWithTicks(target,root);if(target<0||target>EnhancedHook.raw().length()){s.startCaret=EnhancedHook.caret();s.downX=event.getX();}}return true;}
      if(s.space&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)){s.key.view.setPressed(false);if(action==MotionEvent.ACTION_UP&&!s.spaceDragged&&event.getEventTime()-s.downTime<ViewConfiguration.getLongPressTimeout()&&Math.abs(event.getY()-s.downY)<ViewConfiguration.get(root.getContext()).getScaledTouchSlop())EnhancedHook.confirmSpace(s.session);s.space=s.captured=false;recycleDown(s);return true;}
      if(s.letter&&action==MotionEvent.ACTION_MOVE&&!s.ink){for(int i=0;i<event.getHistorySize();i++)s.pending.add(inkPoint(s,event.getHistoricalX(i),event.getHistoricalY(i)));s.pending.add(inkPoint(s,event.getX(),event.getY()));float dx=event.getX()-s.downX,dy=event.getY()-s.downY;float threshold=Math.max(ViewConfiguration.get(root.getContext()).getScaledTouchSlop(),12*root.getResources().getDisplayMetrics().density);if(dx*dx+dy*dy>=threshold*threshold)beginInk(root,s,event,true);}
      if(s.ink&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)){add(s,event);root.invalidate();if(action==MotionEvent.ACTION_UP){s.ended.put(s.stroke,SystemClock.uptimeMillis());s.penUp=SystemClock.uptimeMillis();s.stroke=null;recognize(root,s);scheduleNext(root,s);s.letter=s.captured=s.ink=false;recycleDown(s);}return true;}
      if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){stopHold(s);boolean used=s.captured;if(action==MotionEvent.ACTION_CANCEL&&s.stroke!=null){s.strokes.remove(s.stroke);s.stroke=null;s.generation++;render(root,s);}s.space=s.letter=s.captured=s.ink=false;recycleDown(s);return used;}
      return s.captured;
    }catch(Throwable failure){android.util.Log.e("LocalChinese","Gesture failed",failure);return false;}
  }
  private static void beginInk(View root,State s,MotionEvent event,boolean cancel)throws Exception{
    stopHold(s);if(cancel)cancelNative(root,s,event);EnhancedHook.undoPress(s.rawBefore,s.startCaret,s.key==null?"":s.key.text);EnhancedHook.beginInk();s.captured=s.ink=true;s.space=false;s.generation++;s.session=EnhancedHook.token();
    if(s.strokes.size()>=64){clear(s);Toast.makeText(root.getContext(),"笔画过多，请重新写这个字",Toast.LENGTH_SHORT).show();}
    s.stroke=new ArrayList<>();s.strokes.add(s.stroke);s.stroke.addAll(s.pending);s.pending.clear();if(s.stroke.isEmpty())s.stroke.add(inkPoint(s,s.downX,s.downY));add(s,event);render(root,s);
  }
  private static void add(State s,MotionEvent event){if(s.stroke==null)return;for(int i=0;i<event.getHistorySize();i++)point(s,event.getHistoricalX(i),event.getHistoricalY(i));point(s,event.getX(),event.getY());}
  private static PointF inkPoint(State s,float x,float y){return new PointF(x-(s.routed?s.routeX:0),y-(s.routed?s.routeY:0));}
  private static void point(State s,float x,float y){PointF p=inkPoint(s,x,y);PointF last=s.stroke.get(s.stroke.size()-1);if(Math.abs(last.x-p.x)+Math.abs(last.y-p.y)<1)return;
    // Resample long strokes instead of dropping their entire tail.
    if(s.stroke.size()>=512){for(int i=s.stroke.size()-2;i>0;i-=2)s.stroke.remove(i);}
    s.stroke.add(p);
  }
  static int[] normalized(List<List<PointF>> strokes){float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=-Float.MAX_VALUE,bottom=-Float.MAX_VALUE;int count=0;for(List<PointF> stroke:strokes)for(PointF p:stroke){left=Math.min(left,p.x);top=Math.min(top,p.y);right=Math.max(right,p.x);bottom=Math.max(bottom,p.y);count++;}
    float scale=240/Math.max(24,Math.max(right-left,bottom-top));int[] result=new int[count*3];int offset=0,index=0;for(List<PointF> stroke:strokes){for(PointF p:stroke){result[offset++]=index;result[offset++]=Math.round(150+(p.x-(left+right)/2)*scale);result[offset++]=Math.round(150+(p.y-(top+bottom)/2)*scale);}index++;}return result;}
  private static void recognize(View root,State s){if(s.strokes.isEmpty())return;int generation=++s.generation;Object session=s.session;int[] points=normalized(s.strokes);HandwritingBridge.prepare(root.getContext());HandwritingBridge.recognize(points,words->{if(s.generation==generation&&EnhancedHook.token()==session&&!s.strokes.isEmpty()){s.recognized=generation;EnhancedHook.inkCandidates(words);if(words.length==0)Toast.makeText(root.getContext(),"没有识别到这个字，请重写",Toast.LENGTH_SHORT).show();}});}
  static boolean undo(){for(Map.Entry<View,State> entry:roots.entrySet()){State s=entry.getValue();if(!s.strokes.isEmpty()){stopNext(s);s.ended.remove(s.strokes.remove(s.strokes.size()-1));s.generation++;s.stroke=null;render(entry.getKey(),s);if(s.strokes.isEmpty())EnhancedHook.cancelInk();else recognize(entry.getKey(),s);return true;}}return false;}
  static void clearInk(){if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(KeyboardGestures::clearInk);return;}for(Map.Entry<View,State> entry:roots.entrySet()){clear(entry.getValue());render(entry.getKey(),entry.getValue());}}
  private static void stopNext(State s){View root=s.root.get();if(root!=null&&s.next!=null)root.removeCallbacks(s.next);s.next=null;}
  private static void scheduleNext(View root,State s){stopNext(s);if(!LocalSettings.continuous(root.getContext()))return;
    s.next=()->{if(s.stroke!=null||s.strokes.isEmpty()||EnhancedHook.token()!=s.session)return;
      if(s.recognized!=s.generation){root.postDelayed(s.next,100);return;}
      Ghost ghost=new Ghost(s.strokes);s.ghosts.add(ghost);s.keepGhost=true;
      boolean ok=EnhancedHook.confirmInk(s.session);s.keepGhost=false;if(!ok)s.ghosts.remove(ghost);
      while(s.ghosts.size()>4)s.ghosts.remove(0);render(root,s);
    };root.postDelayed(s.next,NEXT_CHARACTER_MS);
  }
  private static void clear(State s){stopNext(s);s.generation++;s.strokes.clear();s.ended.clear();s.pending.clear();s.stroke=null;if(!s.keepGhost)s.ghosts.clear();}
  private static Rect exitBounds(State s){View root=s.root.get();int size=root==null?0:(int)(56*root.getResources().getDisplayMetrics().density);return new Rect(s.letters.right-size,s.letters.top,s.letters.right,s.letters.top+size/2);}
  private static class InkDrawable extends Drawable {
    final State state;final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);InkDrawable(State s){state=s;}
    public void draw(Canvas canvas){View root=state.root.get();if(root==null)return;if(state.original!=null){state.original.setBounds(getBounds());state.original.draw(canvas);}float density=root.getResources().getDisplayMetrics().density;
      if(state.board){canvas.save();if(!state.deletion.isEmpty())canvas.clipOutRect(state.deletion);ink.setColor(Color.rgb(38,40,44));ink.setStyle(Paint.Style.FILL);canvas.drawRect(state.letters,ink);ink.setColor(0xffaaaaaa);ink.setStrokeWidth(density);canvas.drawLine(state.letters.exactCenterX(),state.letters.top,state.letters.exactCenterX(),state.letters.bottom,ink);canvas.drawLine(state.letters.left,state.letters.exactCenterY(),state.letters.right,state.letters.exactCenterY(),ink);ink.setTextSize(14*density);canvas.drawText("26键",exitBounds(state).left+6*density,exitBounds(state).bottom-5*density,ink);canvas.restore();}
      ink.setColor(0xff66b9ff);ink.setStrokeWidth(3*density);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);ink.setStyle(Paint.Style.STROKE);
      canvas.save();if(state.board&&!state.deletion.isEmpty())canvas.clipOutRect(state.deletion);
      long now=SystemClock.uptimeMillis();boolean continuous=LocalSettings.continuous(root.getContext()),animate=false;
      for(Iterator<Ghost> it=state.ghosts.iterator();it.hasNext();){Ghost ghost=it.next();float remaining=1-(now-ghost.born)/(float)FADE_MS;if(remaining<=0){it.remove();continue;}ink.setAlpha((int)(90*remaining));for(List<PointF> stroke:ghost.strokes)drawStroke(canvas,stroke);animate=true;}
      for(List<PointF> stroke:state.strokes){Long end=state.ended.get(stroke);int alpha=255;if(continuous&&end!=null){alpha=Math.max(70,255-(int)((now-end)*185/900));animate|=alpha>70;}ink.setAlpha(alpha);drawStroke(canvas,stroke);}
      ink.setAlpha(255);canvas.restore();if(animate)root.postInvalidateOnAnimation();
    }
    private void drawStroke(Canvas canvas,List<PointF> stroke){if(stroke.isEmpty())return;Path path=new Path();path.moveTo(stroke.get(0).x,stroke.get(0).y);for(int i=1;i<stroke.size();i++)path.lineTo(stroke.get(i).x,stroke.get(i).y);if(stroke.size()==1)canvas.drawPoint(stroke.get(0).x,stroke.get(0).y,ink);else canvas.drawPath(path,ink);
    }
    public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  }
}
