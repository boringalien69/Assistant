#include "LLMInference.h"
#include "llama.h"
#include <android/log.h>
#include <thread>
#include <sstream>
#include <cstring>

#define LOG_TAG "LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

LLMInference::LLMInference() {
    llama_backend_init();
}

LLMInference::~LLMInference() {
    release();
    llama_backend_free();
}

int LLMInference::autoThreadCount() {
    int cores = (int)std::thread::hardware_concurrency();
    if (cores <= 0) return 4;
    return std::max(1, cores / 2);
}

bool LLMInference::loadModel(int fd, long offset, long length, const LLMParams& params) {
    release(); // ensure clean state

    params_ = params;
    if (params_.n_threads == 0) {
        params_.n_threads = autoThreadCount();
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only by default — Vulkan support handled by ggml backend

    // Load via file descriptor — no file copy needed
    model_ = llama_model_load_from_fd(fd, offset, length, mparams);
    if (!model_) {
        LOGE("Failed to load model from fd");
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = params_.n_ctx;
    cparams.n_threads  = params_.n_threads;
    cparams.n_threads_batch = params_.n_threads;

    ctx_ = llama_new_context_with_model(model_, cparams);
    if (!ctx_) {
        LOGE("Failed to create llama context");
        llama_model_free(model_);
        model_ = nullptr;
        return false;
    }

    LOGI("Model loaded. ctx=%d tokens, threads=%d", params_.n_ctx, params_.n_threads);
    return true;
}

bool LLMInference::runInference(
    const std::vector<ChatMessage>& messages,
    std::function<void(const std::string&)> tokenCallback
) {
    if (!ctx_ || !model_) {
        LOGE("runInference called with no model loaded");
        return false;
    }

    cancelFlag_.store(false);

    // Build prompt from message history using model's chat template
    std::string prompt = applyTemplate(messages);

    // Tokenize the prompt
    const int n_prompt_tokens = -llama_tokenize(model_, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(model_, prompt.c_str(), prompt.size(),
                       prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Tokenization failed");
        return false;
    }

    // Clear KV cache for fresh start (session management handled in Kotlin layer)
    llama_kv_cache_clear(ctx_);

    // Decode the prompt
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), (int)prompt_tokens.size());
    if (llama_decode(ctx_, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        return false;
    }

    // Sample params
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(params_.top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(params_.top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(params_.temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int n_decoded = 0;
    const int max_tokens = params_.max_new_tokens;

    while (n_decoded < max_tokens) {
        if (cancelFlag_.load()) {
            LOGI("Inference cancelled at token %d", n_decoded);
            break;
        }

        llama_token token_id = llama_sampler_sample(sampler, ctx_, -1);

        if (llama_token_is_eog(model_, token_id)) {
            break; // end of generation
        }

        // Decode token to string
        char buf[256];
        int n = llama_token_to_piece(model_, token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string piece(buf, n);
            tokenCallback(piece);
        }

        // Decode next token
        llama_batch next_batch = llama_batch_get_one(&token_id, 1);
        if (llama_decode(ctx_, next_batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }

        n_decoded++;
    }

    llama_sampler_free(sampler);
    LOGI("Inference complete. %d tokens generated.", n_decoded);
    return true;
}

void LLMInference::cancelInference() {
    cancelFlag_.store(true);
}

void LLMInference::release() {
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_model_free(model_);
        model_ = nullptr;
    }
}

bool LLMInference::isLoaded() const {
    return ctx_ != nullptr && model_ != nullptr;
}

std::string LLMInference::applyTemplate(const std::vector<ChatMessage>& messages) {
    // Use llama.cpp's built-in chat template if available
    // Falls back to a sensible default (Mistral/ChatML compatible)
    if (!model_) return "";

    // Build messages array for llama_chat_apply_template
    std::vector<llama_chat_message> chat_msgs;
    chat_msgs.reserve(messages.size());
    for (const auto& msg : messages) {
        chat_msgs.push_back({ msg.role.c_str(), msg.content.c_str() });
    }

    // Query required buffer size
    int required = llama_chat_apply_template(
        llama_model_get_template(model_),
        chat_msgs.data(), chat_msgs.size(),
        true, nullptr, 0
    );

    if (required < 0) {
        // Fallback: manual Mistral-style template
        std::ostringstream ss;
        for (const auto& msg : messages) {
            if (msg.role == "system") {
                ss << "[INST] <<SYS>>\n" << msg.content << "\n<</SYS>>\n\n";
            } else if (msg.role == "user") {
                ss << msg.content << " [/INST]";
            } else if (msg.role == "assistant") {
                ss << msg.content << "</s><s>[INST] ";
            }
        }
        return ss.str();
    }

    std::string result(required, '\0');
    llama_chat_apply_template(
        llama_model_get_template(model_),
        chat_msgs.data(), chat_msgs.size(),
        true, result.data(), required
    );
    return result;
}
