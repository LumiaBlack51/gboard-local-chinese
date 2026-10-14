#include <jni.h>
#include <string>
#include <cstring>
#include <vector>
#include "sherpa_c_api.h"
static std::string utf(JNIEnv* e,jstring s){const char* p=e->GetStringUTFChars(s,nullptr);std::string r(p);e->ReleaseStringUTFChars(s,p);return r;}
static jstring text(JNIEnv* e,const char* s){int n=s?strlen(s):0;jbyteArray b=e->NewByteArray(n);e->SetByteArrayRegion(b,0,n,(const jbyte*)s);jclass c=e->FindClass("java/lang/String");jstring r=(jstring)e->NewObject(c,e->GetMethodID(c,"<init>","([BLjava/lang/String;)V"),b,e->NewStringUTF("UTF-8"));e->DeleteLocalRef(b);e->DeleteLocalRef(c);return r;}
struct Run {const SherpaOnnxOnlineRecognizer* model;const SherpaOnnxOnlineStream* stream;bool finished=false;};
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_VoiceBridge_punctuationModel(JNIEnv* e,jclass,jstring path){
  std::string file=utf(e,path);SherpaOnnxOfflinePunctuationConfig c{};
  c.model.ct_transformer=file.c_str();c.model.num_threads=2;c.model.provider="cpu";
  return (jlong)SherpaOnnxCreateOfflinePunctuation(&c);
}
extern "C" JNIEXPORT jstring JNICALL Java_local_gboard_enhanced_VoiceBridge_addPunctuation(JNIEnv* e,jclass,jlong model,jstring input){
  if(!model)return input;std::string source=utf(e,input);
  const char* output=SherpaOfflinePunctuationAddPunct((const SherpaOnnxOfflinePunctuation*)model,source.c_str());
  if(!output)return input;jstring result=text(e,output);SherpaOfflinePunctuationFreeText(output);return result;
}
static void decode(Run* r){while(SherpaOnnxIsOnlineStreamReady(r->model,r->stream))SherpaOnnxDecodeOnlineStream(r->model,r->stream);}
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_VoiceBridge_model(JNIEnv* e,jclass,jstring path){
  std::string dir=utf(e,path),encoder=dir+"/encoder.int8.onnx",decoder=dir+"/decoder.onnx",joiner=dir+"/joiner.int8.onnx",tokens=dir+"/tokens.txt";
  SherpaOnnxOnlineRecognizerConfig c{};c.feat_config.sample_rate=16000;c.feat_config.feature_dim=80;
  c.model_config.transducer={encoder.c_str(),decoder.c_str(),joiner.c_str()};c.model_config.tokens=tokens.c_str();c.model_config.num_threads=2;c.model_config.provider="cpu";c.model_config.model_type="zipformer";
  c.decoding_method="greedy_search";c.enable_endpoint=1;c.rule1_min_trailing_silence=2.4f;c.rule2_min_trailing_silence=1.2f;c.rule3_min_utterance_length=30;
  return (jlong)SherpaOnnxCreateOnlineRecognizer(&c);
}
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_VoiceBridge_create(JNIEnv*,jclass,jlong model){auto* m=(const SherpaOnnxOnlineRecognizer*)model;if(!m)return 0;auto* s=SherpaOnnxCreateOnlineStream(m);return s?(jlong)new Run{m,s}:0;}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_VoiceBridge_accept(JNIEnv* e,jclass,jlong rec,jshortArray samples,jint n){
  auto* r=(Run*)rec;if(!r||r->finished||n<0||n>e->GetArrayLength(samples))return false;
  jshort* s=e->GetShortArrayElements(samples,nullptr);std::vector<float> f(n);for(int i=0;i<n;i++)f[i]=s[i]/32768.f;e->ReleaseShortArrayElements(samples,s,JNI_ABORT);
  SherpaOnnxOnlineStreamAcceptWaveform(r->stream,16000,f.data(),n);decode(r);return SherpaOnnxOnlineStreamIsEndpoint(r->model,r->stream)!=0;
}
extern "C" JNIEXPORT jstring JNICALL Java_local_gboard_enhanced_VoiceBridge_result(JNIEnv* e,jclass,jlong rec,jint mode){
  auto* r=(Run*)rec;if(!r)return text(e,"{}");
  if(mode==2&&!r->finished){std::vector<float> tail(8000,0.f);SherpaOnnxOnlineStreamAcceptWaveform(r->stream,16000,tail.data(),tail.size());SherpaOnnxOnlineStreamInputFinished(r->stream);r->finished=true;decode(r);}
  const char* json=SherpaOnnxGetOnlineStreamResultAsJson(r->model,r->stream);jstring result=text(e,json);SherpaOnnxDestroyOnlineStreamResultJson(json);
  if(mode==1)SherpaOnnxOnlineStreamReset(r->model,r->stream);return result;
}
extern "C" JNIEXPORT void JNICALL Java_local_gboard_enhanced_VoiceBridge_destroy(JNIEnv*,jclass,jlong rec){auto* r=(Run*)rec;if(r){SherpaOnnxDestroyOnlineStream(r->stream);delete r;}}
