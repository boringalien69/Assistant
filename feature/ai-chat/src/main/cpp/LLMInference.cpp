#include "LLMInference.h"
#include <android/log.h>
#include <sstream>
#include <cstring>

#define TAG "LLMInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── Lifecycle ─────────────────────────────────────────────────────────────────

LLMContext* llm_create() {
    return new LLMContext();
}

void llm_destroy(LLMContext* lc) {
    if (!lc) return;
    llm_abort(lc);
    if (lc->sampler) { llama_sampler_free(lc->sampler); lc->sampler = nullptr; }
    if (lc->ctx)     { llama_free(lc->ctx);              lc->ctx     = nullptr; }
    if (lc->model)   { llama_free_model(lc->model);      lc->model   = nullptr; }
    delete lc;
}

// ── Load / query ──────────────────────────────────────────────────────────────

bool llm_load_model(LLMContext* lc, const char* model_path, int n_ctx, int n_threads) {
    if (!lc) return false;

    // Release previous model if any
    if (lc->sampler) { llama_sampler_free(lc->sampler); lc->sampler = nullptr; }
    if (lc->ctx)     { llama_free(lc->ctx);              lc->ctx     = nullptr; }
    if (lc->model)   { llama_free_model(lc->model);      lc->model   = nullptr; }

    lc->n_ctx     = n_ctx;
    lc->n_threads = n_threads;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // CPU-only

    lc->model = llama_load_model_from_file(model_path, mparams);
    if (!lc->model) {
        LOGE("Failed to load model from: %s", model_path);
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx       = (uint32_t)n_ctx;
    cparams.n_threads   = n_threads;
    cparams.n_threads_batch = n_threads;

    lc->ctx = llama_new_context_with_model(lc->model, cparams);
    if (!lc->ctx) {
        LOGE("Failed to create context");
        llama_free_model(lc->model);
        lc->model = nullptr;
        return false;
    }

    // Build sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    lc->sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(lc->sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(lc->sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(lc->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    lc->loaded_path = std::string(model_path);
    LOGI("Model loaded: %s  ctx=%d  threads=%d", model_path, n_ctx, n_threads);
    return true;
}

bool llm_is_loaded(LLMContext* lc) {
    return lc && lc->model && lc->ctx;
}

const char* llm_get_model_path(LLMContext* lc) {
    if (!lc || lc->loaded_path.empty()) return nullptr;
    return lc->loaded_path.c_str();
}

void llm_set_system_prompt(LLMContext* lc, const char* /*prompt*/) {
    // System prompt is injected via message list — no-op here
}

void llm_clear_history(LLMContext* lc) {
    if (lc && lc->ctx) {
        llama_kv_cache_clear(lc->ctx);
    }
}

// ── Inference ─────────────────────────────────────────────────────────────────

void llm_run_inference(LLMContext* lc,
                       const char** roles,
                       const char** contents,
                       int n_messages,
                       int max_new_tokens,
                       std::function<void(const char*)> token_cb,
                       std::function<void()>            done_cb,
                       std::function<void(const char*)> error_cb)
{
    if (!llm_is_loaded(lc)) {
        error_cb("Model not loaded");
        return;
    }

    lc->abort_flag.store(false);

    // Build llama_chat_message array
    std::vector<llama_chat_message> msgs(n_messages);
    for (int i = 0; i < n_messages; i++) {
        msgs[i].role    = roles[i];
        msgs[i].content = contents[i];
    }

    // Apply chat template
    std::vector<char> buf(65536);
    int len = llama_chat_apply_template(
        lc->model,
        nullptr,           // use model's built-in template
        msgs.data(),
        (size_t)n_messages,
        true,              // add_ass
        buf.data(),
        (int32_t)buf.size()
    );
    if (len < 0) {
        error_cb("Chat template failed");
        return;
    }
    if (len > (int)buf.size()) {
        buf.resize(len + 1);
        len = llama_chat_apply_template(
            lc->model, nullptr,
            msgs.data(), (size_t)n_messages, true,
            buf.data(), (int32_t)buf.size()
        );
    }
    std::string prompt(buf.data(), len);

    // Tokenise
    const int n_vocab = llama_n_vocab(lc->model);
    std::vector<llama_token> tokens(prompt.size() + 16);
    int n_tokens = llama_tokenize(
        lc->model,
        prompt.c_str(), (int32_t)prompt.size(),
        tokens.data(), (int32_t)tokens.size(),
        true,   // add_special
        true    // parse_special
    );
    if (n_tokens < 0) {
        error_cb("Tokenization failed");
        return;
    }
    tokens.resize(n_tokens);

    // Prefill
    llama_kv_cache_clear(lc->ctx);
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t)tokens.size());
        if (llama_decode(lc->ctx, batch) != 0) {
            error_cb("Prefill decode failed");
            return;
        }
    }

    // Generate
    std::string piece_buf;
    piece_buf.resize(256);

    for (int i = 0; i < max_new_tokens; i++) {
        if (lc->abort_flag.load()) break;

        llama_token token = llama_sampler_sample(lc->sampler, lc->ctx, -1);
        llama_sampler_accept(lc->sampler, token);

        if (llama_token_is_eog(lc->model, token)) break;

        // Detokenise single token
        int piece_len = llama_token_to_piece(
            lc->model, token,
            piece_buf.data(), (int32_t)piece_buf.size(),
            0, true
        );
        if (piece_len < 0) {
            piece_buf.resize(-piece_len);
            piece_len = llama_token_to_piece(
                lc->model, token,
                piece_buf.data(), (int32_t)piece_buf.size(),
                0, true
            );
        }
        if (piece_len > 0) {
            std::string piece(piece_buf.data(), piece_len);
            token_cb(piece.c_str());
        }

        // Decode next token position
        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(lc->ctx, next) != 0) break;
    }

    done_cb();
}

void llm_abort(LLMContext* lc) {
    if (lc) lc->abort_flag.store(true);
}
