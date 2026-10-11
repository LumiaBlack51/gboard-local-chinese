package local.gboard.enhanced;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
/** Non-focusable magnification preserves the editor's input connection. */
final class ComposingLens {
  private static PopupWindow popup;private static TextView text;private static HorizontalScrollView scroll;
  static void show(View anchor){
    if(popup!=null){refresh();return;}if(anchor.getWindowToken()==null)return;
    float density=anchor.getResources().getDisplayMetrics().density;
    scroll=new HorizontalScrollView(anchor.getContext());scroll.setFillViewport(true);
    GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(38,40,44));bg.setCornerRadius(12*density);bg.setStroke((int)density,Color.GRAY);scroll.setBackground(bg);
    text=new TextView(anchor.getContext());text.setTextColor(Color.WHITE);text.setTextSize(32);text.setPadding((int)(16*density),(int)(12*density),(int)(16*density),(int)(12*density));text.setSingleLine(true);scroll.addView(text,new android.view.ViewGroup.LayoutParams(-2,-1));
    text.setOnTouchListener((v,event)->{
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
        android.text.Layout layout=text.getLayout();if(layout!=null){int visible=layout.getOffsetForHorizontal(0,event.getX()-text.getTotalPaddingLeft());CharSequence shown=text.getText();int raw=0;for(int i=0;i<Math.min(visible,shown.length());i++)if(shown.charAt(i)!='│')raw++;EnhancedHook.selectCaret(raw,text);}
      }
      return true;
    });
    int width=Math.min(anchor.getResources().getDisplayMetrics().widthPixels-(int)(24*density),(int)(480*density));int height=(int)(76*density);
    popup=new PopupWindow(scroll,width,height,false);popup.setBackgroundDrawable(bg);popup.setElevation(8*density);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);popup.setOutsideTouchable(false);popup.setTouchable(true);
    try{popup.showAsDropDown(anchor,0,-anchor.getHeight()-height);refresh();}catch(RuntimeException e){hide();}
  }
  static void refresh(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){new android.os.Handler(android.os.Looper.getMainLooper()).post(ComposingLens::refresh);return;}if(popup==null)return;String raw=EnhancedHook.raw();if(raw.isEmpty()){hide();return;}int caret=EnhancedHook.caret();text.setText(raw.substring(0,caret)+"│"+raw.substring(caret));text.post(()->{if(scroll!=null&&text!=null){int x=(int)text.getPaint().measureText(EnhancedHook.raw(),0,Math.min(EnhancedHook.caret(),EnhancedHook.raw().length()));scroll.scrollTo(Math.max(0,x-scroll.getWidth()/2),0);}});}
  static void hide(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){new android.os.Handler(android.os.Looper.getMainLooper()).post(ComposingLens::hide);return;}if(popup!=null)popup.dismiss();popup=null;text=null;scroll=null;}
  static boolean visible(){return popup!=null&&popup.isShowing();}
}
