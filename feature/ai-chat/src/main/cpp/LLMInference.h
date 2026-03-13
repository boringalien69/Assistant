#pragma once

#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include "llama.h"

struct LLMParams {
    int   n_ctx          = 2048;
    float temperature    = 0.7f;
    float top_p          = 0.9f;
    int   top_k          = 40;
    int   max_new_tokens = 512;
    int   n_threads      = 0;       // 0 = auto (half available cores)
};

struct ChatMessage {
    std::string role;    // "system" | "user" | "assistant"
    std::string content;
};

class LLMInference {
public:
    LLMInference();
    ~LLMInference();

    // Load model from file descriptor (avoids copying large GGUF files)
    bool loadModel(int fd, long offset, long length, const LLMParams& params);

    // Run inference — tokenCallback fires for each output token
    // Returns false if cancelled or error
    bool runInference(
        const std::vector<ChatMessage>& messages,
        std::function<void(const std::string&)> tokenCallback
    );

    // Signal cancel — thread-safe, checked in inference loop
    void cancelInference();

    // Free native memory
    void release();

    bool isLoaded() const;

private:
    llama_model*   model_   = nullptr;
    llama_context* ctx_     = nullptr;
    LLMParams      params_;
    std::atomic<bool> cancelFlag_{false};

    std::string applyTemplate(const std::vector<ChatMessage>& messages);
    int  autoThreadCount();
};
