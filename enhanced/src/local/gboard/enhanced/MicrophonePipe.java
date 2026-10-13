package local.gboard.enhanced;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;

/** Capture under the visible IME's UID. Only an in-memory PCM pipe crosses to ASR. */
final class MicrophonePipe {
  interface Failure {void failed(String message);}
  final ParcelFileDescriptor reader;
  private final ParcelFileDescriptor writer;
  private final ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<>(1200);
  private volatile boolean stopping,aborted,finished;
  private AudioRecord recorder;
  MicrophonePipe(Context context,Failure failure)throws IOException {
    ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();reader=pipe[0];writer=pipe[1];
    try {
      recorder=new AudioRecord.Builder().setContext(context).setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
        .setBufferSizeInBytes(Math.max(32000,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT))).build();
      recorder.startRecording();
      if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("无法打开麦克风");
    } catch(Throwable e){if(recorder!=null)recorder.release();close(reader);close(writer);throw new IOException("无法打开麦克风，请检查权限",e);}
    new Thread(()->{
      try {byte[] buffer=new byte[3200];long end=SystemClock.elapsedRealtime()+120000;
        while(!stopping&&SystemClock.elapsedRealtime()<end){int n=recorder.read(buffer,0,buffer.length);
          if(n<0){if(stopping)break;throw new IOException("麦克风读取失败");}
          AudioRecordingConfiguration config=recorder.getActiveRecordingConfiguration();
          if(config!=null&&config.isClientSilenced())throw new IOException("系统已将麦克风静音，请检查麦克风开关或其他录音应用");
          if(n>0&&!queue.offer(Arrays.copyOf(buffer,n)))throw new IOException("识别处理过慢，请分段录音");
        }
      }catch(Exception e){if(!stopping)new Handler(Looper.getMainLooper()).post(()->failure.failed(e.getMessage()));}
      finally {synchronized(this){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}finished=true;}
    },"IME-Microphone").start();
    new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(writer)){
      while(!aborted&&(!finished||!queue.isEmpty())){byte[] bytes=queue.poll(100,TimeUnit.MILLISECONDS);if(bytes!=null)out.write(bytes);}
    }catch(Exception e){stop(); /* Normal speech endpoints also close the read end. */}
      finally{queue.clear();}
    },"IME-AudioPipe").start();
  }
  synchronized void stop(){stopping=true;if(recorder!=null)try{recorder.stop();}catch(Exception ignored){}}
  void accepted(){close(reader);}
  void cancel(){aborted=true;stop();close(reader);close(writer);queue.clear();}
  private static void close(ParcelFileDescriptor fd){try{fd.close();}catch(IOException ignored){}}
}
