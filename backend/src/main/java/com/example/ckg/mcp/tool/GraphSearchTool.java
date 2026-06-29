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
 * 知识图谱节点搜索工具
 */
@Slf4j
@Component
public class GraphSearchTool implements McpToolHandler {

    private final GraphController graphController;
    private final ObjectMapper objectMapper;

    public GraphSearchTool(GraphController graphController, ObjectMapper objectMapper) {
        this.graphController = graphController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_graph_search";
    }

    @Override
    public String getDescription() {
        return "在代码知识图谱中搜索节点（类、方法、字段等）";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode query = properties.putObject("query");
        query.put("type", "string");
        query.put("description", "搜索关键词");

        ObjectNode nodeType = properties.putObject("nodeType");
        nodeType.put("type", "string");
        nodeType.put("description", "节点类型过滤");

        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "返回结果数量上限");
        limit.put("default", 10);

        ArrayNode required = schema.putArray("required");
        required.add("projectId");
        required.add("query");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            String query = arguments.path("query").asText();
            String nodeType = arguments.has("nodeType") ? arguments.path("nodeType").asText() : null;
            int limit = arguments.has("limit") ? arguments.path("limit").asInt(10) : 10;

            var result = graphController.searchNodes(projectId, query, nodeType, limit);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Graph search error", e);
            return McpToolResult.error("GRAPH_SEARCH_ERROR", e.getMessage());
        }
    }
}
