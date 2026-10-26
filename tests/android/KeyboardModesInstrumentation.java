package local.gboard.keyboardmodescheck;

import android.app.Instrumentation;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import org.json.JSONObject;

/** Exercise the real production IME only in temporary, private synthetic editors. */
public final class KeyboardModesInstrumentation extends Instrumentation {
  private boolean inspect;
  private InputMethodService ime;
  public void onCreate(Bundle args){super.onCreate(args);inspect="true".equals(args.getString("inspect"));start();}
  private void shell(String command)throws Exception{
    android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
    try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}}
  }
  private void main(Runnable work)throws Exception{
    final Throwable[] error=new Throwable[1];runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});
    if(error[0]!=null)throw new Exception(error[0]);
  }
  private void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
  private void editor(){require(ime.getCurrentInputEditorInfo()!=null&&"local.gboard.probe".equals(ime.getCurrentInputEditorInfo().packageName),"Synthetic editor required");}
  private View find(View view,String label){
    if(!view.isShown())return null;
    if(label.equals(" ")&&view.getClass().getName().endsWith("SoftKeyView"))try{
      Field def=view.getClass().getDeclaredField("e");def.setAccessible(true);Object definition=def.get(view);
      Field actions=definition.getClass().getDeclaredField("f");actions.setAccessible(true);
      for(Object action:(Object[])actions.get(definition)){Field events=action.getClass().getDeclaredField("d");events.setAccessible(true);
        for(Object event:(Object[])events.get(action)){Field code=event.getClass().getDeclaredField("c");code.setAccessible(true);if(code.getInt(event)==62)return view;}}
    }catch(Exception ignored){}
    if(view instanceof TextView&&label.equals(((TextView)view).getText().toString().trim()))return view;
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}
    return null;
  }
  private View key(String label){return find(ime.getWindow().getWindow().getDecorView(),label);}
  private void backToLetters()throws Exception{final String[] label=new String[1];main(()->label[0]=key("返回")!=null?"返回":"ABC");tap(label[0]);}
  private void tap(String label)throws Exception{
    final int[] xy=new int[2];main(()->{editor();View view=key(label);require(view!=null,"Missing key "+label);view.getLocationOnScreen(xy);xy[0]+=view.getWidth()/2;xy[1]+=view.getHeight()/2;});
    shell("input tap "+xy[0]+" "+xy[1]);SystemClock.sleep(300);
  }
  private void open(String type)throws Exception{
    shell("am force-stop local.gboard.probe");
    shell("am start -n local.gboard.probe/.MainActivity --ez private true --es input_type "+type);
    for(int i=0;i<100;i++){
      Field field=Class.forName("local.gboard.enhanced.ToolbarHide").getDeclaredField("current");field.setAccessible(true);
      ime=(InputMethodService)((WeakReference<?>)field.get(null)).get();
      if(ime!=null&&ime.isInputViewShown()&&ime.getCurrentInputEditorInfo()!=null&&"local.gboard.probe".equals(ime.getCurrentInputEditorInfo().packageName))break;
      SystemClock.sleep(100);
    }
    SystemClock.sleep(1300);main(this::editor);
  }
  public void onStart(){Bundle result=new Bundle();JSONObject checks=new JSONObject();try{
    shell("ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME");
    shell("ime set com.localx.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME");
    String[] types=inspect?new String[]{"phone"}:new String[]{"phone","number","signed_decimal","pin","date","time","datetime"};
    for(String type:types){
      open(type);JSONObject row=new JSONObject();final boolean[] hasABC=new boolean[1];main(()->hasABC[0]=(ime.getCurrentInputEditorInfo().inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT&&(key("ABC")!=null||key("返回")!=null));
      row.put("opens_switchable_symbols",hasABC[0]);row.put("editor_type",ime.getCurrentInputEditorInfo().inputType);checks.put(type,row);
      final StringBuilder labels=new StringBuilder();main(()->labels(ime.getWindow().getWindow().getDecorView(),labels));row.put("visible_labels",labels.toString());
      if(inspect)shell("screencap -p /data/local/tmp/keyboard-phone-before.png");
      if(!inspect){
        require(hasABC[0],type+" has no ABC return key");
        main(()->{EditorInfo info=ime.getCurrentInputEditorInfo();require((info.inputType&InputType.TYPE_MASK_CLASS)==InputType.TYPE_CLASS_TEXT,"Dedicated layout remains");
          if(type.equals("pin"))require((info.inputType&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_PASSWORD&&(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0,"PIN privacy lost");});
        if(type.equals("pin"))row.put("password_privacy_preserved",true);
        tap("1");main(()->{editor();require("1".contentEquals(ime.getCurrentInputConnection().getTextBeforeCursor(10,0)),"Numeric entry failed");});row.put("actual_digit_committed",true);
        backToLetters();main(()->require(key("q")!=null&&key("?123")!=null,type+" could not return to letters"));row.put("returns_to_qwerty",true);
        // Reinitializing this same input connection must not force the user back to symbols.
        shell("am start -n local.gboard.probe/.MainActivity --ez private true");SystemClock.sleep(900);
        main(()->{editor();require(key("q")!=null&&key("?123")!=null,"Restart forced symbols again");});row.put("restart_keeps_letters",true);
        tap("?123");backToLetters();main(()->require(key("q")!=null,"Repeated return failed"));row.put("repeated_switching",true);
        if(type.equals("phone")){shell("screencap -p /data/local/tmp/keyboard-phone-letters.png");tap("?123");shell("screencap -p /data/local/tmp/keyboard-phone-symbols.png");}
      }
      checks.put(type,row);
    }
    if(!inspect){open("text");main(()->require(key("q")!=null,"Normal text did not open QWERTY"));tap("n");tap("i");tap("h");tap("a");tap("o");tap(" ");
      main(()->{editor();require("你好".contentEquals(ime.getCurrentInputConnection().getTextBeforeCursor(10,0)),"Normal Chinese composition regressed");});checks.put("ordinary_chinese_input",true);}
    result.putString("checks",checks.toString(2));result.putString("report",inspect?"Baseline inspected":"PASS: real digits, ABC return, repeated switching, input restart, PIN privacy, ordinary Chinese input");finish(0,result);
  }catch(Throwable error){try{shell("screencap -p /data/local/tmp/keyboard-modes-failure.png");}catch(Exception ignored){}result.putString("checks",checks.toString());result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}}
  private void labels(View view,StringBuilder out){if(!view.isShown())return;if(view instanceof TextView)out.append('[').append(((TextView)view).getText()).append(']');if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)labels(g.getChildAt(i),out);}}
}
