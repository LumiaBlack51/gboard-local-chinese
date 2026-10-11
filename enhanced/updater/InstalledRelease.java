package local.gboard.updater;
import android.content.Context;
import android.content.pm.*;
import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;
/** Verify installed APK bytes, without opening application data or input contents. */
final class InstalledRelease {
  static void legacyVersions(Context context)throws Exception{String[] packages={UpdateActivity.KEYBOARD,UpdateActivity.VOICE,UpdateActivity.SELF};long[] codes={ReleaseIdentity.KEYBOARD,ReleaseIdentity.VOICE,ReleaseIdentity.UPDATER};for(int i=0;i<packages.length;i++)if(context.getPackageManager().getPackageInfo(packages[i],0).getLongVersionCode()!=codes[i]||context.getPackageManager().checkSignatures(UpdateActivity.SELF,packages[i])!=PackageManager.SIGNATURE_MATCH)throw new IOException("安装的组件版本或签名不同");}
  static String versions(Context context){StringBuilder text=new StringBuilder("手机已安装：");String[] packages={UpdateActivity.KEYBOARD,UpdateActivity.VOICE,UpdateActivity.SELF};String[] names={"键盘","语音","更新器"};for(int i=0;i<packages.length;i++){text.append('\n').append(names[i]).append("：");try{text.append(context.getPackageManager().getPackageInfo(packages[i],0).versionName);}catch(Exception e){text.append("未安装");}}return text.toString();}
  static String sha(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[131072];int n;while((n=in.read(bytes))!=-1)hash.update(bytes,0,n);}return UpdateActivity.hex(hash.digest());}
  static void verify(Context context,JSONObject manifest)throws Exception{
    JSONArray parts=manifest.getJSONArray("components");
    for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);PackageInfo info=context.getPackageManager().getPackageInfo(part.getString("package"),PackageManager.GET_SIGNING_CERTIFICATES);
      if(info.getLongVersionCode()!=part.getLong("versionCode"))throw new IOException("组件版本未更新："+part.getString("package"));
      if(info.signingInfo==null||info.signingInfo.hasMultipleSigners()||!UpdateActivity.hex(MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray())).equals(manifest.getString("certificateSha256")))throw new IOException("已安装组件签名不匹配");
      Map<String,File> files=new HashMap<>();JSONArray assets=part.getJSONArray("assets");files.put(assets.getJSONObject(0).getString("name"),new File(info.applicationInfo.sourceDir));
      if(info.applicationInfo.splitSourceDirs!=null)for(String path:info.applicationInfo.splitSourceDirs)files.put(new File(path).getName(),new File(path));
      if(files.size()!=assets.length())throw new IOException("已安装分包数量不匹配");
      for(int j=0;j<assets.length();j++){JSONObject asset=assets.getJSONObject(j);File file=files.get(asset.getString("name"));if(file==null||file.length()!=asset.getLong("size")||!sha(file).equals(asset.getString("sha256")))throw new IOException("已安装文件与更新包不同："+asset.getString("name"));}
    }
  }
}
