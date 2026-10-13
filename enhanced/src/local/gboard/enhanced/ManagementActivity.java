package local.gboard.enhanced;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
public final class ManagementActivity extends Activity {
  private void fontControls(LinearLayout layout,String label,boolean candidates){
    TextView heading=new TextView(this);heading.setText(label);heading.setTextSize(18);layout.addView(heading);
    LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);layout.addView(row);
    Button smaller=new Button(this);smaller.setText("A−");smaller.setContentDescription(label+"缩小");row.addView(smaller,new LinearLayout.LayoutParams(0,-2,1));
    TextView value=new TextView(this);value.setTextSize(18);value.setGravity(android.view.Gravity.CENTER);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
    Button larger=new Button(this);larger.setText("A+");larger.setContentDescription(label+"放大");row.addView(larger,new LinearLayout.LayoutParams(0,-2,1));
    Runnable render=()->{int percent=LocalSettings.fontPercent(this,candidates);value.setText(percent+"%");smaller.setEnabled(percent>LocalSettings.MIN_FONT);larger.setEnabled(percent<LocalSettings.MAX_FONT);};
    smaller.setOnClickListener(v->{LocalSettings.fontPercent(this,candidates,LocalSettings.fontPercent(this,candidates)-LocalSettings.FONT_STEP);render.run();});
    larger.setOnClickListener(v->{LocalSettings.fontPercent(this,candidates,LocalSettings.fontPercent(this,candidates)+LocalSettings.FONT_STEP);render.run();});
    Button reset=new Button(this);reset.setText("恢复默认");reset.setContentDescription(label+"恢复默认");row.addView(reset,new LinearLayout.LayoutParams(0,-2,1));reset.setOnClickListener(v->{LocalSettings.fontPercent(this,candidates,candidates?115:100);render.run();});render.run();
  }
  public void onCreate(Bundle state){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);int p=(int)(20*getResources().getDisplayMetrics().density);layout.setPadding(p,p,p,p);
    TextView title=new TextView(this);String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;int local=version.indexOf("-local-");if(local>=0)version=version.substring(local+7);}catch(Exception ignored){}title.setText("Gboard 中文本地增强 "+version);title.setTextSize(24);layout.addView(title);
    TextView note=new TextView(this);note.setText("按住空格左右滑动可修改未确认拼音，点拼音可放大选位。中文 26 键点击输入拼音、滑动画笔画，手写候选点击确认；删除可撤销笔画。语音点即开始，停顿后自动填入。中文、手写和中英语音均在本地运行。");layout.addView(note);
    fontControls(layout,"键盘文字大小",false);fontControls(layout,"候选／联想文字大小",true);
    TextView fontNote=new TextView(this);fontNote.setText("字号自动保存，返回键盘即可生效。候选／联想文字默认放大至 115%，可分别调整至 80%～150%。");layout.addView(fontNote);
    CheckBox handwriting=new CheckBox(this);handwriting.setText("中文 26 键滑动手写");handwriting.setChecked(LocalSettings.handwriting(this));layout.addView(handwriting);handwriting.setOnCheckedChangeListener((b,on)->{LocalSettings.prefs(this).edit().putBoolean("handwriting",on).apply();KeyboardGestures.refresh();});
    CheckBox board=new CheckBox(this);board.setText("使用独立手写板（板上点 26键返回）");board.setChecked(LocalSettings.board(this));layout.addView(board);board.setOnCheckedChangeListener((b,on)->{if(on){handwriting.setChecked(true);}LocalSettings.board(this,on);});
    CheckBox continuous=new CheckBox(this);continuous.setText("手写连写（停笔后自动填入，旧字渐淡）");continuous.setChecked(LocalSettings.continuous(this));layout.addView(continuous);continuous.setOnCheckedChangeListener((b,on)->{if(on)handwriting.setChecked(true);LocalSettings.continuous(this,on);});
    TextView continuousNote=new TextView(this);continuousNote.setText("连写开启后，停笔约 0.9 秒自动选首候选；旧字未消失即可写下一字。每笔逐渐变淡，识别仍保留完整笔画。关闭后手动选字。行草和不按笔顺的写法仍可能识别不准。");layout.addView(continuousNote);
    CheckBox ticks=new CheckBox(this);ticks.setText("拼音光标逐字震动（遵循系统触感设置）");ticks.setChecked(LocalSettings.ticks(this));layout.addView(ticks);ticks.setOnCheckedChangeListener((b,on)->LocalSettings.prefs(this).edit().putBoolean("caret_ticks",on).apply());
    Button settings=new Button(this);settings.setText("键盘设置");layout.addView(settings);settings.setOnClickListener(v->startActivity(new Intent().setClassName(getPackageName(),"com.google.android.apps.inputmethod.latin.preference.SettingsActivity")));
    Button update=new Button(this);update.setText("检查更新");layout.addView(update);update.setOnClickListener(v->{if(getPackageManager().checkSignatures(getPackageName(),"local.gboard.updater")!=PackageManager.SIGNATURE_MATCH){Toast.makeText(this,"请先安装此版本配套的 updater.apk",Toast.LENGTH_LONG).show();return;}startActivity(new Intent().setClassName("local.gboard.updater","local.gboard.updater.UpdateActivity"));});
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(layout);setContentView(scroll);
  }
}
