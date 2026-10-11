#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <cstring>
#include "rime_api.h"
static RimeApi* api=nullptr;
static std::string sharedDir,userDir;
static std::string utf(JNIEnv* env,jstring value) {
  if(!value) return "";
  const char* s=env->GetStringUTFChars(value,nullptr);
  std::string result(s); env->ReleaseStringUTFChars(value,s); return result;
}
static jstring str(JNIEnv* e,const char* text) {
  // Rime emits standard UTF-8, including characters beyond the BMP.
  jbyteArray bytes=e->NewByteArray(strlen(text?text:""));
  e->SetByteArrayRegion(bytes,0,e->GetArrayLength(bytes),(const jbyte*)(text?text:""));
  jclass cls=e->FindClass("java/lang/String");
  jobject result=e->NewObject(cls,e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V"),bytes,e->NewStringUTF("UTF-8"));
  e->DeleteLocalRef(bytes);e->DeleteLocalRef(cls);return (jstring)result;
}
extern "C" JNIEXPORT jstring JNICALL Java_local_gboard_enhanced_RimeBridge_init(JNIEnv* env,jclass,jstring shared,jstring user) {
  if(api) return str(env,api->get_version());
  void* lib=dlopen("librime.so",RTLD_NOW|RTLD_GLOBAL);
  if(!lib) return str(env,dlerror());
  auto get=(RimeApi*(*)())dlsym(lib,"rime_get_api");
  if(!get) return str(env,"Missing rime_get_api");
  api=get(); sharedDir=utf(env,shared);userDir=utf(env,user);
  RIME_STRUCT(RimeTraits,traits);
  traits.shared_data_dir=sharedDir.c_str();traits.user_data_dir=userDir.c_str();
  traits.distribution_name="Gboard Local Chinese";
  traits.distribution_code_name="gboard_local";traits.distribution_version="1.0";
  traits.app_name="rime.gboard_local";traits.min_log_level=2;traits.log_dir="";
  api->setup(&traits);api->initialize(&traits);
  api->start_maintenance(True);api->join_maintenance_thread();
  return str(env,api->get_version());
}
extern "C" JNIEXPORT jlong JNICALL Java_local_gboard_enhanced_RimeBridge_create(JNIEnv*,jclass,jboolean incognito) {
  if(!api) return 0;auto id=api->create_session();
  if(!api->select_schema(id,incognito?"local_pinyin_private":"local_pinyin")){api->destroy_session(id);return 0;}
  api->set_option(id,"incognito",incognito);api->set_option(id,"ascii_mode",False);return id;
}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_RimeBridge_key(JNIEnv*,jclass,jlong id,jint code) {return api&&api->process_key(id,code,0);}
extern "C" JNIEXPORT void JNICALL Java_local_gboard_enhanced_RimeBridge_clear(JNIEnv*,jclass,jlong id) {if(api)api->clear_composition(id);}
extern "C" JNIEXPORT void JNICALL Java_local_gboard_enhanced_RimeBridge_destroy(JNIEnv*,jclass,jlong id) {if(api)api->destroy_session(id);}
extern "C" JNIEXPORT void JNICALL Java_local_gboard_enhanced_RimeBridge_caret(JNIEnv*,jclass,jlong id,jint pos) {if(api)api->set_caret_pos(id,pos);}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_RimeBridge_select(JNIEnv*,jclass,jlong id,jint index) {return api&&api->select_candidate(id,index);}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_RimeBridge_forget(JNIEnv*,jclass,jlong id,jint index) {return api&&RIME_API_AVAILABLE(api,delete_candidate)&&api->delete_candidate(id,index);}
extern "C" JNIEXPORT jboolean JNICALL Java_local_gboard_enhanced_RimeBridge_sync(JNIEnv*,jclass) {if(!api)return false;Bool result=api->sync_user_data();api->join_maintenance_thread();return result;}
extern "C" JNIEXPORT jobjectArray JNICALL Java_local_gboard_enhanced_RimeBridge_state(JNIEnv* env,jclass,jlong id) {
  std::vector<std::string> values(4); // raw, caret, commit, formatted preedit, candidates...
  if(api) {
    const char* input=api->get_input(id);values[0]=input?input:"";
    values[1]=std::to_string(api->get_caret_pos(id));
    RIME_STRUCT(RimeCommit,commit);
    if(api->get_commit(id,&commit)){values[2]=commit.text?commit.text:"";api->free_commit(&commit);}
    RIME_STRUCT(RimeContext,context);
    if(api->get_context(id,&context)){
      values[3]=context.composition.preedit?context.composition.preedit:"";
      api->free_context(&context);
    }
    RimeCandidateListIterator it={0};
    if(api->candidate_list_begin(id,&it)){
      while(values.size()<104&&api->candidate_list_next(&it))values.push_back(it.candidate.text?it.candidate.text:"");
      api->candidate_list_end(&it);
    }
  }
  jobjectArray result=env->NewObjectArray(values.size(),env->FindClass("java/lang/String"),nullptr);
  for(size_t i=0;i<values.size();++i){auto s=str(env,values[i].c_str());env->SetObjectArrayElement(result,i,s);env->DeleteLocalRef(s);}
  return result;
}
