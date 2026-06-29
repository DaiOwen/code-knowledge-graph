package com.example.ckg.mcp.tool;

import com.example.ckg.controller.QAController;
import com.example.ckg.dto.request.QARequest;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 智能问答工具 - 通过自然语言向代码知识图谱提问
 */
@Slf4j
@Component
public class QaAskTool implements McpToolHandler {

    private final QAController qaController;
    private final ObjectMapper objectMapper;

    public QaAskTool(QAController qaController, ObjectMapper objectMapper) {
        this.qaController = qaController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_qa_ask";
    }

    @Override
    public String getDescription() {
        return "通过自然语言向代码知识图谱提问，获取智能回答";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode question = properties.putObject("question");
        question.put("type", "string");
        question.put("description", "自然语言问题，例如：谁调用了createOrder方法？");

        ObjectNode sessionId = properties.putObject("sessionId");
        sessionId.put("type", "integer");
        sessionId.put("description", "会话ID（可选，用于保持上下文）");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");
        required.add("question");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            QARequest request = new QARequest();
            request.setProjectId(arguments.path("projectId").asLong());
            request.setQuestion(arguments.path("question").asText());
            if (arguments.has("sessionId")) {
                request.setSessionId(arguments.path("sessionId").asLong());
            }

            var result = qaController.askQuick(request, null);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("QA ask error", e);
            return McpToolResult.error("QA_ASK_ERROR", e.getMessage());
        }
    }
}
