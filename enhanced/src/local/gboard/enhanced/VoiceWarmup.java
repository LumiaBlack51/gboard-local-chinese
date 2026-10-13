package local.gboard.enhanced;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.view.inputmethod.EditorInfo;
/** Only a visible, non-private keyboard may pre-load the same-signature ASR model. */
final class VoiceWarmup {
  private static InputMethodService owner;private static ServiceConnection connection;private static android.speech.SpeechRecognizer speech;
  static void shown(InputMethodService service){
    EditorInfo e=service.getCurrentInputEditorInfo();if(e==null)return;int v=e.inputType&0xfff;
    if((e.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0||v==0x81||v==0x91||v==0xe1||v==0x12||!LocalSettings.voiceWarmup(service)){hide(service);return;}
    if((connection!=null||speech!=null)&&owner==service)return;
    if(service.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED||service.getPackageManager().checkSignatures(service.getPackageName(),VoiceHook.HELPER)!=PackageManager.SIGNATURE_MATCH)return;
    owner=service;
    if(android.os.Build.VERSION.SDK_INT>=34){try{
      speech=android.speech.SpeechRecognizer.createSpeechRecognizer(service,new ComponentName(VoiceHook.HELPER,"local.gboard.voice.OfflineRecognitionService"));
      speech.checkRecognitionSupport(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),service.getMainExecutor(),new android.speech.RecognitionSupportCallback(){public void onSupportResult(android.speech.RecognitionSupport result){}public void onError(int error){}});return;
    }catch(RuntimeException failure){if(speech!=null)speech.destroy();speech=null;}}
    connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){}public void onServiceDisconnected(ComponentName n){}public void onBindingDied(ComponentName n){hide(service);}};
    try{if(!service.bindService(new Intent().setComponent(new ComponentName(VoiceHook.HELPER,"local.gboard.voice.WarmupService")),connection,Context.BIND_AUTO_CREATE)){connection=null;owner=null;}}catch(RuntimeException failure){connection=null;owner=null;}
  }
  static void hide(Object service){if(owner!=service)return;if(speech!=null){speech.destroy();speech=null;}if(connection!=null)try{owner.unbindService(connection);}catch(RuntimeException ignored){}connection=null;owner=null;}
}
