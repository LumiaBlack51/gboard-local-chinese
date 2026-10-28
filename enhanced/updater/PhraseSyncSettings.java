package local.gboard.updater;
import android.app.*;
import android.os.*;
import android.widget.*;
import java.text.DateFormat;
import org.json.*;
/** Pairing is explicit; release binaries contain no personal endpoint or credential. */
public final class PhraseSyncSettings extends Activity {
  public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(32,32,32,32);
    TextView note=new TextView(this);note.setText("手机与电脑共享中文词条和选词学习\n默认无论插电或离电，均由系统每小时安排一次，系统省电可能延后；可关闭电池供电同步以改为仅插电运行。没有常驻服务，不同步时同步进程退出。键盘使用中仅延后 Rime 快照刷新。\n共享词条，以及真实选择的累计次数和最近选择时间；学习统计会影响两端候选排序，导入词库频率不会直接置顶原生字词。本地新选择优先于旧的远端记录。不同引擎的候选顺序仍可能不同。不共享原始输入、密码、语音或模型。重复同步不增加计数；清除本机词库不会删除其他设备和云端词条。");body.addView(note);
    TextView status=new TextView(this);long attempt=PhraseSync.prefs(this).getLong("last_attempt",0);String outcome=PhraseSync.prefs(this).getString("last_result","");String learning=PhraseSync.prefs(this).getBoolean("learning_synced",false)?"词条和选词学习已同步（"+PhraseSync.prefs(this).getInt("learned_words",0)+" 条学习记录）":"仅词条；服务端尚未确认支持选词学习";status.setText(attempt==0?"尚无同步记录":"上次尝试："+DateFormat.getDateTimeInstance().format(new java.util.Date(attempt))+" · "+("success".equals(outcome)?learning:outcome));body.addView(status);
    Switch enabled=new Switch(this);enabled.setText("每小时共享词库");enabled.setChecked(PhraseSync.prefs(this).getBoolean("enabled",false));body.addView(enabled);
    enabled.setOnCheckedChangeListener((b,on)->{PhraseSync.prefs(this).edit().putBoolean("enabled",on).commit();PhraseSync.schedule(this);});
    Switch battery=new Switch(this);battery.setText("允许电池供电时同步（仍不常驻后台）");battery.setChecked(PhraseSync.prefs(this).getBoolean("allow_battery",true));body.addView(battery);
    battery.setOnCheckedChangeListener((b,on)->{PhraseSync.prefs(this).edit().putBoolean("allow_battery",on).commit();getSystemService(android.app.job.JobScheduler.class).cancel(PhraseSync.JOB_ID);PhraseSync.schedule(this);});
    EditText config=new EditText(this);config.setHint("粘贴新设备配对 JSON（含 HTTPS 地址、证书指纹、设备令牌）");config.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.addView(config);
    Button pair=new Button(this);pair.setText("保存配对并启用");body.addView(pair);pair.setOnClickListener(v->{try{PhraseSync.configure(this,config.getText().toString());config.setText("");enabled.setChecked(true);status.setText("已配对。等待系统每小时同步。");}catch(Exception error){status.setText("配对信息无效，请核对地址、指纹和令牌。");}});
    ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
  }
}
