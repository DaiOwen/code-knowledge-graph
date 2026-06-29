package com.example.ckg.mcp.tool;

import com.example.ckg.controller.FileController;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件树工具 - 获取项目的文件树结构
 */
@Slf4j
@Component
public class FileTreeTool implements McpToolHandler {

    private final FileController fileController;
    private final ObjectMapper objectMapper;

    public FileTreeTool(FileController fileController, ObjectMapper objectMapper) {
        this.fileController = fileController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_file_tree";
    }

    @Override
    public String getDescription() {
        return "获取项目的文件树结构";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "目录路径，默认为根目录");
        path.put("default", ".");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            String path = arguments.has("path") ? arguments.path("path").asText(".") : ".";

            var result = fileController.getFileTree(projectId, path);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("File tree error", e);
            return McpToolResult.error("FILE_TREE_ERROR", e.getMessage());
        }
    }
}
