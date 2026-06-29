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
 * 文件内容工具 - 获取指定文件的内容
 */
@Slf4j
@Component
public class FileContentTool implements McpToolHandler {

    private final FileController fileController;
    private final ObjectMapper objectMapper;

    public FileContentTool(FileController fileController, ObjectMapper objectMapper) {
        this.fileController = fileController;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "ckg_file_content";
    }

    @Override
    public String getDescription() {
        return "获取指定文件的内容";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode projectId = properties.putObject("projectId");
        projectId.put("type", "integer");
        projectId.put("description", "项目ID");

        ObjectNode filePath = properties.putObject("filePath");
        filePath.put("type", "string");
        filePath.put("description", "文件路径");

        ObjectNode startLine = properties.putObject("startLine");
        startLine.put("type", "integer");
        startLine.put("description", "起始行号（可选）");

        ObjectNode endLine = properties.putObject("endLine");
        endLine.put("type", "integer");
        endLine.put("description", "结束行号（可选）");

        ArrayNode required = schema.putArray("required");
        required.add("projectId");
        required.add("filePath");

        return schema;
    }

    @Override
    public McpToolResult execute(JsonNode arguments) {
        try {
            Long projectId = arguments.path("projectId").asLong();
            String filePath = arguments.path("filePath").asText();
            Integer startLine = arguments.has("startLine") ? arguments.path("startLine").asInt() : null;
            Integer endLine = arguments.has("endLine") ? arguments.path("endLine").asInt() : null;

            var result = fileController.getFileContent(projectId, filePath, startLine, endLine);
            return McpToolResult.success(objectMapper.writeValueAsString(result.getData()));
        } catch (Exception e) {
            log.error("File content error", e);
            return McpToolResult.error("FILE_CONTENT_ERROR", e.getMessage());
        }
    }
}
