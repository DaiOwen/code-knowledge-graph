package com.example.ckg.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 工具处理器接口
 */
public interface McpToolHandler {
    /**
     * 工具名称
     */
    String getName();

    /**
     * 工具描述
     */
    String getDescription();

    /**
     * 输入参数 JSON Schema
     */
    JsonNode getInputSchema();

    /**
     * 执行工具
     */
    McpToolResult execute(JsonNode arguments);
}
