#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include "LLMInference.h"

#define LOG_TAG "LLM_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Holds the native LLMInference pointer — cast to/from jlong
static LLMInference* getInference(jlong ptr) {
    return reinterpret_cast<LLMInference*>(ptr);
}

extern "C" {

// Create native inference object — returns pointer as jlong handle
JNIEXPORT jlong JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeCreate(JNIEnv* env, jobject) {
    auto* inf = new LLMInference();
    return reinterpret_cast<jlong>(inf);
}

// Load model from file descriptor
JNIEXPORT jboolean JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject,
    jlong ptr,
    jint fd, jlong offset, jlong length,
    jint nCtx, jfloat temperature, jfloat topP, jint topK,
    jint maxNewTokens, jint nThreads
) {
    auto* inf = getInference(ptr);
    if (!inf) return JNI_FALSE;

    LLMParams params;
    params.n_ctx          = nCtx;
    params.temperature    = temperature;
    params.top_p          = topP;
    params.top_k          = topK;
    params.max_new_tokens = maxNewTokens;
    params.n_threads      = nThreads;

    return inf->loadModel(fd, offset, length, params) ? JNI_TRUE : JNI_FALSE;
}

// Run inference — fires tokenCallback on each token
// tokenCallback is a Kotlin lambda passed as jobject with method "invoke(String)"
JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeRunInference(
    JNIEnv* env, jobject,
    jlong ptr,
    jobjectArray roleArray,
    jobjectArray contentArray,
    jobject callbackObj
) {
    auto* inf = getInference(ptr);
    if (!inf) return;

    int msgCount = env->GetArrayLength(roleArray);
    std::vector<ChatMessage> messages;
    messages.reserve(msgCount);

    for (int i = 0; i < msgCount; i++) {
        auto roleJStr    = (jstring) env->GetObjectArrayElement(roleArray, i);
        auto contentJStr = (jstring) env->GetObjectArrayElement(contentArray, i);
        const char* role    = env->GetStringUTFChars(roleJStr,    nullptr);
        const char* content = env->GetStringUTFChars(contentJStr, nullptr);
        messages.push_back({ std::string(role), std::string(content) });
        env->ReleaseStringUTFChars(roleJStr,    role);
        env->ReleaseStringUTFChars(contentJStr, content);
    }

    // Get callback class + method for token emission
    jclass cbClass  = env->GetObjectClass(callbackObj);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");

    inf->runInference(messages, [&](const std::string& token) {
        jstring jToken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callbackObj, onToken, jToken);
        env->DeleteLocalRef(jToken);
    });
}

// Cancel inference — sets atomic cancel flag
JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeCancel(
    JNIEnv* env, jobject, jlong ptr
) {
    auto* inf = getInference(ptr);
    if (inf) inf->cancelInference();
}

// Release model from memory
JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeRelease(
    JNIEnv* env, jobject, jlong ptr
) {
    auto* inf = getInference(ptr);
    if (inf) inf->release();
}

// Destroy native object
JNIEXPORT void JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeDestroy(
    JNIEnv* env, jobject, jlong ptr
) {
    delete getInference(ptr);
}

// Check if model is loaded
JNIEXPORT jboolean JNICALL
Java_com_assistant_feature_aichat_data_LlamaEngine_nativeIsLoaded(
    JNIEnv* env, jobject, jlong ptr
) {
    auto* inf = getInference(ptr);
    return (inf && inf->isLoaded()) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
