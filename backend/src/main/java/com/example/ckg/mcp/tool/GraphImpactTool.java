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
 * 影响分析工具 - 分析修改某个方法会影响哪些代码
 */
@Slf4j
@Component
public class GraphImpactTool implements McpToolHandler {

    private final GraphController graphController;
    private final ObjectMapper objectMapper;

    public GraphImpactTool(GraphController graphController, ObjectMapper objectMapper) {
        this.graphController = graphController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_graph_impact";
    }

    @Override
    public String getDescription() {
        return "分析修改某个方法会影响哪些代码（影响分析）";
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

        ObjectNode depth = properties.putObject("depth");
        depth.put("type", "integer");
        depth.put("description", "影响分析深度");
        depth.put("default", 3);

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
            int depth = arguments.has("depth") ? arguments.path("depth").asInt(3) : 3;

            var result = graphController.getImpactAnalysis(projectId, methodName, depth);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Graph impact analysis error", e);
            return McpToolResult.error("GRAPH_IMPACT_ERROR", e.getMessage());
        }
    }
}
