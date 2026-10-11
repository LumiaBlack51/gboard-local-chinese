package local.gboard.enhanced;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
public final class ManagementActivity extends Activity {
  public void onCreate(Bundle state){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);int p=(int)(20*getResources().getDisplayMetrics().density);layout.setPadding(p,p,p,p);
    TextView title=new TextView(this);title.setText("Gboard 中文本地增强 1.1.1");title.setTextSize(24);layout.addView(title);
    TextView note=new TextView(this);note.setText("中文预测和中英语音在本地运行。输入拼音时保留展开候选；提交后的联想栏显示独立 ×，点击恢复工具栏。点麦克风即开始，停顿后自动填入语音结果。");layout.addView(note);
    Button settings=new Button(this);settings.setText("键盘设置");layout.addView(settings);settings.setOnClickListener(v->startActivity(new Intent().setClassName(getPackageName(),"com.google.android.apps.inputmethod.latin.preference.SettingsActivity")));
    Button update=new Button(this);update.setText("检查更新");layout.addView(update);update.setOnClickListener(v->{if(getPackageManager().checkSignatures(getPackageName(),"local.gboard.updater")!=PackageManager.SIGNATURE_MATCH){Toast.makeText(this,"请先安装此版本配套的 updater.apk",Toast.LENGTH_LONG).show();return;}startActivity(new Intent().setClassName("local.gboard.updater","local.gboard.updater.UpdateActivity"));});
    setContentView(layout);
  }
}
