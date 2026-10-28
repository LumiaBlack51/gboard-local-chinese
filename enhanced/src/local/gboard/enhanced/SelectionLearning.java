package local.gboard.enhanced;

import android.content.Context;
import java.util.*;

/** Local choices are independent of immutable/max-merged import frequencies. */
public final class SelectionLearning {
  public static String preferred(Context context,String raw){
    try{return LearningStore.preferred(context,raw);}catch(java.io.IOException error){return "";}
  }
  public static List<LearningFile.Entry> match(Context context,String raw){
    try{List<LearningFile.Entry> rows=LearningStore.match(context,raw);String preferred=LearningStore.preferred(context,raw);
      rows.sort((a,b)->{boolean ap=a.word.equals(preferred),bp=b.word.equals(preferred);if(ap!=bp)return ap?-1:1;if(a.count!=b.count)return Integer.compare(b.count,a.count);return a.word.compareTo(b.word);});return rows;
    }catch(java.io.IOException error){return Collections.emptyList();}
  }
  public static void record(Context context,String raw,String word){
    try{LearningStore.record(context,raw,word);}catch(java.io.IOException error){android.util.Log.e("LocalChinese","Selection aggregate unavailable",error);}
  }
}
