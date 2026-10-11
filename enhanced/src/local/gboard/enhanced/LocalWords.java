package local.gboard.enhanced;
import android.content.Context;
import android.database.*;
import android.os.*;
import android.provider.UserDictionary;
import java.util.*;
/** Honor local Android dictionary shortcuts without exporting their contents. */
public final class LocalWords {
  private static volatile List<WordIndex.Entry> words=Collections.emptyList();
  public static void load(Context app){
    refresh(app);
    try{app.getContentResolver().registerContentObserver(UserDictionary.Words.CONTENT_URI,true,new ContentObserver(new Handler(Looper.getMainLooper())){public void onChange(boolean self){new Thread(()->refresh(app),"LocalDictionary-Read").start();}});}catch(Exception ignored){}
  }
  private static void refresh(Context app){
    ArrayList<WordIndex.Entry> result=new ArrayList<>();
    try(Cursor cursor=app.getContentResolver().query(UserDictionary.Words.CONTENT_URI,new String[]{"word","shortcut","locale","frequency"},null,null,null)){
      if(cursor!=null)while(cursor.moveToNext()){
        String text=cursor.getString(0),code=cursor.getString(1),locale=cursor.getString(2);
        if(text==null||code==null||!(locale==null||locale.startsWith("zh")))continue;
        code=code.toLowerCase(Locale.ROOT).replace(" ","").replace("'","");
        if(code.matches("[a-z]{2,64}"))result.add(new WordIndex.Entry(code,text,cursor.getInt(3)));
      }
      words=Collections.unmodifiableList(result);
    }catch(Exception error){android.util.Log.w("LocalChinese","System dictionary unavailable");}
  }
  public static List<WordIndex.Entry> match(String input){String code=input.replace("'","");ArrayList<WordIndex.Entry> result=new ArrayList<>();for(WordIndex.Entry entry:words)if(entry.code.equals(code))result.add(entry);return result;}
}
