package local.gboard.candidateregression;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Headless emulator-only regression for the actual shipped candidate holder. */
public final class CandidateRegressionInstrumentation extends Instrumentation {
  public void onCreate(Bundle args){super.onCreate(args);start();}
  static Object call(Object target,String name,Object...args)throws Exception{
    for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(target,args);}throw new NoSuchMethodException(name);
  }
  static void put(Object target,String name,Object value)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
  public void onStart(){Bundle result=new Bundle();final Throwable[] failure={null};
    runOnMainSync(()->{try{
      Context context=getTargetContext();ContextThemeWrapper themed=new ContextThemeWrapper(context,0x7f150402);themed.getTheme().applyStyle(0x7f150421,true);themed.getTheme().applyStyle(0x7f150439,true);
      int id=0;for(int i=0x7f0e0000;i<0x7f0e1000;i++)try{TypedValue value=new TypedValue();context.getResources().getValue(i,value,true);if(value.string!=null&&value.string.toString().equals("res/-Z2.xml")){id=i;break;}}catch(Exception ignored){}
      if(id==0)throw new Exception("candidate XML unavailable");
      ViewGroup bar=null;
      android.content.res.XmlResourceParser xml=context.getResources().getLayout(id);
      while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT)if(xml.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG&&xml.getName().endsWith(".FixedSizeCandidatesHolderView")){bar=(ViewGroup)Class.forName(xml.getName()).getConstructor(Context.class,android.util.AttributeSet.class).newInstance(themed,Xml.asAttributeSet(xml));break;}
      xml.close();if(bar==null)throw new Exception("native holder XML missing");
      FrameLayout host=new FrameLayout(themed);host.addView(bar,new FrameLayout.LayoutParams(1200,144));host.measure(View.MeasureSpec.makeMeasureSpec(1200,1073741824),View.MeasureSpec.makeMeasureSpec(144,1073741824));host.layout(0,0,1200,144);
      Class<?> hook=Class.forName("local.gboard.enhanced.EnhancedHook");hook.getMethod("bindCandidateBar",View.class).invoke(null,bar);
      View close=null;for(int i=0;i<host.getChildCount();i++){View v=host.getChildAt(i);if(v!=bar)close=v;}
      if(close==null||close.getParent()==bar)throw new Exception("dismiss must be outside native children");
      for(int count:new int[]{12,1,8,2,1,0,4,1,0}){
        call(bar,"C");ArrayList<Object> list=new ArrayList<>();for(int i=0;i<count;i++){Object builder=Class.forName("oqd").getDeclaredConstructor().newInstance();put(builder,"a","合成候选"+i);put(builder,"k",i);put(builder,"l",i);list.add(call(builder,"a"));}
        call(bar,"z",list,new ArrayList<>());
        for(int i=0;i<bar.getChildCount();i++)if(!bar.getChildAt(i).getClass().getName().endsWith("SoftKeyView"))throw new Exception("foreign native child");
      }
      result.putBoolean("passed",true);result.putInt("candidate_transitions",9);result.putBoolean("dismiss_outside_native_children",true);
    }catch(Throwable error){failure[0]=error;}});
    if(failure[0]!=null)result.putString("error",android.util.Log.getStackTraceString(failure[0]));finish(failure[0]==null?-1:0,result);
  }
}
