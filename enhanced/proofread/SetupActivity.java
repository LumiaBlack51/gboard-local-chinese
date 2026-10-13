package local.gboard.proofread;
import android.app.Activity;import android.os.*;import android.widget.*;
/** Explicit first-run preparation keeps model compilation out of a recording session. */
public final class SetupActivity extends Activity {
  volatile boolean closed;ProofEngine.Run run;
  public void onCreate(Bundle state){super.onCreate(state);TextView status=new TextView(this);status.setTextSize(18);status.setPadding(40,100,40,40);status.setText("正在准备中英本地校对…\n首次准备可能需要约一分钟。完成后回到键盘即可使用。\n全部处理在本机进行，不需要联网或麦克风权限。");setContentView(status);
    new Thread(()->{String result;try(ProofEngine engine=new ProofEngine(this);ProofEngine.Run request=new ProofEngine.Run(120000)){run=request;engine.loadChinese(request,"auto");engine.loadEnglish(request);result="中英本地校对已就绪\n中文："+engine.backend+"\n英文：小模型、两条 CPU 线程\n\n返回键盘即可使用。模型按需加载，空闲后释放。";}catch(Exception e){result="准备未完成，可以稍后重新打开此组件重试。\n键盘仍可使用原始语音识别文字。";android.util.Log.i("LocalProof","Preparation failed: "+e.getClass().getSimpleName());}finally{run=null;}final String text=result;runOnUiThread(()->{if(!closed)status.setText(text);});},"PrepareLocalProof").start();
  }
  public void onDestroy(){closed=true;if(run!=null)run.cancel();super.onDestroy();}
}
