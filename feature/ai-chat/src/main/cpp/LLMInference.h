#pragma once

// b3447: public headers live in include/ — CMakeLists adds that to include path
#include "llama.h"

#include <string>
#include <vector>
#include <functional>

class LLMInference {
public:
    LLMInference();
    ~LLMInference();

    bool loadModel(const std::string& modelPath, int nCtx, int nThreads);
    void setSystemPrompt(const std::string& systemPrompt);
    void addMessage(const std::string& role, const std::string& content);
    void clearHistory();

    bool generate(
        const std::string& userMessage,
        std::function<void(const std::string&)> tokenCallback,
        std::function<void()> doneCallback,
        int maxNewTokens = 2048
    );

    void abort();
    bool isLoaded() const { return _model != nullptr; }

private:
    llama_model*   _model   = nullptr;
    llama_context* _ctx     = nullptr;
    llama_sampler* _sampler = nullptr;

    std::string _systemPrompt;

    // Store message strings — llama_chat_message holds raw char* pointers
    // so we own the strings here
    struct Message {
        std::string role;
        std::string content;
    };
    std::vector<Message> _history;

    volatile bool _abortFlag = false;

    void freeAll();
    std::string buildPrompt(bool addAssistantTurn);
};
