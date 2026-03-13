#include "LLMInference.h"
#include <android/log.h>
#include <cstring>
#include <stdexcept>

#define LOG_TAG "LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

LLMInference::LLMInference() {
    llama_backend_init();
}

LLMInference::~LLMInference() {
    freeModel();
    llama_backend_free();
}

void LLMInference::freeModel() {
    if (_sampler) { llama_sampler_free(_sampler); _sampler = nullptr; }
    if (_ctx)     { llama_free(_ctx);              _ctx     = nullptr; }
    if (_model)   { llama_free_model(_model);      _model   = nullptr; }
}

bool LLMInference::loadModel(const std::string& modelPath, int nCtx, int nThreads) {
    freeModel();
    _messages.clear();
    _msgStorage.clear();

    // ── Model ──────────────────────────────────────────────────────────────
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU only

    _model = llama_load_model_from_file(modelPath.c_str(), mparams);
    if (!_model) {
        LOGE("Failed to load model: %s", modelPath.c_str());
        return false;
    }

    // ── Context ────────────────────────────────────────────────────────────
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = (uint32_t)nCtx;
    cparams.n_batch    = 512;
    cparams.n_ubatch   = 512;
    cparams.n_threads  = (uint32_t)nThreads;
    cparams.flash_attn = false;

    _ctx = llama_new_context_with_model(_model, cparams);
    if (!_ctx) {
        LOGE("Failed to create context");
        llama_free_model(_model);
        _model = nullptr;
        return false;
    }

    // ── Sampler chain ──────────────────────────────────────────────────────
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    _sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model loaded: %s  ctx=%d  threads=%d", modelPath.c_str(), nCtx, nThreads);
    return true;
}

void LLMInference::setSystemPrompt(const std::string& systemPrompt) {
    _systemPrompt = systemPrompt;
}

void LLMInference::addMessage(const std::string& role, const std::string& content) {
    _msgStorage.push_back(role);
    const char* rolePtr = _msgStorage.back().c_str();
    _msgStorage.push_back(content);
    const char* contentPtr = _msgStorage.back().c_str();
    _messages.push_back({rolePtr, contentPtr});
}

void LLMInference::clearHistory() {
    _messages.clear();
    _msgStorage.clear();
    if (_ctx) llama_kv_cache_clear(_ctx);
}

std::string LLMInference::applyTemplate() {
    // Build full message list: system prompt + history
    std::vector<std::string> storage;
    std::vector<llama_chat_message> msgs;

    if (!_systemPrompt.empty()) {
        storage.push_back("system");
        storage.push_back(_systemPrompt);
        msgs.push_back({storage[storage.size()-2].c_str(), storage.back().c_str()});
    }
    for (auto& m : _messages) {
        msgs.push_back(m);
    }

    // Apply chat template embedded in the model
    std::vector<char> buf(4096);
    int32_t n = llama_chat_apply_template(
        _model,
        nullptr,      // use template from model
        msgs.data(),
        msgs.size(),
        true,         // add_ass — append assistant turn prefix
        buf.data(),
        (int32_t)buf.size()
    );
    if (n > (int32_t)buf.size()) {
        buf.resize(n + 1);
        llama_chat_apply_template(
            _model, nullptr,
            msgs.data(), msgs.size(),
            true,
            buf.data(), (int32_t)buf.size()
        );
    }
    return std::string(buf.data(), n > 0 ? n : 0);
}

bool LLMInference::generate(
    const std::string& userMessage,
    std::function<void(const std::string&)> tokenCallback,
    std::function<void()> doneCallback,
    int maxNewTokens)
{
    if (!_model || !_ctx || !_sampler) {
        LOGE("generate() called but model not loaded");
        return false;
    }
    _abortFlag = false;

    // Add user message then apply template
    addMessage("user", userMessage);
    std::string prompt = applyTemplate();

    // Tokenise
    const int nPromptTokens = -llama_tokenize(
        _model, prompt.c_str(), (int32_t)prompt.size(),
        nullptr, 0, true, true);

    std::vector<llama_token> tokens(nPromptTokens);
    if (llama_tokenize(_model, prompt.c_str(), (int32_t)prompt.size(),
                       tokens.data(), (int32_t)tokens.size(), true, true) < 0) {
        LOGE("Tokenization failed");
        return false;
    }

    // Decode prompt in one batch
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(_ctx, batch) != 0) {
        LOGE("Prompt decode failed");
        return false;
    }

    // Generation loop
    std::string response;
    char piece[256];

    for (int i = 0; i < maxNewTokens && !_abortFlag; i++) {
        llama_token token = llama_sampler_sample(_sampler, _ctx, -1);

        if (llama_token_is_eog(_model, token)) break;

        int32_t n = llama_token_to_piece(_model, token, piece, sizeof(piece), 0, true);
        if (n < 0) break;

        std::string tokenStr(piece, n);
        response += tokenStr;
        tokenCallback(tokenStr);

        // Decode next token
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(_ctx, next) != 0) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }

    // Add assistant response to history
    addMessage("assistant", response);

    llama_sampler_reset(_sampler);
    doneCallback();
    return true;
}

void LLMInference::abort() {
    _abortFlag = true;
}
