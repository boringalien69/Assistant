#include "LLMInference.h"
#include <android/log.h>
#include <cstring>

#define TAG "LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

LLMInference::LLMInference() {
    llama_backend_init();
}

LLMInference::~LLMInference() {
    freeAll();
    llama_backend_free();
}

void LLMInference::freeAll() {
    if (_sampler) { llama_sampler_free(_sampler); _sampler = nullptr; }
    if (_ctx)     { llama_free(_ctx);              _ctx     = nullptr; }
    if (_model)   { llama_free_model(_model);      _model   = nullptr; }
}

bool LLMInference::loadModel(const std::string& modelPath, int nCtx, int nThreads) {
    freeAll();
    _history.clear();

    // ── Load model ───────────────────────────────────────────────────────────
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    _model = llama_load_model_from_file(modelPath.c_str(), mparams);
    if (!_model) {
        LOGE("Failed to load model from: %s", modelPath.c_str());
        return false;
    }

    // ── Create context ───────────────────────────────────────────────────────
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = static_cast<uint32_t>(nCtx);
    cparams.n_batch   = 512;
    cparams.n_ubatch  = 512;
    cparams.n_threads = static_cast<uint32_t>(nThreads);

    _ctx = llama_new_context_with_model(_model, cparams);
    if (!_ctx) {
        LOGE("Failed to create context");
        llama_free_model(_model);
        _model = nullptr;
        return false;
    }

    // ── Build sampler chain ──────────────────────────────────────────────────
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    _sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model loaded OK — ctx=%d threads=%d", nCtx, nThreads);
    return true;
}

void LLMInference::setSystemPrompt(const std::string& p) { _systemPrompt = p; }

void LLMInference::addMessage(const std::string& role, const std::string& content) {
    _history.push_back({role, content});
}

void LLMInference::clearHistory() {
    _history.clear();
    if (_ctx) llama_kv_cache_clear(_ctx);
}

std::string LLMInference::buildPrompt(bool addAssistantTurn) {
    // Build llama_chat_message array from our stored history
    std::vector<llama_chat_message> msgs;

    if (!_systemPrompt.empty()) {
        msgs.push_back({"system", _systemPrompt.c_str()});
    }
    for (const auto& m : _history) {
        msgs.push_back({m.role.c_str(), m.content.c_str()});
    }

    // Size the buffer generously
    std::vector<char> buf(16384);
    int32_t n = llama_chat_apply_template(
        _model,
        nullptr,           // use template baked into model
        msgs.data(),
        (int32_t)msgs.size(),
        addAssistantTurn,  // append assistant turn prefix when generating
        buf.data(),
        (int32_t)buf.size()
    );

    if (n < 0) {
        LOGE("llama_chat_apply_template failed — using raw last message");
        return _history.empty() ? "" : _history.back().content;
    }

    if (n > (int32_t)buf.size()) {
        buf.resize(static_cast<size_t>(n) + 1);
        llama_chat_apply_template(
            _model, nullptr,
            msgs.data(), (int32_t)msgs.size(),
            addAssistantTurn,
            buf.data(), (int32_t)buf.size()
        );
    }

    return std::string(buf.data(), static_cast<size_t>(n));
}

bool LLMInference::generate(
    const std::string& userMessage,
    std::function<void(const std::string&)> tokenCallback,
    std::function<void()> doneCallback,
    int maxNewTokens)
{
    if (!_model || !_ctx || !_sampler) {
        LOGE("generate() called with no model loaded");
        return false;
    }

    _abortFlag = false;

    // Add user message, build full prompt
    addMessage("user", userMessage);
    std::string prompt = buildPrompt(/*addAssistantTurn=*/true);

    // Tokenise
    int nPrompt = -llama_tokenize(
        _model, prompt.c_str(), (int32_t)prompt.size(),
        nullptr, 0, /*add_special=*/true, /*parse_special=*/true);

    std::vector<llama_token> tokens(static_cast<size_t>(nPrompt));
    if (llama_tokenize(_model, prompt.c_str(), (int32_t)prompt.size(),
                       tokens.data(), (int32_t)tokens.size(),
                       /*add_special=*/true, /*parse_special=*/true) < 0) {
        LOGE("Tokenisation failed");
        return false;
    }

    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
    if (llama_decode(_ctx, batch) != 0) {
        LOGE("Prompt decode failed");
        return false;
    }

    // Generation loop
    std::string response;
    std::vector<char> piece(64);

    for (int i = 0; i < maxNewTokens && !_abortFlag; ++i) {
        llama_token token = llama_sampler_sample(_sampler, _ctx, -1);

        if (llama_token_is_eog(_model, token)) break;

        // Convert token to text piece
        int32_t n = llama_token_to_piece(
            _model, token,
            piece.data(), (int32_t)piece.size(),
            /*lstrip=*/0, /*special=*/true);

        if (n < 0) {
            piece.resize(static_cast<size_t>(-n) + 1);
            n = llama_token_to_piece(
                _model, token,
                piece.data(), (int32_t)piece.size(),
                0, true);
        }
        if (n <= 0) break;

        std::string tokenStr(piece.data(), static_cast<size_t>(n));
        response += tokenStr;
        tokenCallback(tokenStr);

        // Feed token back for next step
        llama_sampler_accept(_sampler, token);
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(_ctx, next) != 0) {
            LOGE("Decode failed at step %d", i);
            break;
        }
    }

    addMessage("assistant", response);
    llama_sampler_reset(_sampler);
    doneCallback();
    return true;
}

void LLMInference::abort() {
    _abortFlag = true;
}
