package local.gboard.enhanced;
import android.content.Context;
import java.io.*;
import org.json.JSONObject;
/** All audio stays in memory and is decoded by one Mandarin/English model. */
public final class VoiceBridge {
  private static long model;
  private static long punctuation;
  public static synchronized long prepare(Context app)throws IOException {
    if(model!=0)return model;
    File dir=new File(app.getNoBackupFilesDir(),"voice-zh-en-2023-02-20");copy(app,"local-voice",dir);
    System.loadLibrary("onnxruntime");System.loadLibrary("sherpa-onnx-c-api");System.loadLibrary("gboard_local_voice");
    model=model(dir.getAbsolutePath());if(model==0)throw new IOException("无法载入离线语音模型");
    // Warm both models through the same speech broker; never open the microphone.
    try{preparePunctuation(app);}catch(Exception error){android.util.Log.w("OfflineSpeech","Punctuation unavailable",error);}
    return model;
  }
  private static void preparePunctuation(Context app)throws IOException{
    if(punctuation!=0)return;
    File dir=new File(app.getNoBackupFilesDir(),"punctuation-ct-2024-04-12-int8");copy(app,"local-punctuation",dir);
    punctuation=punctuationModel(new File(dir,"model.int8.onnx").getAbsolutePath());
    if(punctuation==0)throw new IOException("无法载入离线标点模型");
  }
  public static synchronized String punctuate(Context app,String text){
    try{prepare(app);preparePunctuation(app);return VoicePunctuation.restore(text,s->addPunctuation(punctuation,s));}
    catch(Exception error){android.util.Log.w("OfflineSpeech","Using recognition text without model punctuation",error);return text;}
  }
  private static void copy(Context app,String name,File dest)throws IOException {
    String[] children=app.getAssets().list(name);
    if(children.length>0){dest.mkdirs();for(String child:children)copy(app,name+"/"+child,new File(dest,child));}
    else if(!dest.isFile()){
      dest.getParentFile().mkdirs();File tmp=new File(dest.getPath()+".tmp");
      try(InputStream in=app.getAssets().open(name);OutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
      if(!tmp.renameTo(dest))throw new IOException("无法安装本地语音模型");
    }
  }
  public static String parse(String json,String field)throws Exception {JSONObject result=new JSONObject(json);return format(result.optString(field,result.optString("text")));}
  public static String format(String text){
    return text.trim().replaceAll("\\s+"," ").replaceAll("(?<=[\\p{IsHan}]) +(?=[\\p{IsHan}])","");
  }
  public static void append(StringBuilder buffer,String text){
    text=format(text);if(text.isEmpty())return;
    if(buffer.length()>0&&Character.isLetterOrDigit(buffer.charAt(buffer.length()-1))&&buffer.charAt(buffer.length()-1)<128&&Character.isLetterOrDigit(text.charAt(0))&&text.charAt(0)<128)buffer.append(' ');
    buffer.append(text);
  }
  public static native long model(String path);
  private static native long punctuationModel(String path);
  private static native String addPunctuation(long model,String text);
  public static native long create(long model);
  public static native boolean accept(long recognizer,short[] samples,int length);
  public static native String result(long recognizer,int mode);
  public static native void destroy(long recognizer);
}
