package local.gboard.enhanced;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import java.util.Map;
import java.util.WeakHashMap;

/** Scale native labels before measurement, preserving Gboard's own theme/height scaling. */
public final class FontSizes {
  private static final Map<TextView,Size> sizes=new WeakHashMap<>();
  private static final Map<View,Boolean> keys=new WeakHashMap<>();
  private static final class Size {float base,applied;Size(float value){base=applied=value;}}
  private static boolean candidate(View view){
    try{Object action=view.getClass().getMethod("h").invoke(view);if(action!=null){Object value=action.getClass().getField("e").get(action);return value!=null&&(value.getClass().getName().equals("oqg")||value.getClass().getName().equals("tbl"));}}catch(Exception ignored){}return false;
  }
  private static int role(View key){
    // Candidate keys are also measured in a detached pool to determine their widths.
    if(candidate(key))return 2;
    for(ViewParent parent=key.getParent();parent instanceof View;parent=parent.getParent()){
      for(Class<?> cls=parent.getClass();cls!=null;cls=cls.getSuperclass()){
        String name=cls.getName();if(name.contains("CandidatesHolderView"))return 2;
        if(name.equals("com.google.android.libraries.inputmethod.widgets.SoftKeyView")&&candidate((View)parent))return 2;
        if(name.equals("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView"))return 1;
      }
    }
    return 0;
  }
  public static void apply(View key){
    keys.put(key,Boolean.TRUE);int role=role(key);
    float ratio=role==0?1f:LocalSettings.fontPercent(key.getContext(),role==2)/100f;
    scale(key,ratio,false);
  }
  /** Remove our factor before native code rebuilds labels or changes its size ratio. */
  public static void restore(View key){scale(key,1f,true);}
  /** Native candidate labels use a custom canvas renderer, not TextView autosize.
   * Its minimum scale can spill a large glyph out of a narrow final column. */
  public static void fitCandidateText(TextView text,int widthSpec){
    if(role(text)!=2||View.MeasureSpec.getMode(widthSpec)!=View.MeasureSpec.EXACTLY)return;
    String value=text.getText().toString();int length=value.codePointCount(0,value.length());
    // Long words retain native truncation; one/two-character candidates stay legible.
    if(length==0||length>2||value.indexOf('\n')>=0)return;
    float available=View.MeasureSpec.getSize(widthSpec)-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()-2*text.getResources().getDisplayMetrics().density;
    if(available<=0)return;
    Size state=sizes.get(text);if(state==null)return;
    float requested=state.base*LocalSettings.fontPercent(text.getContext(),true)/100f;
    Paint paint=new Paint(text.getPaint());paint.setTextSize(requested);Rect glyph=new Rect();paint.getTextBounds(value,0,value.length(),glyph);
    float width=Math.max(paint.measureText(value),glyph.width());float target=width>available?requested*available/width:requested;
    state.applied=target;if(Math.abs(text.getTextSize()-target)>0.01f)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,target);
  }
  public static int clipCandidateText(View text,Canvas canvas){
    if(role(text)!=2)return -1;int save=canvas.save();canvas.clipRect(0,0,text.getWidth(),text.getHeight());return save;
  }
  public static void restoreCanvas(Canvas canvas,int save){if(save>=0)canvas.restoreToCount(save);}
  private static void scale(View view,float ratio,boolean restore){
    if(view instanceof TextView){TextView text=(TextView)view;float current=text.getTextSize();Size state=sizes.get(text);
      if(state==null){if(restore)return;state=new Size(current);sizes.put(text,state);}
      else if(Math.abs(current-state.applied)>0.01f)state.base=current;
      float target=state.base*ratio;state.applied=target;
      if(Math.abs(current-target)>0.01f)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,target);
    }
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)scale(group.getChildAt(i),ratio,restore);}
  }
  static void refresh(){
    if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(FontSizes::refresh);return;}
    for(View key:keys.keySet()){apply(key);key.requestLayout();key.invalidate();}
  }
}
