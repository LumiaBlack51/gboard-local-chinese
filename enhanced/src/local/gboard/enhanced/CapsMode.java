package local.gboard.enhanced;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** One Shift tap enters native Caps Lock; the next Shift tap leaves it. */
public final class CapsMode {
  private static final Map<View,State> states=new WeakHashMap<>();
  private static class State {boolean locked,pressed;float x,y;}
  private CapsMode(){}
  public static void reset(){for(State state:states.values()){state.locked=false;state.pressed=false;}}
  public static void detached(View root){states.remove(root);}
  public static void touch(View root,MotionEvent event){
    if(event.getPointerCount()!=1)return;
    State state=states.get(root);if(state==null){state=new State();states.put(root,state);}
    int action=event.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){state.pressed=isShiftAt(root,event.getX(),event.getY());state.x=event.getX();state.y=event.getY();}
    else if(action==MotionEvent.ACTION_CANCEL)state.pressed=false;
    else if(action==MotionEvent.ACTION_UP&&state.pressed){
      state.pressed=false;
      float slop=android.view.ViewConfiguration.get(root.getContext()).getScaledTouchSlop();
      if(Math.abs(event.getX()-state.x)>slop||Math.abs(event.getY()-state.y)>slop)return;
      if(state.locked){state.locked=false;return;}
      state.locked=true;
      final float x=state.x,y=state.y;
      root.postDelayed(()->{
        if(!root.isAttachedToWindow()||!isShiftAt(root,x,y))return;
        long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);
        try{KeyboardGestures.nativeTouch(root,down);KeyboardGestures.nativeTouch(root,up);}
        finally{down.recycle();up.recycle();}
      },40);
    }
  }
  private static boolean isShiftAt(View root,float x,float y){return shiftAt(root,root,x,y);}
  private static boolean shiftAt(View node,View root,float x,float y){
    if(node.getVisibility()!=View.VISIBLE)return false;
    if(node.getClass().getName().endsWith("SoftKeyView")&&isShift(node)){
      Rect bounds=new Rect(0,0,node.getWidth(),node.getHeight());
      if(node!=root)((ViewGroup)root).offsetDescendantRectToMyCoords(node,bounds);
      return bounds.contains((int)x,(int)y);
    }
    if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;
      for(int i=0;i<group.getChildCount();i++)if(shiftAt(group.getChildAt(i),root,x,y))return true;}
    return false;
  }
  private static boolean isShift(View view){
    try{
      CharSequence description=view.getContentDescription();
      if(shiftLabel(description==null?"":description.toString()))return true;
      Field field=view.getClass().getDeclaredField("e");field.setAccessible(true);
      Object definition=field.get(view);
      if(definition!=null){Field label=definition.getClass().getDeclaredField("l");label.setAccessible(true);
        Object value=label.get(definition);if(value!=null&&shiftLabel(value.toString()))return true;}
    }catch(Throwable ignored){}
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
      for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);
        if(child instanceof TextView&&shiftLabel(((TextView)child).getText().toString()))return true;}}
    return false;
  }
  private static boolean shiftLabel(String label){String text=label.toLowerCase(java.util.Locale.ROOT);
    return text.contains("shift")||text.contains("uppercase")||text.contains("capital")||text.contains("大写")||text.contains("⇧")||text.contains("⬆");}
}
