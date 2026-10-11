#include <jni.h>
#include <memory>
#include <vector>
#include "third_party/zinnia/zinnia.h"

extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_HandwritingBridge_open(JNIEnv* env,jclass,jstring file){
  const char* path=env->GetStringUTFChars(file,nullptr);
  std::unique_ptr<zinnia::Recognizer> model(zinnia::Recognizer::create());
  bool ok=model->open(path);env->ReleaseStringUTFChars(file,path);
  return ok?reinterpret_cast<jlong>(model.release()):0;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_local_gboard_enhanced_HandwritingBridge_classify(JNIEnv* env,jclass,jlong handle,jintArray ink,jint width,jint height){
  auto* model=reinterpret_cast<zinnia::Recognizer*>(handle);
  jclass string=env->FindClass("java/lang/String");
  int length=env->GetArrayLength(ink);
  if(!model||width<1||height<1||length<3||length%3||length>60000)return env->NewObjectArray(0,string,nullptr);
  std::vector<jint> points(length);env->GetIntArrayRegion(ink,0,length,points.data());
  std::unique_ptr<zinnia::Character> character(zinnia::Character::create());
  character->set_width(width);character->set_height(height);
  for(int i=0;i<length;i+=3){if(points[i]<0||points[i]>63)return env->NewObjectArray(0,string,nullptr);character->add(points[i],points[i+1],points[i+2]);}
  std::unique_ptr<zinnia::Result> result(model->classify(*character,16));
  jobjectArray words=env->NewObjectArray(result?result->size():0,string,nullptr);
  if(result)for(size_t i=0;i<result->size();i++){jstring word=env->NewStringUTF(result->value(i));env->SetObjectArrayElement(words,i,word);env->DeleteLocalRef(word);}
  return words;
}
