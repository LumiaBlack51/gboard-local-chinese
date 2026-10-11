#include <jni.h>
#include <string>
#include <cstring>
#include "vosk_api.h"
static std::string utf(JNIEnv* e,jstring s){const char* p=e->GetStringUTFChars(s,nullptr);std::string r(p);e->ReleaseStringUTFChars(s,p);return r;}
static jstring text(JNIEnv* e,const char* s){int n=s?strlen(s):0;jbyteArray b=e->NewByteArray(n);e->SetByteArrayRegion(b,0,n,(const jbyte*)s);jclass c=e->FindClass("java/lang/String");jstring r=(jstring)e->NewObject(c,e->GetMethodID(c,"<init>","([BLjava/lang/String;)V"),b,e->NewStringUTF("UTF-8"));e->DeleteLocalRef(b);e->DeleteLocalRef(c);return r;}
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_VoiceBridge_model(JNIEnv* e,jclass,jstring path){vosk_set_log_level(-1);return (jlong)vosk_model_new(utf(e,path).c_str());}
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_VoiceBridge_create(JNIEnv*,jclass,jlong model){return (jlong)vosk_recognizer_new((VoskModel*)model,16000.f);}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_VoiceBridge_accept(JNIEnv* e,jclass,jlong rec,jshortArray samples,jint n){if(n<0||n>e->GetArrayLength(samples))return false;jshort* s=e->GetShortArrayElements(samples,nullptr);int result=vosk_recognizer_accept_waveform_s((VoskRecognizer*)rec,s,n);e->ReleaseShortArrayElements(samples,s,JNI_ABORT);return result==1;}
extern "C" JNIEXPORT jstring JNICALL Java_local_gboard_enhanced_VoiceBridge_result(JNIEnv* e,jclass,jlong rec,jint mode){return text(e,mode==2?vosk_recognizer_final_result((VoskRecognizer*)rec):mode==1?vosk_recognizer_result((VoskRecognizer*)rec):vosk_recognizer_partial_result((VoskRecognizer*)rec));}
extern "C" JNIEXPORT void JNICALL Java_local_gboard_enhanced_VoiceBridge_destroy(JNIEnv*,jclass,jlong rec){vosk_recognizer_free((VoskRecognizer*)rec);}
