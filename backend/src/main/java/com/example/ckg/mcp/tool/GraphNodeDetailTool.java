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
 * 节点详情工具 - 获取知识图谱中某个节点的详细信息及关联关系
 */
@Slf4j
@Component
public class GraphNodeDetailTool implements McpToolHandler {

    private final GraphController graphController;
    private final ObjectMapper objectMapper;

    public GraphNodeDetailTool(GraphController graphController, ObjectMapper objectMapper) {
        this.graphController = graphController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_graph_node_detail";
    }

    @Override
    public String getDescription() {
        return "获取知识图谱中某个节点的详细信息及关联关系";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode nodeId = properties.putObject("nodeId");
        nodeId.put("type", "string");
        nodeId.put("description", "节点标识（格式: type:name:filePath:line 或简单名称）");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");
        required.add("nodeId");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            String nodeId = arguments.path("nodeId").asText();

            var result = graphController.getNodeDetail(projectId, nodeId);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Graph node detail error", e);
            return McpToolResult.error("GRAPH_NODE_DETAIL_ERROR", e.getMessage());
        }
    }
}
