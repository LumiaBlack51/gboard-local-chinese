package local.gboard.enhanced;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
public final class ManagementActivity extends Activity {
  public void onCreate(Bundle state){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);int p=(int)(20*getResources().getDisplayMetrics().density);layout.setPadding(p,p,p,p);
    TextView title=new TextView(this);String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;int local=version.indexOf("-local-");if(local>=0)version=version.substring(local+7);}catch(Exception ignored){}title.setText("Gboard 中文本地增强 "+version);title.setTextSize(24);layout.addView(title);
    TextView note=new TextView(this);note.setText("按住空格左右滑动可修改未确认拼音，点拼音可放大选位。中文 26 键点击输入拼音、滑动画笔画，手写候选点击确认；删除可撤销笔画。语音点即开始，停顿后自动填入。中文、手写和中英语音均在本地运行。");layout.addView(note);
    CheckBox handwriting=new CheckBox(this);handwriting.setText("中文 26 键滑动手写");handwriting.setChecked(LocalSettings.handwriting(this));layout.addView(handwriting);handwriting.setOnCheckedChangeListener((b,on)->{LocalSettings.prefs(this).edit().putBoolean("handwriting",on).apply();KeyboardGestures.refresh();});
    CheckBox board=new CheckBox(this);board.setText("使用独立手写板（板上点 26键返回）");board.setChecked(LocalSettings.board(this));layout.addView(board);board.setOnCheckedChangeListener((b,on)->{if(on){handwriting.setChecked(true);}LocalSettings.board(this,on);});
    CheckBox ticks=new CheckBox(this);ticks.setText("拼音光标逐字震动（遵循系统触感设置）");ticks.setChecked(LocalSettings.ticks(this));layout.addView(ticks);ticks.setOnCheckedChangeListener((b,on)->LocalSettings.prefs(this).edit().putBoolean("caret_ticks",on).apply());
    Button settings=new Button(this);settings.setText("键盘设置");layout.addView(settings);settings.setOnClickListener(v->startActivity(new Intent().setClassName(getPackageName(),"com.google.android.apps.inputmethod.latin.preference.SettingsActivity")));
    Button update=new Button(this);update.setText("检查更新");layout.addView(update);update.setOnClickListener(v->{if(getPackageManager().checkSignatures(getPackageName(),"local.gboard.updater")!=PackageManager.SIGNATURE_MATCH){Toast.makeText(this,"请先安装此版本配套的 updater.apk",Toast.LENGTH_LONG).show();return;}startActivity(new Intent().setClassName("local.gboard.updater","local.gboard.updater.UpdateActivity"));});
    setContentView(layout);
  }
}
