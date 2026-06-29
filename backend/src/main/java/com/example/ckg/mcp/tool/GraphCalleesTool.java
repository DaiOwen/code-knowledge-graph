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
 * 查询方法被调用者工具
 */
@Slf4j
@Component
public class GraphCalleesTool implements McpToolHandler {

    private final GraphController graphController;
    private final ObjectMapper objectMapper;

    public GraphCalleesTool(GraphController graphController, ObjectMapper objectMapper) {
        this.graphController = graphController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_graph_callees";
    }

    @Override
    public String getDescription() {
        return "查询某个方法调用了哪些其他方法";
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
        methodName.put("description", "方法名");

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

            var result = graphController.getCallees(projectId, methodName);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Graph callees error", e);
            return McpToolResult.error("GRAPH_CALLEES_ERROR", e.getMessage());
        }
    }
}
