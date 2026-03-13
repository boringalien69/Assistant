#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "LLMInference.h"

#define LOG_TAG "LLM_JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Native handle stored as a jlong ────────────────────────────────────────
static LLMInference* fromHandle(jlong handle) {
    return reinterpret_cast<LLMInference*>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new LLMInference());
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject, jlong handle,
    jstring modelPath, jint nCtx, jint nThreads)
{
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = fromHandle(handle)->loadModel(std::string(path), nCtx, nThreads);
    env->ReleaseStringUTFChars(modelPath, path);
    return (jboolean)ok;
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeSetSystemPrompt(
    JNIEnv* env, jobject, jlong handle, jstring prompt)
{
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    fromHandle(handle)->setSystemPrompt(std::string(p));
    env->ReleaseStringUTFChars(prompt, p);
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeClearHistory(
    JNIEnv*, jobject, jlong handle)
{
    fromHandle(handle)->clearHistory();
}

// Callback object must implement: void onToken(String token), void onDone()
JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject, jlong handle,
    jstring userMessage, jobject callback, jint maxNewTokens)
{
    const char* msg = env->GetStringUTFChars(userMessage, nullptr);

    // Get callback method IDs
    jclass cbClass    = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onDone  = env->GetMethodID(cbClass, "onDone",  "()V");

    // Need a JavaVM to attach in case callback is on a different thread
    // (generate() runs synchronously on the calling thread here)
    JavaVM* jvm;
    env->GetJavaVM(&jvm);

    // Keep a global ref to callback so lambda can call it
    jobject cbGlobal = env->NewGlobalRef(callback);

    fromHandle(handle)->generate(
        std::string(msg),
        [env, cbGlobal, onToken](const std::string& token) {
            jstring jtoken = env->NewStringUTF(token.c_str());
            env->CallVoidMethod(cbGlobal, onToken, jtoken);
            env->DeleteLocalRef(jtoken);
        },
        [env, cbGlobal, onDone]() {
            env->CallVoidMethod(cbGlobal, onDone);
            env->DeleteGlobalRef(cbGlobal);
        },
        (int)maxNewTokens
    );

    env->ReleaseStringUTFChars(userMessage, msg);
}

JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeAbort(
    JNIEnv*, jobject, jlong handle)
{
    fromHandle(handle)->abort();
}

} // extern "C"
