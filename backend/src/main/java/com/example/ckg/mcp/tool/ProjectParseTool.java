package com.example.ckg.mcp.tool;

import com.example.ckg.controller.ProjectController;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 触发项目解析工具 - 触发项目的全量代码解析
 */
@Slf4j
@Component
public class ProjectParseTool implements McpToolHandler {

    private final ProjectController projectController;
    private final ObjectMapper objectMapper;

    public ProjectParseTool(ProjectController projectController, ObjectMapper objectMapper) {
        this.projectController = projectController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_project_parse";
    }

    @Override
    public String getDescription() {
        return "触发项目的全量代码解析";
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
            var result = projectController.triggerParse(projectId);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Project parse error", e);
            return McpToolResult.error("PROJECT_PARSE_ERROR", e.getMessage());
        }
    }
}
