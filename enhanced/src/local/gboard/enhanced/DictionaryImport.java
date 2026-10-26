package local.gboard.enhanced;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.util.*;

/** File access is limited to the document explicitly chosen in Android's picker. */
final class DictionaryImport {
  static final int REQUEST=741;
  private final Activity activity;
  private final TextView status;
  private final Button choose,clear;
  private boolean busy;
  DictionaryImport(Activity owner,LinearLayout layout){
    activity=owner;
    TextView title=new TextView(owner);title.setText("本地词库");title.setTextSize(18);layout.addView(title);
    status=new TextView(owner);layout.addView(status);
    TextView note=new TextView(owner);note.setText("导入词条可按完整拼音选用。支持带拼音的 UTF-8 文本和 Gboard 词库 ZIP，最多 50000 条；导入会合并并去重。词库仅保存在本机。讯飞用户词需先导出并转换，不能直接连接其云词库或导入加密备份。");layout.addView(note);
    choose=new Button(owner);choose.setText("导入本地词库");layout.addView(choose);
    choose.setOnClickListener(v->{
      Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
      try{busy(true);activity.startActivityForResult(intent,REQUEST);}
      catch(ActivityNotFoundException error){busy(false);error("此设备没有可用的文件选择器");}
    });
    Button help=new Button(owner);help.setText("词库格式说明");layout.addView(help);
    help.setOnClickListener(v->new AlertDialog.Builder(owner).setTitle("导入词库")
        .setMessage("文本每行：中文词语 + Tab + 无声调拼音，可再加 Tab 和词频。\n例如：\n离线词库\tlixianciku\n\n也支持由项目 local_vocab.py 转换的 Gboard 词库 ZIP。只有词语、没有拼音的讯飞文本，先用该工具生成并核对读音，尤其是多音字和姓名。\n\n仅迁移显式词条，不迁移讯飞预测模型、学习历史或云联想。二进制、加密备份不支持。")
        .setPositiveButton("知道了",null).show());
    clear=new Button(owner);clear.setText("清除导入的词库");layout.addView(clear);
    clear.setOnClickListener(v->new AlertDialog.Builder(owner).setTitle("清除导入的词库？")
        .setMessage("仅清除通过此入口导入的词条。Rime 学习词库和系统词典会保留。")
        .setNegativeButton("取消",null).setPositiveButton("清除",(dialog,which)->{
          busy(true);new Thread(()->{try{DictionaryStore.clear(owner.getApplicationContext());done("已清除导入的词库");}catch(IOException e){failed(e);}},"LocalDictionary-Clear").start();
        }).show());
    refresh();
  }
  private void busy(boolean value){busy=value;choose.setEnabled(!value);clear.setEnabled(!value);}
  private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
  private void error(String message){new AlertDialog.Builder(activity).setTitle("词库未导入").setMessage(message).setPositiveButton("知道了",null).show();}
  private void refresh(){
    new Thread(()->{try{int count=DictionaryStore.count(activity.getApplicationContext());activity.runOnUiThread(()->{if(alive())status.setText("已导入 "+count+" 条词条");});}
      catch(IOException e){activity.runOnUiThread(()->{if(alive())status.setText("本地词库读取失败，可重新导入或清除；学习词库不受影响");});}},"LocalDictionary-Count").start();
  }
  private void done(String message){activity.runOnUiThread(()->{if(!alive())return;busy(false);refresh();Toast.makeText(activity,message,Toast.LENGTH_LONG).show();});}
  private void failed(Exception error){activity.runOnUiThread(()->{if(!alive())return;busy(false);error(error instanceof IOException?error.getMessage():"无法读取所选文件，请选择本地可读文件");});}
  void result(int result,Intent data){
    busy(false);if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
    final Uri uri=data.getData();busy(true);
    new Thread(()->{
      try{
        List<DictionaryFile.Entry> words;
        try(InputStream input=activity.getContentResolver().openInputStream(uri)){
          if(input==null)throw new IOException("无法读取所选文件");words=DictionaryFile.read(input);
        }
        int old=DictionaryStore.count(activity.getApplicationContext());int total=DictionaryStore.previewCount(activity.getApplicationContext(),words);
        final List<DictionaryFile.Entry> imported=words;
        activity.runOnUiThread(()->{
          if(!alive())return;busy(false);
          new AlertDialog.Builder(activity).setTitle("确认导入词库")
              .setMessage("文件包含 "+imported.size()+" 条有效词条。当前已有 "+old+" 条，合并去重后共 "+total+" 条。\n请确认已核对词库来源和多音字读音。")
              .setNegativeButton("取消",null).setPositiveButton("导入",(dialog,which)->{
                if(busy)return;busy(true);new Thread(()->{try{DictionaryStore.merge(activity.getApplicationContext(),imported);done("词库已导入，返回键盘即可使用");}catch(IOException e){failed(e);}},"LocalDictionary-Save").start();
              }).show();
        });
      }catch(Exception error){failed(error);}
    },"LocalDictionary-Import").start();
  }
}
