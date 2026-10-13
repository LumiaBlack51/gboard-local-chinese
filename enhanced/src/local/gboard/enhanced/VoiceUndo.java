package local.gboard.enhanced;
import android.inputmethodservice.InputMethodService;import android.os.*;import android.view.*;import android.view.inputmethod.*;import android.widget.*;
/** Ephemeral undo. A changed editor, selection, cursor or suffix makes the action a no-op. */
final class VoiceUndo {
  static PopupWindow popup;static final Handler main=new Handler(Looper.getMainLooper());static final Runnable expire=VoiceUndo::clear;
  static void clear(){main.removeCallbacks(expire);if(popup!=null){popup.dismiss();popup=null;}}
  static void offer(InputMethodService ime,InputConnection connection,String original,String corrected){
    clear();if(original.equals(corrected))return;
    ExtractedText before=connection.getExtractedText(new ExtractedTextRequest(),0);
    if(before==null||before.selectionStart!=before.selectionEnd)return;final int cursor=before.startOffset+before.selectionEnd;
    Button undo=new Button(ime);undo.setText("恢复识别原文");undo.setTextSize(14);undo.setContentDescription("撤销本次语音纠错，恢复识别原文");
    popup=new PopupWindow(undo,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,false);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    undo.setOnClickListener(v->{clear();try{
      InputConnection now=ime.getCurrentInputConnection();if(now!=connection)return;ExtractedText current=now.getExtractedText(new ExtractedTextRequest(),0);CharSequence selected=now.getSelectedText(0),suffix=now.getTextBeforeCursor(corrected.length(),0);
      if(current==null||current.selectionStart!=current.selectionEnd||current.startOffset+current.selectionEnd!=cursor||(selected!=null&&selected.length()>0)||suffix==null||!corrected.contentEquals(suffix))return;
      now.beginBatchEdit();try{if(now.deleteSurroundingText(corrected.length(),0))now.commitText(original,1);}finally{now.endBatchEdit();}
    }catch(RuntimeException ignored){}});
    try{popup.showAtLocation(ime.getWindow().getWindow().getDecorView(),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,(int)(300*ime.getResources().getDisplayMetrics().density));main.postDelayed(expire,8000);}catch(RuntimeException e){clear();}
  }
}
