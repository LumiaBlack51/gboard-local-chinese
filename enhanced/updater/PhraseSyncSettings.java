package local.gboard.updater;
import android.app.*;
import android.os.*;
import android.widget.*;
import java.text.DateFormat;
import org.json.*;
/** Pairing is explicit; release binaries contain no personal endpoint or credential. */
public final class PhraseSyncSettings extends Activity {
  private TextView status;
  public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(32,32,32,32);
    TextView note=new TextView(this);note.setText("手机与电脑共享中文词条和选词学习\n默认无论插电或离电，均由系统每小时安排一次，系统省电可能延后；可关闭电池供电同步以改为仅插电运行。没有常驻服务，不同步时同步进程退出。键盘使用中仅延后 Rime 快照刷新。\n共享词条，以及真实选择的累计次数和最近选择时间；学习统计会影响两端候选排序，导入词库频率不会直接置顶原生字词。本地新选择优先于旧的远端记录。不同引擎的候选顺序仍可能不同。不共享原始输入、密码、语音或模型。重复同步不增加计数；清除本机词库不会删除其他设备和云端词条。");body.addView(note);
    status=new TextView(this);status.setText(syncStatus());body.addView(status);
    Switch enabled=new Switch(this);enabled.setText("每小时共享词库");enabled.setChecked(PhraseSync.prefs(this).getBoolean("enabled",false));body.addView(enabled);
    enabled.setOnCheckedChangeListener((b,on)->{PhraseSync.prefs(this).edit().putBoolean("enabled",on).commit();PhraseSync.schedule(this);});
    Switch battery=new Switch(this);battery.setText("允许电池供电时同步（仍不常驻后台）");battery.setChecked(PhraseSync.prefs(this).getBoolean("allow_battery",true));body.addView(battery);
    battery.setOnCheckedChangeListener((b,on)->{PhraseSync.prefs(this).edit().putBoolean("allow_battery",on).commit();getSystemService(android.app.job.JobScheduler.class).cancel(PhraseSync.JOB_ID);PhraseSync.schedule(this);});
    EditText config=new EditText(this);config.setHint("粘贴新设备配对 JSON（含 HTTPS 地址、证书指纹、设备令牌）");config.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.addView(config);
    Button pair=new Button(this);pair.setText("保存配对并启用");body.addView(pair);pair.setOnClickListener(v->{try{PhraseSync.configure(this,config.getText().toString());config.setText("");enabled.setChecked(true);status.setText("已配对。等待系统每小时同步。");}catch(Exception error){status.setText("配对信息无效，请核对地址、指纹和令牌。");}});
    ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
  }
  @Override public void onResume(){super.onResume();if(status!=null)status.setText(syncStatus());}
  private static String date(long stamp){return DateFormat.getDateTimeInstance().format(new java.util.Date(stamp));}
  private String syncStatus(){
    android.content.SharedPreferences prefs=PhraseSync.prefs(this);long attempt=prefs.getLong("last_attempt",0),success=prefs.getLong("last_success",0);String outcome=prefs.getString("last_result","");
    if(attempt==0)return "尚无同步记录";
    if(success==0&&"success".equals(outcome))success=attempt;
    String learning=prefs.getBoolean("learning_synced",false)?"词条和选词学习（"+prefs.getInt("learned_words",0)+" 条学习记录）":"仅词条；服务端尚未确认支持选词学习";
    String result="success".equals(outcome)?"成功":"cancelled".equals(outcome)?"已取消／超时":"失败，等待下次系统调度";
    String stage=prefs.getString("last_stage",""),error=prefs.getString("last_error","");
    if(!"success".equals(outcome)&&!stage.isEmpty())result+=" · 阶段："+("export".equals(stage)?"导出本机词库":"network".equals(stage)?"连接／交换":"validate".equals(stage)?"校验响应":"merge_words".equals(stage)?"保存词条":"merge_learning".equals(stage)?"保存学习":"准备");
    if(!error.isEmpty())result+=" · "+("tls".equals(error)?"TLS 连接失败":"timeout".equals(error)?"网络超时":"dns".equals(error)?"地址解析失败":"connect".equals(error)?"无法连接服务器":"permission".equals(error)?"权限不可用":"deadline".equals(error)?"任务超时":"cancelled".equals(error)?"系统停止或供电策略取消":"io".equals(error)?"读写／交换失败":"任务异常");
    return "最近成功："+(success==0?"暂无可确认记录":date(success)+" · "+learning)+"\n上次尝试："+date(attempt)+" · "+result;
  }
}
