package com.example.ckg.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LlmConfig {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String modelName;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.temperature:0.7}")
    private Double temperature;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing OpenAI LLM: model={}, baseUrl={}", modelName, baseUrl);
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(60))
            .build();
    }
}