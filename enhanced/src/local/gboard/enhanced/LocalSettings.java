package local.gboard.enhanced;
import android.content.Context;
import android.content.SharedPreferences;
final class LocalSettings {
  static SharedPreferences prefs(Context context){return context.getSharedPreferences("local-enhancement-settings",Context.MODE_PRIVATE);}
  static boolean handwriting(Context c){return prefs(c).getBoolean("handwriting",true);}
  static boolean board(Context c){return prefs(c).getBoolean("handwriting_board",false);}
  static void board(Context c,boolean enabled){prefs(c).edit().putBoolean("handwriting_board",enabled).apply();KeyboardGestures.refresh();}
  static boolean continuous(Context c){return prefs(c).getBoolean("handwriting_continuous",false);}
  static void continuous(Context c,boolean enabled){prefs(c).edit().putBoolean("handwriting_continuous",enabled).apply();KeyboardGestures.reset();}
  static boolean ticks(Context c){return prefs(c).getBoolean("caret_ticks",true);}
  static final int CANDIDATE_REPLACE=0,CANDIDATE_BOTH=1,CANDIDATE_EXPAND=2;
  static int candidateControls(Context c){return Math.max(0,Math.min(2,prefs(c).getInt("candidate_controls",CANDIDATE_REPLACE)));}
  static void candidateControls(Context c,int mode){prefs(c).edit().putInt("candidate_controls",mode).apply();EnhancedHook.refreshCandidateBars();}
  static boolean proofread(Context c){return prefs(c).getBoolean("voice_proofread",true);}
  static boolean voiceWarmup(Context c){return prefs(c).getBoolean("voice_warmup",true);}
  static final int MIN_FONT=80,MAX_FONT=150,FONT_STEP=5;
  static int fontPercent(Context c,boolean candidates){return Math.max(MIN_FONT,Math.min(MAX_FONT,prefs(c).getInt(candidates?"candidate_font_percent":"keyboard_font_percent",candidates?115:100)));}
  static void fontPercent(Context c,boolean candidates,int percent){prefs(c).edit().putInt(candidates?"candidate_font_percent":"keyboard_font_percent",Math.max(MIN_FONT,Math.min(MAX_FONT,percent))).apply();FontSizes.refresh();}
}
