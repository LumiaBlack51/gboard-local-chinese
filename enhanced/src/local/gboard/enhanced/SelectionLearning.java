package local.gboard.enhanced;

import android.content.Context;
import android.content.SharedPreferences;

/** Local choices are independent of immutable/max-merged import frequencies. */
public final class SelectionLearning {
  private static SharedPreferences prefs(Context context){
    return context.getSharedPreferences("shared-word-choices",Context.MODE_PRIVATE);
  }
  public static String preferred(Context context,String raw){
    return prefs(context).getString(DictionaryFile.normalize(raw),"");
  }
  public static void record(Context context,String raw,String word){
    String code=DictionaryFile.normalize(raw);
    // Bounded by imported codes, with no input history or sentence log.
    if(code.isEmpty()||LocalWords.match(code).isEmpty())return;
    prefs(context).edit().putString(code,word).apply();
  }
}
