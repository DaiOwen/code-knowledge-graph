package com.example.ckg.service.qa;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerGenerator {

    private final ChatLanguageModel llm;

    private static final String ANSWER_PROMPT = """
你是一个代码知识图谱问答助手。基于图谱查询结果，生成准确、有帮助的回答。

## 用户问题
%s

## 图谱查询结果
%s

## 输出要求
1. 用自然语言回答问题
2. 引用具体的文件名和行号
3. 如果有代码片段，用代码块展示
4. 如果查询结果为空，诚实说明未找到相关信息

## 回答
""";

    public String generate(String question, GraphResult graphResult) {
        String context = buildContext(graphResult);
        String prompt = String.format(ANSWER_PROMPT, question, context);
        return llm.generate(prompt);
    }

    public String generateWithContext(String question, String context) {
        String prompt = String.format(ANSWER_PROMPT, question, context);
        return llm.generate(prompt);
    }

    private String buildContext(GraphResult result) {
        if (result == null || result.getNodes() == null || result.getNodes().isEmpty()) {
            return "查询结果为空";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到以下相关信息：\n\n");

        for (GraphResult.Node node : result.getNodes()) {
            sb.append("- **").append(node.getName()).append("**");
            if (node.getProperties() != null) {
                Map<String, Object> props = node.getProperties();
                if (props.containsKey("filePath")) {
                    sb.append(" (").append(props.get("filePath"));
                    if (props.containsKey("startLine")) {
                        sb.append(":").append(props.get("startLine"));
                    }
                    sb.append(")");
                }
                if (props.containsKey("signature")) {
                    sb.append("\n  签名: `").append(props.get("signature")).append("`");
                }
                if (props.containsKey("message")) {
                    sb.append("\n  提交信息: ").append(props.get("message"));
                }
                if (props.containsKey("authorName")) {
                    sb.append("\n  作者: ").append(props.get("authorName"));
                }
                if (props.containsKey("name") && !props.get("name").equals(node.getName())) {
                    // Handle case where name might be different
                    sb.append("\n  名称: ").append(props.get("name"));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Data
    @Builder
    public static class Citation {
        private String filePath;
        private Integer line;
        private String snippet;
    }
}