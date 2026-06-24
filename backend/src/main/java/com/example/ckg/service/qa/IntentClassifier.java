package com.example.ckg.service.qa;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatLanguageModel llm;

    private static final String CLASSIFICATION_PROMPT = """
你是一个代码问答系统的意图分类器。分析用户问题，提取以下信息：

## 输出格式 (JSON)
{"intent":"<意图类型>","entities":[{"name":"<实体名>","type":"<类型>"],"confidence":0.95}

## 意图类型
- CALL_CHAIN: 调用链查询 ("谁调用了", "被谁调用")
- IMPACT_ANALYSIS: 影响分析 ("会影响什么", "修改后影响")
- AUTHOR_TRACE: 代码溯源 ("谁写的", "谁修改的", "最后修改")
- CLASS_METHODS: 类方法查询 ("有哪些方法")
- SERVICE_DEPS: 服务依赖 ("依赖哪些服务")
- METHOD_HISTORY: 修改历史 ("修改历史", "提交记录")

## 示例
用户: "谁调用了 checkStock 方法？"
输出: {"intent":"CALL_CHAIN","entities":[{"name":"checkStock","type":"method"}],"confidence":0.95}

用户: "createOrder 方法是谁写的？"
输出: {"intent":"AUTHOR_TRACE","entities":[{"name":"createOrder","type":"method"}],"confidence":0.95}

## 用户问题
%s
""";

    public IntentResult classify(String question) {
        String prompt = String.format(CLASSIFICATION_PROMPT, question);
        String response = llm.generate(prompt);
        return parseResponse(response);
    }

    private IntentResult parseResponse(String response) {
        // Parse JSON response
        try {
            response = response.trim();
            if (response.startsWith("{")) {
                // Simple JSON parsing
                String intent = extractValue(response, "intent");
                String entitiesStr = extractArray(response, "entities");

                java.util.List<IntentResult.EntityInfo> entities = new java.util.ArrayList<>();

                // Parse entities array
                if (entitiesStr != null && !entitiesStr.isEmpty()) {
                    java.util.regex.Pattern entityPattern = java.util.regex.Pattern.compile(
                        "\\{\"name\":\"([^\"]+)\",\"type\":\"([^\"]+)\"}"
                    );
                    java.util.regex.Matcher matcher = entityPattern.matcher(entitiesStr);
                    while (matcher.find()) {
                        entities.add(IntentResult.EntityInfo.builder()
                            .name(matcher.group(1))
                            .type(matcher.group(2))
                            .build());
                    }
                }

                return IntentResult.builder()
                    .intent(intent)
                    .entities(entities)
                    .confidence(0.95)
                    .build();
            }
        } catch (Exception e) {
            log.warn("Failed to parse intent response: {}", e.getMessage());
        }

        // Default fallback
        return IntentResult.builder()
            .intent("UNKNOWN")
            .entities(java.util.List.of())
            .confidence(0.5)
            .build();
    }

    private String extractValue(String json, String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"([^\"]+)\""
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractArray(String json, String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]"
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}