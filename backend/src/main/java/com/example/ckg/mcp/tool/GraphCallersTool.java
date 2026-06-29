package com.example.ckg.mcp.tool;

import com.example.ckg.controller.GraphController;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询方法调用者工具
 */
@Slf4j
@Component
public class GraphCallersTool implements McpToolHandler {

    private final GraphController graphController;
    private final ObjectMapper objectMapper;

    public GraphCallersTool(GraphController graphController, ObjectMapper objectMapper) {
        this.graphController = graphController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_graph_callers";
    }

    @Override
    public String getDescription() {
        return "查询某个方法的调用者（谁调用了这个方法）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode methodName = properties.putObject("methodName");
        methodName.put("type", "string");
        methodName.put("description", "方法名（支持简单名称或全限定名）");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");
        required.add("methodName");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            String methodName = arguments.path("methodName").asText();

            var result = graphController.getCallers(projectId, methodName);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Graph callers error", e);
            return McpToolResult.error("GRAPH_CALLERS_ERROR", e.getMessage());
        }
    }
}
