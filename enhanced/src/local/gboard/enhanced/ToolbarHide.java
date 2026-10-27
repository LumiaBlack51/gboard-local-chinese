package local.gboard.enhanced;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Hide action in the native toolbar; settings remain in the feature menu. */
public final class ToolbarHide {
  private static WeakReference<InputMethodService> current=new WeakReference<>(null);
  private static WeakReference<TextView> button=new WeakReference<>(null);
  private static WeakReference<View> displaced=new WeakReference<>(null),observed=new WeakReference<>(null);
  private static WeakReference<ViewGroup> mirroredPanel=new WeakReference<>(null);
  private static WeakReference<View> nativeVoice=new WeakReference<>(null);
  private static float originalVoiceX,originalVoiceY;
  private static Object mirroredAction;
  private static final android.view.ViewTreeObserver.OnGlobalLayoutListener layouts=ToolbarHide::refresh;
  private static boolean routing;
  private static final class HideButton extends TextView {
    private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path path=new android.graphics.Path();
    HideButton(Context context){super(context);}
    @Override protected void onDraw(android.graphics.Canvas canvas){
      float density=getResources().getDisplayMetrics().density,half=7*density,cx=getWidth()/2f,cy=getHeight()/2f;
      paint.setColor(getCurrentTextColor());paint.setStrokeWidth(2*density);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
      path.reset();path.moveTo(cx-half,cy-half/2);path.lineTo(cx,cy+half/2);path.lineTo(cx+half,cy-half/2);canvas.drawPath(path,paint);
    }
  }
  private ToolbarHide(){}
  static boolean inputVisible(){InputMethodService ime=current.get();return ime!=null&&ime.isInputViewShown();}
  public static void shown(Object service){
    if(!(service instanceof InputMethodService))return;
    InputMethodService ime=(InputMethodService)service;current=new WeakReference<>(ime);
    View decor=ime.getWindow().getWindow().getDecorView(),previous=observed.get();
    if(previous!=decor){
      if(previous!=null&&previous.getViewTreeObserver().isAlive())previous.getViewTreeObserver().removeOnGlobalLayoutListener(layouts);
      observed=new WeakReference<>(decor);decor.getViewTreeObserver().addOnGlobalLayoutListener(layouts);
    }
    for(int delay:new int[]{0,120,400,1000})decor.postDelayed(ToolbarHide::refresh,delay);
  }
  static void refresh(){
    EnhancedHook.refreshCandidateBars();
    InputMethodService ime=current.get();if(ime==null||ime.getWindow()==null)return;
    TextView control=button.get();
    if(!LocalSettings.toolbarHide(ime)){
      restoreSlot();
      if(control!=null&&control.getParent() instanceof ViewGroup)((ViewGroup)control.getParent()).removeView(control);
      return;
    }
    View decor=ime.getWindow().getWindow().getDecorView();ViewGroup row=findRow(decor);
    if(row==null){if(control!=null&&control.getVisibility()!=View.GONE)control.setVisibility(View.GONE);return;}
    if(control==null){
      control=new HideButton(ime);
      control.setGravity(Gravity.CENTER);control.setContentDescription("收起键盘");control.setFocusable(false);
      control.setOnClickListener(v->{InputMethodService active=current.get();if(active!=null)active.requestHideSelf(0);});
      control.setOnLongClickListener(v->{showOptions(v);return true;});button=new WeakReference<>(control);
    }
    int color=textColor(decor);control.setTextColor(color==0?Color.LTGRAY:color);
    if(isNativeBar(row)){placeNative(row,control);return;}
    restoreSlot();control.setVisibility(View.VISIBLE);
    if(control.getParent()!=row&&control.getParent() instanceof ViewGroup)((ViewGroup)control.getParent()).removeView(control);
    int size=(int)(48*ime.getResources().getDisplayMetrics().density+0.5f);
    place((LinearLayout)row,control,row.getChildCount(),size);
  }
  private static void restoreSlot(){
    clearMenuAction();
    View voice=nativeVoice.get();if(voice!=null){voice.setTranslationX(originalVoiceX);voice.setTranslationY(originalVoiceY);}nativeVoice=new WeakReference<>(null);
    View slot=displaced.get();if(slot!=null&&slot.getVisibility()==View.INVISIBLE)slot.setVisibility(View.VISIBLE);
    displaced=new WeakReference<>(null);
  }
  private static boolean isNativeBar(View view){return view.getClass().getName().equals("com.google.android.libraries.inputmethod.accesspoint.widget.AccessPointsBar");}
  private static int textColor(View view){
    if(view instanceof TextView&&view!=button.get()&&((TextView)view).getText().length()>0)return ((TextView)view).getCurrentTextColor();
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;int color=textColor(child);if(color!=0)return color;}}
    return 0;
  }
  private static void placeNative(ViewGroup row,TextView control){
    // AccessPointsBar casts its children. Keep its native slot measured and put
    // our action in its FrameLayout parent, never inside the native bar.
    if(!(row.getParent() instanceof FrameLayout))return;
    View slot=null;int count=row.getChildCount();
    for(int i=0;i<count;i++)if(contains(row.getChildAt(i),"settings","设置")){slot=row.getChildAt(i);break;}
    if(slot==null)for(int i=count-1;i>=0;i--){View candidate=row.getChildAt(i);if(candidate.getVisibility()==View.VISIBLE&&!contains(candidate,"one-handed","单手")){slot=candidate;break;}}
    if(slot==null||slot.getWidth()<=0||slot.getHeight()<=0)return;
    if(displaced.get()!=slot){restoreSlot();displaced=new WeakReference<>(slot);}
    ViewGroup barHost=(ViewGroup)row.getParent();
    if(!(barHost.getParent() instanceof ViewGroup))return;
    ViewGroup header=(ViewGroup)barHost.getParent();View voice=findVoice(header);
    if(voice==null||!(header.getParent() instanceof FrameLayout)||voice.getWidth()<=0)return;
    FrameLayout parent=(FrameLayout)header.getParent();
    if(nativeVoice.get()!=voice){View previous=nativeVoice.get();if(previous!=null){previous.setTranslationX(originalVoiceX);previous.setTranslationY(originalVoiceY);}nativeVoice=new WeakReference<>(voice);originalVoiceX=voice.getTranslationX();originalVoiceY=voice.getTranslationY();}
    Rect voiceBounds=layoutBounds(parent,voice),slotBounds=layoutBounds(parent,slot);
    // Native hit testing in Gboard 18.4.1 applies each view's inverse matrix,
    // so the translated microphone keeps its original native touch action.
    float dx=originalVoiceX+slotBounds.exactCenterX()-voiceBounds.exactCenterX(),dy=originalVoiceY+slotBounds.exactCenterY()-voiceBounds.exactCenterY();
    if(voice.getTranslationX()!=dx)voice.setTranslationX(dx);if(voice.getTranslationY()!=dy)voice.setTranslationY(dy);
    if(control.getParent()!=parent){if(control.getParent() instanceof ViewGroup)((ViewGroup)control.getParent()).removeView(control);parent.addView(control,new FrameLayout.LayoutParams(voiceBounds.width(),voiceBounds.height()));}
    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)control.getLayoutParams();
    int left=voiceBounds.left,top=voiceBounds.top;
    if(params.width!=voiceBounds.width()||params.height!=voiceBounds.height()||params.leftMargin!=left||params.topMargin!=top){params.width=voiceBounds.width();params.height=voiceBounds.height();params.leftMargin=left;params.topMargin=top;params.gravity=Gravity.TOP|Gravity.LEFT;control.setLayoutParams(params);}
    int visible=row.isShown()?View.VISIBLE:View.GONE;if(control.getVisibility()!=visible)control.setVisibility(visible);
    if(slot.getVisibility()!=View.INVISIBLE)slot.setVisibility(View.INVISIBLE);
    mirrorMenuAction(row.getRootView(),row,slot);
  }
  private static Rect layoutBounds(ViewGroup parent,View view){
    Rect bounds=new Rect(0,0,view.getWidth(),view.getHeight());
    for(View node=view;node!=parent;){bounds.offset(node.getLeft(),node.getTop());if(!(node.getParent() instanceof View))break;node=(View)node.getParent();bounds.offset(-node.getScrollX(),-node.getScrollY());}
    return bounds;
  }
  private static View findVoice(View node){
    CharSequence desc=node.getContentDescription();
    if(desc!=null&&node.getClass().getName().endsWith("SoftKeyView")){String text=desc.toString().toLowerCase(java.util.Locale.ROOT);if(text.contains("voice")||text.contains("语音"))return node;}
    if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++){View found=findVoice(group.getChildAt(i));if(found!=null)return found;}}
    return null;
  }
  private static ViewGroup nativePanel(View node){
    if(!node.isShown()||!(node instanceof ViewGroup))return null;
    ViewGroup group=(ViewGroup)node;
    if(node.getClass().getName().equals("com.google.android.libraries.inputmethod.accesspoint.widget.AccessPointsPanel"))return group;
    for(int i=0;i<group.getChildCount();i++){ViewGroup panel=nativePanel(group.getChildAt(i));if(panel!=null)return panel;}
    return null;
  }
  private static java.util.List<?> menuActions(ViewGroup panel)throws ReflectiveOperationException{
    java.lang.reflect.Field field=panel.getClass().getDeclaredField("e");field.setAccessible(true);return (java.util.List<?>)field.get(panel);
  }
  private static void clearMenuAction(){
    ViewGroup panel=mirroredPanel.get();Object action=mirroredAction;mirroredPanel=new WeakReference<>(null);mirroredAction=null;
    if(panel==null||action==null)return;
    try{java.util.List<Object> items=new java.util.ArrayList<>(menuActions(panel));if(items.remove(action))panel.getClass().getMethod("m",java.util.List.class).invoke(panel,items);}
    catch(ReflectiveOperationException|RuntimeException ignored){}
  }
  private static void mirrorMenuAction(View decor,ViewGroup row,View slot){
    ViewGroup panel=nativePanel(decor);if(panel==null)return;
    try{
      Object action=row.getClass().getMethod("k",int.class).invoke(row,row.indexOfChild(slot));if(action==null)return;
      if(mirroredPanel.get()!=panel||mirroredAction!=action)clearMenuAction();
      java.util.List<Object> items=new java.util.ArrayList<>(menuActions(panel));
      if(!items.contains(action)){items.add(0,action);panel.getClass().getMethod("m",java.util.List.class).invoke(panel,items);mirroredPanel=new WeakReference<>(panel);mirroredAction=action;}
    }catch(ReflectiveOperationException|RuntimeException ignored){}
  }
  /** Native SoftKeyboardView routes touch itself; forward the whole gesture. */
  static boolean touch(View root,MotionEvent event){
    TextView control=button.get();if(control==null||!control.isShown()||!(root instanceof ViewGroup)){routing=false;return false;}
    Rect bounds=new Rect(0,0,control.getWidth(),control.getHeight());
    try{((ViewGroup)root).offsetDescendantRectToMyCoords(control,bounds);}catch(IllegalArgumentException outside){return false;}
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN)routing=bounds.contains((int)event.getX(),(int)event.getY());
    if(!routing)return false;
    MotionEvent local=MotionEvent.obtain(event);local.offsetLocation(-bounds.left,-bounds.top);
    try{control.dispatchTouchEvent(local);}finally{local.recycle();}
    if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)routing=false;
    return true;
  }
  static void place(LinearLayout row,TextView control,int desired,int size){
    boolean alreadyInRow=control.getParent()==row;
    int position=Math.max(0,Math.min(desired,row.getChildCount()-(alreadyInRow?1:0)));
    if(alreadyInRow){if(row.indexOfChild(control)==position)return;row.removeView(control);}
    row.addView(control,position,new LinearLayout.LayoutParams(size,ViewGroup.LayoutParams.MATCH_PARENT));
  }
  private static void showOptions(View anchor){
    android.widget.PopupMenu menu=new android.widget.PopupMenu(anchor.getContext(),anchor);
    menu.getMenu().add("从工具栏移除");
    menu.setOnMenuItemClickListener(item->{LocalSettings.toolbarHide(anchor.getContext(),false);return true;});menu.show();
  }
  private static ViewGroup findRow(View node){
    if(!(node instanceof ViewGroup))return null;
    ViewGroup group=(ViewGroup)node;ViewGroup best=null;
    if(isNativeBar(node))return group;
    for(int i=0;i<group.getChildCount();i++){ViewGroup child=findRow(group.getChildAt(i));if(child!=null)best=child;}
    if(best!=null)return best;
    if(node instanceof LinearLayout&&((LinearLayout)node).getOrientation()==LinearLayout.HORIZONTAL&&group.getChildCount()>=2){
      int matches=0;
      if(contains(node,"voice","语音"))matches++;
      if(contains(node,"settings","设置")||contains(node,"preferences","偏好"))matches++;
      if(contains(node,"clipboard","剪贴板"))matches++;
      if(contains(node,"emoji","表情"))matches++;
      if(contains(node,"theme","主题"))matches++;
      if(contains(node,"gif","动图"))matches++;
      if(matches>=2)return (LinearLayout)node;
    }
    return null;
  }
  private static boolean contains(View node,String english,String chinese){
    CharSequence desc=node.getContentDescription();
    if(desc!=null){String label=desc.toString().toLowerCase(java.util.Locale.ROOT);if(label.contains(english)||label.contains(chinese))return true;}
    if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),english,chinese))return true;}
    return false;
  }
}
