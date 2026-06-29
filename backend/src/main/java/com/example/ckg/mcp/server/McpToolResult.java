package com.example.ckg.mcp.server;

/**
 * MCP 工具执行结果
 */
public record McpToolResult(boolean success, String content, String errorCode) {
    public static McpToolResult success(String content) {
        return new McpToolResult(true, content, null);
    }

    public static McpToolResult error(String errorCode, String message) {
        return new McpToolResult(false, message, errorCode);
    }
}
