package com.example.ckg.mcp.tool;

import com.example.ckg.controller.ParseController;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 解析进度查询工具 - 查询项目解析进度
 */
@Slf4j
@Component
public class ProjectParseProgressTool implements McpToolHandler {

    private final ParseController parseController;
    private final ObjectMapper objectMapper;

    public ProjectParseProgressTool(ParseController parseController, ObjectMapper objectMapper) {
        this.parseController = parseController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_project_parse_progress";
    }

    @Override
    public String getDescription() {
        return "查询项目解析进度";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            var result = parseController.getParseProgress(projectId);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Project parse progress error", e);
            return McpToolResult.error("PROJECT_PARSE_PROGRESS_ERROR", e.getMessage());
        }
    }
}
