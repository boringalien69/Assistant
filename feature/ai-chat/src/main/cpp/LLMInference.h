#pragma once

#include "llama.h"
#include <string>
#include <vector>
#include <functional>

class LLMInference {
public:
    LLMInference();
    ~LLMInference();

    // Load model from file path
    bool loadModel(const std::string& modelPath, int nCtx, int nThreads);

    // Set system prompt
    void setSystemPrompt(const std::string& systemPrompt);

    // Add a message to chat history
    void addMessage(const std::string& role, const std::string& content);

    // Clear chat history (keep system prompt)
    void clearHistory();

    // Run inference; calls tokenCallback for each new token string, returns false on error
    bool generate(
        const std::string& userMessage,
        std::function<void(const std::string&)> tokenCallback,
        std::function<void()> doneCallback,
        int maxNewTokens = 2048
    );

    // Abort ongoing generation
    void abort();

    bool isLoaded() const { return _model != nullptr; }

private:
    llama_model*   _model   = nullptr;
    llama_context* _ctx     = nullptr;
    llama_sampler* _sampler = nullptr;

    std::string _systemPrompt;
    std::vector<llama_chat_message> _messages;
    std::vector<std::string>        _msgStorage; // owns the string data pointed to by _messages

    volatile bool _abortFlag = false;

    void freeModel();
    std::string applyTemplate();
};
