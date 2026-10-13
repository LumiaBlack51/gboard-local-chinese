package local.gboard.probe;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.UserDictionary;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A temporary local text field for checking the existing keyboard. */
public final class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private android.media.MediaPlayer speech;
    private void speechTest(){if(!getIntent().getBooleanExtra("speech_test",false))return;
        input.postDelayed(()->{if(!hasWindowFocus())return;try{
            speech=new android.media.MediaPlayer();
            speech.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
            android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
            for(android.media.AudioDeviceInfo device:audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))if(device.getType()==android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER){speech.setPreferredDevice(device);break;}
            android.content.res.AssetFileDescriptor fd=getAssets().openFd("speech-fixture.wav");
            speech.setVolume(0.3f,0.3f);speech.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());fd.close();
            speech.setOnCompletionListener(player->{player.release();speech=null;});speech.prepare();speech.start();
        }catch(Exception e){android.util.Log.e("LocalProbe","Synthetic playback failed",e);}},getIntent().getIntExtra("speech_delay_ms",6000));
    }
    @Override protected void onDestroy(){if(speech!=null){speech.release();speech=null;}super.onDestroy();}

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        speechTest();
        if (intent.hasExtra("private")) setPrivate(intent.getBooleanExtra("private", true));
        input.postDelayed(() -> {
            if (!hasWindowFocus()) return;
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.restartInput(input);
            boolean shown = manager.showSoftInput(input, InputMethodManager.SHOW_FORCED);
            android.util.Log.i("LocalProbe", "Synthetic editor focus=" + input.hasFocus() + "; keyboard request=" + shown);
        }, 400);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && input != null) input.postDelayed(() -> {
            if (!hasWindowFocus()) return;
            input.requestFocus();
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(android.view.WindowInsets.Type.ime());
        }, 200);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(margin, margin, margin, margin);

        TextView title = new TextView(this);
        title.setText("Gboard 本地兼容性测试");
        title.setTextSize(18);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(13);
        layout.addView(status);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        addButton(controls, "普通输入", () -> setPrivate(false));
        addButton(controls, "隐私输入", () -> setPrivate(true));
        addButton(controls, "清空", () -> input.setText(""));
        layout.addView(controls);

        input = new EditText(this);
        input.setHint("仅输入测试句子");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setSaveEnabled(false);
        layout.addView(input, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setText("此应用没有联网权限，不保存输入内容。隐私输入向键盘声明禁止个性化学习。");
        layout.addView(note);
        TextView dictionaryResult = new TextView(this);
        dictionaryResult.setTextSize(12);
        layout.addView(dictionaryResult);
        setContentView(layout);
        setPrivate(getIntent().getBooleanExtra("private", true));
        speechTest();
        String dictionaryAction = getIntent().getStringExtra("dictionary_action");
        if (dictionaryAction != null) {
            dictionaryResult.setText(updatePublicDictionary(dictionaryAction));
        }
    }

    private void addButton(LinearLayout target, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setOnClickListener(v -> action.run());
        target.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void setPrivate(boolean enabled) {
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | (enabled ? EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING : 0));
        status.setText(enabled ? "模式：隐私输入（禁止个性化学习）" : "模式：普通输入");
        input.requestFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.restartInput(input);
        input.post(() -> manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT));
    }

    private String updatePublicDictionary(String action) {
        SharedPreferences ledger = getSharedPreferences("created_rows", MODE_PRIVATE);
        if ("show-created".equals(action)) {
            StringBuilder records = new StringBuilder("本工具创建的公开词条记录：\n");
            for (java.util.Map.Entry<String, ?> record : ledger.getAll().entrySet()) {
                records.append(record.getKey()).append("\t").append(record.getValue()).append("\n");
            }
            return records.toString();
        }
        if ("insert-plain-yinyu".equals(action)) {
            if (ledger.contains("plain-yinyushiyi")) return "普通词条已添加。";
            ContentValues values = new ContentValues();
            values.put(UserDictionary.Words.WORD, "引喻失义");
            values.put(UserDictionary.Words.FREQUENCY, 250);
            values.put(UserDictionary.Words.LOCALE, "zh_CN");
            Uri created = getContentResolver().insert(UserDictionary.Words.CONTENT_URI, values);
            if (created == null) return "未写入普通词条。";
            ledger.edit().putString("plain-yinyushiyi", created.toString()).commit();
            return "已添加无快捷码的公开测试词：引喻失义。";
        }
        if ("remove-plain-yinyu".equals(action)) {
            String created = ledger.getString("plain-yinyushiyi", null);
            int removed = created == null ? 0 : getContentResolver().delete(Uri.parse(created), null, null);
            ledger.edit().remove("plain-yinyushiyi").commit();
            return "撤销普通测试词条：" + removed;
        }
        String[][] words = {
            {"引喻失义", "yinyushiyi"}, {"陟罚臧否", "zhifazangpi"},
            {"拾遗补阙", "shiyibuque"}, {"招贤进能", "zhaoxianjinneng"},
            {"与时俯仰", "yushifuyang"}
        };
        int changed = 0;
        try {
            for (String[] word : words) {
                String existingUri = ledger.getString(word[1], null);
                if ("remove-inserted".equals(action)) {
                    if (existingUri != null) {
                        changed += getContentResolver().delete(Uri.parse(existingUri), null, null);
                        ledger.edit().remove(word[1]).commit();
                    }
                    continue;
                }
                if (!"insert-benchmark".equals(action)
                        && !("insert-yinyu".equals(action) && "yinyushiyi".equals(word[1]))) {
                    continue;
                }
                if (existingUri != null) continue;
                ContentValues values = new ContentValues();
                values.put(UserDictionary.Words.WORD, word[0]);
                values.put(UserDictionary.Words.SHORTCUT, word[1]);
                values.put(UserDictionary.Words.FREQUENCY, 250);
                values.put(UserDictionary.Words.LOCALE, "zh_CN");
                Uri created = getContentResolver().insert(UserDictionary.Words.CONTENT_URI, values);
                if (created == null) {
                    return "未写入：Android 未授权词库访问。";
                }
                ledger.edit().putString(word[1], created.toString()).commit();
                changed++;
            }
            return action.equals("remove-inserted")
                    ? "撤销本工具创建的词条：" + changed
                    : "仅添加公开测试词条：" + changed + "（不读取已有词库）";
        } catch (RuntimeException failure) {
            return "词库操作失败：" + failure.getClass().getSimpleName();
        }
    }
}
