package local.gboard.enhanced;
import android.content.Context;
import android.content.SharedPreferences;
final class LocalSettings {
  static SharedPreferences prefs(Context context){return context.getSharedPreferences("local-enhancement-settings",Context.MODE_PRIVATE);}
  static boolean handwriting(Context c){return prefs(c).getBoolean("handwriting",true);}
  static boolean board(Context c){return prefs(c).getBoolean("handwriting_board",false);}
  static void board(Context c,boolean enabled){prefs(c).edit().putBoolean("handwriting_board",enabled).apply();KeyboardGestures.refresh();}
  static boolean ticks(Context c){return prefs(c).getBoolean("caret_ticks",true);}
}
