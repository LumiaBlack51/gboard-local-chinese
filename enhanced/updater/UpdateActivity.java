package local.gboard.updater;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** GitHub updates and opt-in scheduled phrase exchange, without microphone or input connections. */
public final class UpdateActivity extends Activity {
  static final String REPO="LumiaBlack51/gboard-local-chinese",KEYBOARD="com.localx.android.inputmethod.latin",VOICE="local.gboard.voice",SELF="local.gboard.updater";
  static final String PROOF="local.gboard.proofread";
  static JSONArray allComponents(JSONObject manifest)throws JSONException{JSONArray result=new JSONArray();JSONArray core=manifest.getJSONArray("components"),optional=manifest.optJSONArray("optionalComponents");for(int i=0;i<core.length();i++)result.put(core.get(i));if(optional!=null)for(int i=0;i<optional.length();i++)result.put(optional.get(i));return result;}
  private TextView status,current;private Button check,install;private JSONArray components;private JSONObject release;private Thread worker;private volatile boolean closed;
  private final Map<String,String> urls=new HashMap<>();
  private SharedPreferences updates;
  private final SharedPreferences.OnSharedPreferenceChangeListener resultListener=(prefs,key)->{if("last_result".equals(key))runOnUiThread(this::showInstallResult);};
  private void message(String text){runOnUiThread(()->{if(!closed)status.setText(text);});}
  public void onCreate(Bundle state){super.onCreate(state);
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
    TextView title=new TextView(this);title.setText("Gboard 中文增强更新");title.setTextSize(24);layout.addView(title);
    current=new TextView(this);current.setTextSize(16);current.setText(InstalledRelease.versions(this));layout.addView(current);
    status=new TextView(this);status.setTextSize(17);status.setText("点击检查更新后连接 GitHub。键盘和离线语音保持无联网权限；启用词库共享后，此应用仅在插电同步时读取中文词条。");layout.addView(status);
    check=new Button(this);check.setText("检查更新");layout.addView(check);check.setOnClickListener(v->check());
    install=new Button(this);install.setText("下载并安装更新");install.setEnabled(false);layout.addView(install);install.setOnClickListener(v->beginInstall());
    Button sync=new Button(this);sync.setText("词库共享设置");layout.addView(sync);sync.setOnClickListener(v->startActivity(new Intent(this,PhraseSyncSettings.class)));
    setContentView(layout);
    updates=getSharedPreferences("updates",0);
  }
  private void background(Runnable task){check.setEnabled(false);install.setEnabled(false);worker=new Thread(()->{try{task.run();}finally{runOnUiThread(()->{if(!closed)check.setEnabled(true);});}},"ExplicitGitHubUpdate");worker.start();}
  private long installed(String pkg){try{return getPackageManager().getPackageInfo(pkg,0).getLongVersionCode();}catch(PackageManager.NameNotFoundException e){return 0;}}
  static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
  private String certificate(PackageInfo info)throws Exception{
    if(info==null||info.signingInfo==null||info.signingInfo.hasMultipleSigners())throw new IOException("安装包签名不可验证");
    return hex(MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray()));
  }
  private String signer()throws Exception{return certificate(getPackageManager().getPackageInfo(SELF,PackageManager.GET_SIGNING_CERTIFICATES));}
  private HttpURLConnection connection(String address)throws IOException{
    URL url=new URL(address);if(!url.getProtocol().equals("https"))throw new IOException("更新地址必须使用 HTTPS");
    HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","GboardLocalChinese/1.1");c.setRequestProperty("Accept","application/vnd.github+json");return c;
  }
  private JSONObject json(String address)throws Exception{
    HttpURLConnection c=connection(address);try{if(c.getResponseCode()!=200)throw new IOException("GitHub 返回 "+c.getResponseCode());
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1048576)throw new IOException("更新信息过大");bytes.write(b,0,n);}}return new JSONObject(bytes.toString("UTF-8"));
    }finally{c.disconnect();}
  }
  private void check(){background(()->{try{
    JSONObject latest=json("https://api.github.com/repos/"+REPO+"/releases/latest");
    if(latest.optBoolean("draft")||latest.optBoolean("prerelease"))throw new IOException("没有正式版本");
    urls.clear();JSONArray assets=latest.getJSONArray("assets");for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String address=a.getString("browser_download_url");if(!address.startsWith("https://github.com/"+REPO+"/releases/download/"))throw new IOException("更新来源不匹配");urls.put(a.getString("name"),address);}
    if(!urls.containsKey("update-manifest.json")){message("当前发布版本尚未提供应用内更新包。请稍后再次检查。");return;}
    release=json(urls.get("update-manifest.json"));validateManifest(release,signer(),urls.keySet());components=allComponents(release);boolean newer=false;
    StringBuilder versions=new StringBuilder("最新版本：").append(release.getString("version"));
    for(int i=0;i<components.length();i++){JSONObject part=components.getJSONObject(i);long current=installed(part.getString("package")),next=part.getLong("versionCode");if(current>next)throw new IOException("服务器版本比已安装版本旧，拒绝降级");if(current<next)newer=true;}
    if(newer){message(versions+"\n"+release.optString("notes")+"\n尚未安装这个版本。点击下载并安装更新，完成系统安装后才会生效。词库和设置保留。");runOnUiThread(()->{if(!closed){install.setText("下载并安装更新");install.setEnabled(true);}});}
    else {String state;try{InstalledRelease.verify(this,release);state="已是最新版本；全部安装文件已核验。";}catch(Exception e){state="版本号相同，但安装文件未通过核验："+e.getMessage()+"。可重新安装修复。";}message(versions+"\n"+state);runOnUiThread(()->{if(!closed){install.setText("重新安装此版本");install.setEnabled(true);}});}
  }catch(Exception e){message("检查失败："+e.getMessage()+"\n可重新检查。");}});}
  static void validateManifest(JSONObject manifest,String certificate,Set<String> names)throws Exception{
    if(manifest.getInt("format")!=1||!certificate.equalsIgnoreCase(manifest.getString("certificateSha256")))throw new IOException("版本签名身份不匹配");
    JSONArray parts=manifest.getJSONArray("components");if(parts.length()!=3)throw new IOException("更新组件不完整");Set<String> packages=new HashSet<>(),files=new HashSet<>();
    for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);String pkg=p.getString("package");if(!Arrays.asList(KEYBOARD,VOICE,SELF).contains(pkg)||!packages.add(pkg)||p.getLong("versionCode")<=0)throw new IOException("更新包身份不匹配");
      JSONArray assets=p.getJSONArray("assets");if(assets.length()!=(pkg.equals(KEYBOARD)?4:1))throw new IOException("安装组件数量错误");
      for(int j=0;j<assets.length();j++){JSONObject a=assets.getJSONObject(j);String name=a.getString("name");if(!name.matches("[a-zA-Z0-9_.-]+\\.apk")||!names.contains(name)||!files.add(name)||!a.getString("sha256").matches("[0-9a-f]{64}")||a.getLong("size")<=0||a.getLong("size")>1073741824)throw new IOException("更新文件无效");}
    }
    if(!files.containsAll(Arrays.asList("base.apk","split_brella_feature_split.apk","split_config.xxxhdpi.apk","split_tenoranimation_feature_split.apk","voice-helper.apk","updater.apk")))throw new IOException("更新文件不完整");
    JSONArray optional=manifest.optJSONArray("optionalComponents");if(optional!=null){if(optional.length()>1)throw new IOException("未知扩展组件");for(int i=0;i<optional.length();i++){JSONObject p=optional.getJSONObject(i);JSONArray assets=p.getJSONArray("assets");if(!PROOF.equals(p.getString("package"))||p.getLong("versionCode")<=0||assets.length()!=1)throw new IOException("校对组件身份无效");JSONObject asset=assets.getJSONObject(0);if(!"proofread.apk".equals(asset.getString("name"))||!names.contains("proofread.apk")||!asset.getString("sha256").matches("[0-9a-f]{64}")||asset.getLong("size")<=0||asset.getLong("size")>536870912)throw new IOException("校对组件文件无效");}}
  }
  private void beginInstall(){if(components==null)return;if(!getPackageManager().canRequestPackageInstalls()){
    message("请在系统页面允许此应用安装更新，然后返回继续。");startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+SELF)),11);return;
  }downloadAndInstall();}
  public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==11){if(getPackageManager().canRequestPackageInstalls())downloadAndInstall();else{message("安装权限未开启。可再次点击安装更新。");install.setEnabled(true);}}}
  private File download(JSONObject asset,File folder)throws Exception{
    String name=asset.getString("name");File target=new File(folder,name),temp=new File(folder,name+".part");long expected=asset.getLong("size"),received=0;if(target.isFile()&&target.length()==expected&&InstalledRelease.sha(target).equals(asset.getString("sha256")))return target;MessageDigest hash=MessageDigest.getInstance("SHA-256");HttpURLConnection c=connection(urls.get(name));
    try{if(c.getResponseCode()!=200)throw new IOException("下载返回 "+c.getResponseCode());try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){
      byte[] buffer=new byte[131072];int n;long notify=0;while((n=in.read(buffer))!=-1){if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException("下载已取消");received+=n;if(received>expected)throw new IOException("安装包大小不匹配");out.write(buffer,0,n);hash.update(buffer,0,n);long now=SystemClock.elapsedRealtime();if(now-notify>1000){message("正在下载 "+name+"："+(received*100/expected)+"%");notify=now;}}
    }if(received!=expected||!hex(hash.digest()).equals(asset.getString("sha256")))throw new IOException("安装包校验失败");if(target.exists()&&!target.delete())throw new IOException("无法替换缓存");if(!temp.renameTo(target))throw new IOException("无法保存安装包");return target;
    }finally{c.disconnect();if(temp.exists())temp.delete();}
  }
  private void verifyBase(File file,JSONObject component)throws Exception{
    PackageInfo info=getPackageManager().getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
    if(info==null||!component.getString("package").equals(info.packageName)||info.getLongVersionCode()!=component.getLong("versionCode")||!signer().equals(certificate(info)))throw new IOException("安装包版本或签名不匹配");
  }
  private void downloadAndInstall(){background(()->{PackageInstaller installer=getPackageManager().getPackageInstaller();List<Integer> sessions=new ArrayList<>();boolean committed=false;try{
    File folder=new File(getCacheDir(),"update-"+release.getString("version").replaceAll("[^a-zA-Z0-9._-]","_"));if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建更新缓存");
    List<List<File>> groups=new ArrayList<>();for(int i=0;i<components.length();i++){JSONObject part=components.getJSONObject(i);JSONArray assets=part.getJSONArray("assets");List<File> files=new ArrayList<>();for(int j=0;j<assets.length();j++)files.add(download(assets.getJSONObject(j),folder));verifyBase(files.get(0),part);groups.add(files);}
    PackageInstaller.SessionParams parentParams=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);parentParams.setMultiPackage();parentParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);int parentId=installer.createSession(parentParams);sessions.add(parentId);
    try(PackageInstaller.Session parent=installer.openSession(parentId)){
      for(int i=0;i<groups.size();i++){PackageInstaller.SessionParams p=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);p.setAppPackageName(components.getJSONObject(i).getString("package"));long size=0;for(File file:groups.get(i))size+=file.length();p.setSize(size);int id=installer.createSession(p);sessions.add(id);
        try(PackageInstaller.Session child=installer.openSession(id)){for(File file:groups.get(i))try(InputStream in=new FileInputStream(file);OutputStream out=child.openWrite(file.getName(),0,file.length())){byte[] buffer=new byte[131072];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);child.fsync(out);}}parent.addChildSessionId(id);
      }
      if(closed)throw new InterruptedIOException("安装已取消");getSharedPreferences("updates",0).edit().putString("pending_manifest",release.toString()).putInt("pending_session",parentId).remove("last_result").commit();message("下载校验完成，尚未安装。请在系统页面完成安装；随后将核验手机上全部组件。");Intent callback=new Intent(this,InstallReceiver.class).setAction("local.gboard.updater.INSTALL_RESULT");PendingIntent pending=PendingIntent.getBroadcast(this,parentId,callback,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);parent.commit(pending.getIntentSender());committed=true;
    }
  }catch(Exception e){message("更新失败："+e.getMessage()+"\n已安装版本可继续使用。");runOnUiThread(()->{if(!closed)install.setEnabled(true);});}
  finally{if(!committed)for(int id:sessions)try{installer.abandonSession(id);}catch(Exception ignored){}}});}
  public void onDestroy(){closed=true;if(worker!=null)worker.interrupt();super.onDestroy();}
  private void showInstallResult(){String last=updates.getString("last_result",null);if(last!=null&&!closed){current.setText(InstalledRelease.versions(this));status.setText(last);if(components!=null)install.setEnabled(true);}}
  public void onResume(){super.onResume();updates.registerOnSharedPreferenceChangeListener(resultListener);current.setText(InstalledRelease.versions(this));showInstallResult();}
  public void onPause(){updates.unregisterOnSharedPreferenceChangeListener(resultListener);super.onPause();}
}
