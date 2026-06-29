package com.example.ckg.mcp.tool;

import com.example.ckg.controller.ProjectController;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 项目列表工具 - 列出所有已解析的项目
 */
@Slf4j
@Component
public class ProjectListTool implements McpToolHandler {

    private final ProjectController projectController;
    private final ObjectMapper objectMapper;

    public ProjectListTool(ProjectController projectController, ObjectMapper objectMapper) {
        this.projectController = projectController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_project_list";
    }

    @Override
    public String getDescription() {
        return "列出所有已解析的项目";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            var result = projectController.listProjects();
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Project list error", e);
            return McpToolResult.error("PROJECT_LIST_ERROR", e.getMessage());
        }
    }
}
