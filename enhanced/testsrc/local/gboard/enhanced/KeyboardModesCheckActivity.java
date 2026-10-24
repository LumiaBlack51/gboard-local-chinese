package local.gboard.enhanced;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** Debug-only AVD checks for numeric mode and persistent Shift behavior. */
public final class KeyboardModesCheckActivity extends Activity {
  private final JSONObject results=new JSONObject();
  private Keyboard keyboard;private TextView status;private TextView switcher;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private static final class FakeSoftKeyView extends FrameLayout {
    FakeSoftKeyView(Activity activity){super(activity);setContentDescription("Shift");}
  }
  private final class Keyboard extends FrameLayout {
    boolean symbols,caps,once;int shiftClicks,switchClicks;String letters="";
    Keyboard(){super(KeyboardModesCheckActivity.this);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
      if(KeyboardGestures.touch(this,event))return true;
      if(event.getActionMasked()==MotionEvent.ACTION_UP){
        View key=null;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);
          if(event.getX()>=child.getLeft()&&event.getX()<child.getRight()&&event.getY()>=child.getTop()&&event.getY()<child.getBottom()){key=child;break;}}
        if(key==switcher){symbols=!symbols;switchClicks++;switcher.setText(symbols?"ABC":"?123");}
        else if(key instanceof FakeSoftKeyView){shiftClicks++;if(caps)caps=false;else if(once){caps=true;once=false;}else once=true;}
        else if(key!=null){String value=((TextView)key).getText().toString();
          if(value.equals("a")){letters+=caps||once?"A":"a";once=false;}
          else if(value.equals("+"))letters+="+";}
      }
      return true;
    }
  }
  private void check(String name,boolean okay)throws Exception{results.put(name,okay);if(!okay)throw new Exception(name);}
  private void tap(float x,float y){long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0),up=MotionEvent.obtain(time,time+30,1,x,y,0);
    try{keyboard.dispatchTouchEvent(down);keyboard.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}}
  @Override public void onCreate(Bundle state){super.onCreate(state);if(!Diagnostics.isDebug(this)){finish();return;}
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);status=new TextView(this);status.setText("Testing keyboard modes");layout.addView(status);
    keyboard=new Keyboard();layout.addView(keyboard,new LinearLayout.LayoutParams(-1,300));
    switcher=new TextView(this);switcher.setText("?123");switcher.setTextSize(25);switcher.setBackgroundColor(0xffdddddd);
    keyboard.addView(switcher,params(0,0,160,100));
    FakeSoftKeyView shift=new FakeSoftKeyView(this);shift.setBackgroundColor(0xffdddddd);keyboard.addView(shift,params(180,0,160,100));
    TextView letter=new TextView(this);letter.setText("a");letter.setTextSize(25);letter.setBackgroundColor(Color.WHITE);keyboard.addView(letter,params(360,0,160,100));
    TextView plus=new TextView(this);plus.setText("+");plus.setTextSize(25);plus.setBackgroundColor(Color.WHITE);keyboard.addView(plus,params(540,0,160,100));
    setContentView(layout);
    try{
      LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);
      TextView voice=new TextView(this);voice.setContentDescription("Voice typing");toolbar.addView(voice);
      TextView settings=new TextView(this);settings.setContentDescription("Settings");toolbar.addView(settings);
      java.lang.reflect.Method finder=ToolbarHide.class.getDeclaredMethod("findRow",View.class);finder.setAccessible(true);
      check("toolbar_row_found",finder.invoke(null,toolbar)==toolbar);
      TextView hideButton=new TextView(this);ToolbarHide.place(toolbar,hideButton,1,48);
      check("hide_button_inserted_between_actions",toolbar.indexOfChild(hideButton)==1);
      ToolbarHide.place(toolbar,hideButton,2,48);
      check("hide_button_can_move_right",toolbar.indexOfChild(hideButton)==2);
      toolbar.removeView(hideButton);
      LocalSettings.toolbarHidePosition(this,2);check("hide_position_saved",LocalSettings.toolbarHidePosition(this)==2);
      LocalSettings.toolbarHide(this,false);check("hide_button_can_be_removed",!LocalSettings.toolbarHide(this));
      LocalSettings.toolbarHide(this,true);
    }catch(Exception error){save(error);return;}
    EditorInfo info=new EditorInfo();info.inputType=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL;
    NumericMode.inputStarted(info);KeyboardGestures.attach(keyboard);
    try{check("numeric_editor_uses_text_keyboard",(info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT);}catch(Exception e){save(e);return;}
    handler.postDelayed(()->{try{
      check("numeric_opens_switchable_symbols",keyboard.symbols&&keyboard.switchClicks==1);
      tap(580,50);check("plus_key_available",keyboard.letters.equals("+"));
      tap(80,50);check("can_switch_back_to_letters",!keyboard.symbols&&keyboard.switchClicks==2);
      tap(240,50);
      handler.postDelayed(()->{try{
        check("single_shift_enables_caps_lock",keyboard.caps&&keyboard.shiftClicks==2);
        tap(420,50);tap(420,50);check("caps_persists_across_letters",keyboard.letters.equals("+AA"));
        tap(240,50);tap(420,50);check("second_shift_disables_caps",keyboard.letters.equals("+AAa"));
        EditorInfo ordinary=new EditorInfo();ordinary.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        NumericMode.inputStarted(ordinary);check("ordinary_editor_keeps_its_input_type",ordinary.inputType==(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        EditorInfo pin=new EditorInfo();pin.inputType=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        NumericMode.inputStarted(pin);check("password_number_editor_stays_secure",pin.inputType==(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        save(null);
      }catch(Exception error){save(error);}},180);
    }catch(Exception error){save(error);}},500);
  }
  private FrameLayout.LayoutParams params(int x,int y,int width,int height){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;return p;}
  private void save(Exception error){try{results.put("passed",error==null);if(error!=null)results.put("error",android.util.Log.getStackTraceString(error));
      try(FileOutputStream out=openFileOutput("keyboard-modes-report.json",0)){out.write(results.toString(2).getBytes("UTF-8"));}
      status.setText(results.toString(2));}catch(Exception failure){throw new RuntimeException(failure);}}
}
