package local.gboard.enhanced;

import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import java.util.Map;
import java.util.WeakHashMap;

/** Open number fields on the ordinary, switchable ?123 layout. */
public final class NumericMode {
  private static final Map<View,Boolean> keyboards=new WeakHashMap<>();
  private static boolean pending;
  private static int generation;
  private NumericMode(){}

  public static void inputStarted(EditorInfo info){
    generation++;
    pending=false;
    if(info==null)return;
    int kind=info.inputType&InputType.TYPE_MASK_CLASS;
    if(kind!=InputType.TYPE_CLASS_NUMBER)return;
    if((info.inputType&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_NUMBER_VARIATION_PASSWORD)return;
    // Only the IME's copy of EditorInfo changes. The application's InputConnection
    // still enforces its own field rules, including any signed/decimal restrictions.
    info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    pending=true;
    refresh();
  }
  public static void attach(View root){
    if(keyboards.containsKey(root))return;
    keyboards.put(root,true);
    root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedule(v,generation,0));
    schedule(root,generation,0);
  }
  public static void detached(View root){keyboards.remove(root);}
  public static void refresh(){for(View root:keyboards.keySet())schedule(root,generation,0);}
  private static void schedule(View root,int token,int attempt){
    if(!pending||root==null||attempt>8)return;
    root.postDelayed(()->{
      if(!pending||token!=generation||!root.isAttachedToWindow())return;
      View switchKey=findSwitch(root);
      if(switchKey==null||switchKey.getWidth()==0||switchKey.getHeight()==0){schedule(root,token,attempt+1);return;}
      pending=false;
      android.graphics.Rect bounds=new android.graphics.Rect(0,0,switchKey.getWidth(),switchKey.getHeight());
      if(switchKey!=root)((ViewGroup)root).offsetDescendantRectToMyCoords(switchKey,bounds);
      float x=bounds.exactCenterX(),y=bounds.exactCenterY();
      long now=android.os.SystemClock.uptimeMillis();
      MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
      MotionEvent up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);
      try{KeyboardGestures.nativeTouch(root,down);KeyboardGestures.nativeTouch(root,up);}
      finally{down.recycle();up.recycle();}
    },attempt==0?80:100);
  }
  private static View findSwitch(View view){
    if(view.getVisibility()!=View.VISIBLE||!view.isShown())return null;
    if(view instanceof TextView){String text=((TextView)view).getText().toString().trim();
      if(text.equals("?123")||text.equals("123?")){
        View key=view;
        while(key.getParent() instanceof View&&key.getParent()!=view.getRootView()){
          View parent=(View)key.getParent();
          if(parent.getClass().getName().endsWith("SoftKeyView"))return parent;
          if(parent==view)break;
          key=parent;
        }
        return view;
      }
    }
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
      for(int i=0;i<group.getChildCount();i++){View found=findSwitch(group.getChildAt(i));if(found!=null)return found;}}
    return null;
  }
}
