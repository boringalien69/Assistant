#pragma once

#include <string>
#include <vector>
#include <atomic>
#include <functional>

#include "llama.h"

struct LLMContext {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;

    std::string loaded_path;
    std::atomic<bool> abort_flag{false};

    int n_ctx     = 4096;
    int n_threads = 4;
};

LLMContext* llm_create();
void        llm_destroy(LLMContext* lc);

bool llm_load_model(LLMContext* lc,
                    const char* model_path,
                    int n_ctx,
                    int n_threads);

bool        llm_is_loaded(LLMContext* lc);
const char* llm_get_model_path(LLMContext* lc);

void llm_set_system_prompt(LLMContext* lc, const char* prompt);
void llm_clear_history(LLMContext* lc);

// roles and contents are parallel arrays of length n_messages
// token_cb is called for each token; done_cb when finished; error_cb on error
void llm_run_inference(LLMContext* lc,
                       const char** roles,
                       const char** contents,
                       int n_messages,
                       int max_new_tokens,
                       std::function<void(const char*)> token_cb,
                       std::function<void()>            done_cb,
                       std::function<void(const char*)> error_cb);

void llm_abort(LLMContext* lc);
