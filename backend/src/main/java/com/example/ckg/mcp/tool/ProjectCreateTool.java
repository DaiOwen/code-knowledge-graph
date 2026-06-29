package com.example.ckg.mcp.tool;

import com.example.ckg.controller.ProjectController;
import com.example.ckg.dto.request.ProjectCreateRequest;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 创建项目工具 - 从Git仓库克隆代码并解析
 */
@Slf4j
@Component
public class ProjectCreateTool implements McpToolHandler {

    private final ProjectController projectController;
    private final ObjectMapper objectMapper;

    public ProjectCreateTool(ProjectController projectController, ObjectMapper objectMapper) {
        this.projectController = projectController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_project_create";
    }

    @Override
    public String getDescription() {
        return "创建新项目，从Git仓库克隆代码并解析";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "项目名称");

        ObjectNode gitUrl = properties.putObject("gitUrl");
        gitUrl.put("type", "string");
        gitUrl.put("description", "Git仓库URL");

        ObjectNode branch = properties.putObject("branch");
        branch.put("type", "string");
        branch.put("description", "分支名称，默认main");

        ObjectNode description = properties.putObject("description");
        description.put("type", "string");
        description.put("description", "项目描述");

        ArrayNode required = schema.putArray("required");
        required.add("name");
        required.add("gitUrl");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            ProjectCreateRequest request = new ProjectCreateRequest();
            request.setName(arguments.path("name").asText());
            request.setGitUrl(arguments.path("gitUrl").asText());
            if (arguments.has("branch")) {
                request.setBranch(arguments.path("branch").asText());
            }
            if (arguments.has("description")) {
                request.setDescription(arguments.path("description").asText());
            }

            var result = projectController.createProject(request, null);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("Project create error", e);
            return McpToolResult.error("PROJECT_CREATE_ERROR", e.getMessage());
        }
    }
}
