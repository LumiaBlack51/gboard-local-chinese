package local.gboard.enhanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Optional hide button in the same toolbar row as the native voice/settings actions. */
public final class ToolbarHide {
  private static WeakReference<InputMethodService> current=new WeakReference<>(null);
  private static WeakReference<TextView> button=new WeakReference<>(null);
  private ToolbarHide(){}
  public static void shown(Object service){
    if(!(service instanceof InputMethodService))return;
    InputMethodService ime=(InputMethodService)service;current=new WeakReference<>(ime);
    View decor=ime.getWindow().getWindow().getDecorView();
    for(int delay:new int[]{0,120,400,1000})decor.postDelayed(ToolbarHide::refresh,delay);
  }
  static void refresh(){
    InputMethodService ime=current.get();if(ime==null||ime.getWindow()==null)return;
    TextView old=button.get();
    if(!LocalSettings.toolbarHide(ime)){
      if(old!=null&&old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
      return;
    }
    View decor=ime.getWindow().getWindow().getDecorView();
    LinearLayout row=findRow(decor);
    if(row==null)return;
    if(old!=null&&old.getParent()!=row&&old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
    if(old==null){
      old=new TextView(ime);old.setText("⌄");old.setTextSize(26);old.setTextColor(Color.rgb(45,47,53));
      old.setGravity(Gravity.CENTER);old.setContentDescription("收起键盘");old.setFocusable(true);
      GradientDrawable background=new GradientDrawable();background.setColor(0x00ffffff);
      background.setCornerRadius(16*ime.getResources().getDisplayMetrics().density);old.setBackground(background);
      old.setOnClickListener(v->ime.requestHideSelf(0));
      old.setOnLongClickListener(v->{showOptions(v);return true;});
      button=new WeakReference<>(old);
    }
    int size=(int)(48*ime.getResources().getDisplayMetrics().density+0.5f);
    place(row,old,LocalSettings.toolbarHidePosition(ime),size);
  }
  static void place(LinearLayout row,TextView control,int desired,int size){
    boolean alreadyInRow=control.getParent()==row;
    int position=Math.max(0,Math.min(desired,row.getChildCount()-(alreadyInRow?1:0)));
    if(alreadyInRow){if(row.indexOfChild(control)==position)return;row.removeView(control);}
    row.addView(control,position,new LinearLayout.LayoutParams(size,ViewGroup.LayoutParams.MATCH_PARENT));
  }
  private static void showOptions(View anchor){
    android.widget.PopupMenu menu=new android.widget.PopupMenu(anchor.getContext(),anchor);
    menu.getMenu().add(0,0,0,"向左移动");menu.getMenu().add(0,1,1,"向右移动");menu.getMenu().add(0,2,2,"从工具栏移除");
    menu.setOnMenuItemClickListener(item->{int which=item.getItemId();Context context=anchor.getContext();
      if(which==2)LocalSettings.toolbarHide(context,false);
      else LocalSettings.toolbarHidePosition(context,LocalSettings.toolbarHidePosition(context)+(which==0?-1:1));
      refresh();return true;});
    menu.show();
  }
  private static LinearLayout findRow(View node){
    if(!(node instanceof ViewGroup))return null;
    ViewGroup group=(ViewGroup)node;LinearLayout best=null;
    for(int i=0;i<group.getChildCount();i++){
      LinearLayout child=findRow(group.getChildAt(i));if(child!=null)best=child;
    }
    if(best!=null)return best;
    if(node instanceof LinearLayout&&((LinearLayout)node).getOrientation()==LinearLayout.HORIZONTAL
      &&contains(node,"voice","语音")&&contains(node,"settings","设置"))return (LinearLayout)node;
    return null;
  }
  private static boolean contains(View node,String english,String chinese){
    CharSequence desc=node.getContentDescription();
    if(desc!=null){String label=desc.toString().toLowerCase(java.util.Locale.ROOT);
      if(label.contains(english)||label.contains(chinese))return true;}
    if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;
      for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),english,chinese))return true;}
    return false;
  }
}
