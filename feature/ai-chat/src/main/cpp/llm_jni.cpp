#include <jni.h>
#include <string>
#include <android/log.h>
#include "LLMInference.h"

#define TAG "LLM_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Helper to call a Java void method
static void callJavaMethod(JNIEnv* env, jobject obj, jmethodID mid, ...) {
    va_list args;
    va_start(args, mid);
    env->CallVoidMethodV(obj, mid, args);
    va_end(args);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeCreate(JNIEnv*, jobject) {
    return (jlong) llm_create();
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    llm_destroy(reinterpret_cast<LLMContext*>(handle));
}

JNIEXPORT jboolean JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeLoadModel(
        JNIEnv* env, jobject, jlong handle,
        jstring modelPath, jint nCtx, jint nThreads)
{
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = llm_load_model(reinterpret_cast<LLMContext*>(handle), path, nCtx, nThreads);
    env->ReleaseStringUTFChars(modelPath, path);
    return (jboolean) ok;
}

JNIEXPORT jboolean JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeIsLoaded(JNIEnv*, jobject, jlong handle) {
    return (jboolean) llm_is_loaded(reinterpret_cast<LLMContext*>(handle));
}

JNIEXPORT jstring JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeGetModelPath(JNIEnv* env, jobject, jlong handle) {
    const char* path = llm_get_model_path(reinterpret_cast<LLMContext*>(handle));
    if (!path) return nullptr;
    return env->NewStringUTF(path);
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeSetSystemPrompt(
        JNIEnv* env, jobject, jlong handle, jstring prompt)
{
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    llm_set_system_prompt(reinterpret_cast<LLMContext*>(handle), p);
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeClearHistory(JNIEnv*, jobject, jlong handle) {
    llm_clear_history(reinterpret_cast<LLMContext*>(handle));
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeRunInference(
        JNIEnv* env, jobject, jlong handle,
        jobjectArray roles, jobjectArray contents,
        jobject callback, jint maxNewTokens)
{
    // Cache callback method IDs
    jclass cbClass  = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone  = env->GetMethodID(cbClass, "onDone",  "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    int n = env->GetArrayLength(roles);
    std::vector<std::string> role_strs(n), content_strs(n);
    std::vector<const char*> role_ptrs(n), content_ptrs(n);

    for (int i = 0; i < n; i++) {
        auto r = (jstring) env->GetObjectArrayElement(roles,    i);
        auto c = (jstring) env->GetObjectArrayElement(contents, i);
        role_strs[i]    = env->GetStringUTFChars(r, nullptr);
        content_strs[i] = env->GetStringUTFChars(c, nullptr);
        env->ReleaseStringUTFChars(r, role_strs[i].c_str());
        env->ReleaseStringUTFChars(c, content_strs[i].c_str());
        role_ptrs[i]    = role_strs[i].c_str();
        content_ptrs[i] = content_strs[i].c_str();
    }

    // We need a global ref to callback since lambdas are called from same thread here
    jobject cbGlobal = env->NewGlobalRef(callback);

    llm_run_inference(
        reinterpret_cast<LLMContext*>(handle),
        role_ptrs.data(), content_ptrs.data(), n,
        (int) maxNewTokens,
        [env, cbGlobal, onToken](const char* token) {
            jstring jtok = env->NewStringUTF(token);
            env->CallVoidMethod(cbGlobal, onToken, jtok);
            env->DeleteLocalRef(jtok);
        },
        [env, cbGlobal, onDone]() {
            env->CallVoidMethod(cbGlobal, onDone);
        },
        [env, cbGlobal, onError](const char* msg) {
            jstring jmsg = env->NewStringUTF(msg);
            env->CallVoidMethod(cbGlobal, onError, jmsg);
            env->DeleteLocalRef(jmsg);
        }
    );

    env->DeleteGlobalRef(cbGlobal);
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeAbort(JNIEnv*, jobject, jlong handle) {
    llm_abort(reinterpret_cast<LLMContext*>(handle));
}

} // extern "C"
