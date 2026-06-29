package com.example.ckg.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Slf4j
@Configuration
public class LlmConfig {

    @Value("${llm.provider:openai}")
    private String provider;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String modelName;

    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.temperature:0.7}")
    private Double temperature;

    @Value("${llm.max-tokens:4096}")
    private Integer maxTokens;

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing LLM: provider={}, model={}", provider, modelName);

        return switch (provider.toLowerCase()) {
            case "openai", "azure" -> createOpenAiChatModel();
            case "qwen", "dashscope", "tongyi" -> createQwenChatModel();
            case "deepseek" -> createDeepSeekChatModel();
            default -> {
                log.warn("Unknown provider: {}, falling back to OpenAI", provider);
                yield createOpenAiChatModel();
            }
        };
    }

    @Bean
    @Primary
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("Initializing Streaming LLM: provider={}, model={}", provider, modelName);

        return switch (provider.toLowerCase()) {
            case "openai", "azure" -> createOpenAiStreamingChatModel();
            case "qwen", "dashscope", "tongyi" -> createQwenStreamingChatModel();
            case "deepseek" -> createDeepSeekStreamingChatModel();
            default -> {
                log.warn("Unknown provider: {}, falling back to OpenAI", provider);
                yield createOpenAiStreamingChatModel();
            }
        };
    }

    // ===================== OpenAI =====================

    private ChatLanguageModel createOpenAiChatModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    private StreamingChatLanguageModel createOpenAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    // ===================== Qwen (Tongyi) =====================

    private ChatLanguageModel createQwenChatModel() {
        return QwenChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName != null && !modelName.isEmpty() ? modelName : "qwen-max")
            .temperature(temperature != null ? temperature.floatValue() : 0.7f)
            .maxTokens(maxTokens)
            .build();
    }

    private StreamingChatLanguageModel createQwenStreamingChatModel() {
        return QwenStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName != null && !modelName.isEmpty() ? modelName : "qwen-max")
            .temperature(temperature != null ? temperature.floatValue() : 0.7f)
            .maxTokens(maxTokens)
            .build();
    }

    // ===================== DeepSeek =====================
    // DeepSeek uses OpenAI-compatible API

    private ChatLanguageModel createDeepSeekChatModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName != null && !modelName.isEmpty() ? modelName : "deepseek-chat")
            .baseUrl("https://api.deepseek.com/v1")
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    private StreamingChatLanguageModel createDeepSeekStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName != null && !modelName.isEmpty() ? modelName : "deepseek-chat")
            .baseUrl("https://api.deepseek.com/v1")
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(60))
            .build();
    }
}