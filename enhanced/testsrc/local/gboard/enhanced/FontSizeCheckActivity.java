package local.gboard.enhanced;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;

/** AVD-only checks against native Gboard key and candidate views. */
public final class FontSizeCheckActivity extends Activity {
  private final JSONObject report=new JSONObject();private TextView status;private Object oldKeyboard,oldCandidate;
  private Object call(Object target,String name,Object...args)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(target,args);}throw new NoSuchMethodException(name);}
  private void check(String name,boolean pass)throws Exception{report.put(name,pass);if(!pass)throw new Exception(name);}
  private boolean near(float a,float b){return Math.abs(a-b)<0.1f;}
  private TextView label(View view){if(view instanceof TextView)return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView text=label(group.getChildAt(i));if(text!=null)return text;}}return null;}
  private void measure(View view){view.measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(144,View.MeasureSpec.EXACTLY));}
  public void onCreate(Bundle state){super.onCreate(state);if(!Diagnostics.isDebug(this)){finish();return;}status=new TextView(this);setContentView(status);
    oldKeyboard=LocalSettings.prefs(this).getAll().get("keyboard_font_percent");oldCandidate=LocalSettings.prefs(this).getAll().get("candidate_font_percent");
    try{
      LocalSettings.prefs(this).edit().remove("keyboard_font_percent").remove("candidate_font_percent").commit();
      check("default_keyboard_100_candidate_115",LocalSettings.fontPercent(this,false)==100&&LocalSettings.fontPercent(this,true)==115);
      Context theme=new ContextThemeWrapper(this,0x7f150402);theme.getTheme().applyStyle(0x7f150421,true);theme.getTheme().applyStyle(0x7f150439,true);
      Class<?> keyClass=Class.forName("com.google.android.libraries.inputmethod.widgets.SoftKeyView");
      ViewGroup keyboard=(ViewGroup)Class.forName("com.google.android.libraries.inputmethod.widgets.SoftKeyboardView").getConstructor(Context.class).newInstance(theme);
      ViewGroup key=(ViewGroup)keyClass.getConstructor(Context.class).newInstance(theme);TextView letter=new TextView(theme);letter.setText("Q");letter.setTextSize(TypedValue.COMPLEX_UNIT_PX,20);key.addView(letter);keyboard.addView(key);
      measure(key);check("native_key_default_size_preserved",near(letter.getTextSize(),20));
      LocalSettings.fontPercent(this,false,125);measure(key);check("keyboard_independent_scaling",near(letter.getTextSize(),25)&&LocalSettings.fontPercent(this,true)==115);
      for(int i=0;i<30;i++)measure(key);check("repeated_measure_does_not_compound",near(letter.getTextSize(),25));
      FontSizes.restore(key);letter.setTextSize(TypedValue.COMPLEX_UNIT_PX,24);measure(key);check("theme_size_change_uses_new_baseline",near(letter.getTextSize(),30));
      keyboard.removeView(key);measure(key);check("unrelated_keys_not_scaled",near(letter.getTextSize(),24));
      int id=0;for(int i=0x7f0e0000;i<0x7f0e1000;i++)try{TypedValue value=new TypedValue();getResources().getValue(i,value,true);if(value.string!=null&&value.string.toString().equals("res/1Fy.xml")){id=i;break;}}catch(Exception ignored){}
      check("native_candidate_xml_found",id!=0);ViewGroup strip=(ViewGroup)LayoutInflater.from(theme).inflate(id,null);
      Object builder=Class.forName("oqd").getDeclaredConstructor().newInstance();builder.getClass().getField("a").set(builder,"联想文字");Object candidate=call(builder,"a");
      Field factoryField=strip.getClass().getDeclaredField("l");factoryField.setAccessible(true);Object factory=factoryField.get(strip);
      View item=(View)call(factory,"e");strip.addView(item);Object definition=call(factory,"d",0,candidate,false,0x7f0e0627);call(item,"c",definition);report.put("native_candidate_child_count",((ViewGroup)item).getChildCount());report.put("native_candidate_layout",definition.getClass().getField("e").get(definition));TextView text=label(item);check("native_candidate_label_found",text!=null);
      LocalSettings.fontPercent(this,true,100);measure(item);float base=text.getTextSize();
      LocalSettings.fontPercent(this,true,115);measure(item);check("native_candidate_grows_15_percent",near(text.getTextSize(),base*1.15f));
      call(item,"o",0.8f);measure(item);check("native_height_scaling_not_compounded",near(text.getTextSize(),base*0.8f*1.15f));
      call(item,"o",1f);measure(item);check("native_height_reset_restores_baseline",near(text.getTextSize(),base*1.15f));
      for(int i=0;i<20;i++){call(item,"c",call(factory,"d",0,candidate,false,0x7f0e0627));measure(item);}check("recycled_candidates_do_not_compound",near(text.getTextSize(),base*1.15f));
      ((ViewGroup)item.getParent()).removeView(item);measure(item);check("detached_candidate_width_uses_same_font",near(text.getTextSize(),base*1.15f));
      LocalSettings.fontPercent(this,true,150);measure(item);check("max_candidate_font_applied",near(text.getTextSize(),base*1.5f));
      LocalSettings.fontPercent(this,true,1000);check("upper_limit_clamped",LocalSettings.fontPercent(this,true)==150);LocalSettings.fontPercent(this,false,0);check("lower_limit_clamped",LocalSettings.fontPercent(this,false)==80);
      check("font_preferences_saved",LocalSettings.prefs(this).getInt("candidate_font_percent",0)==150&&LocalSettings.prefs(this).getInt("keyboard_font_percent",0)==80);
      save(null);
    }catch(Throwable error){save(error);}
  }
  private void save(Throwable error){try{
    android.content.SharedPreferences.Editor prefs=LocalSettings.prefs(this).edit();if(oldKeyboard==null)prefs.remove("keyboard_font_percent");else prefs.putInt("keyboard_font_percent",(Integer)oldKeyboard);if(oldCandidate==null)prefs.remove("candidate_font_percent");else prefs.putInt("candidate_font_percent",(Integer)oldCandidate);prefs.commit();FontSizes.refresh();
    if(error!=null)report.put("error",android.util.Log.getStackTraceString(error));report.put("passed",error==null);
    try(java.io.FileOutputStream out=openFileOutput("font-size-report.json",0)){out.write(report.toString(2).getBytes("UTF-8"));}status.setText(report.toString(2));
  }catch(Exception failure){throw new RuntimeException(failure);}}
}
