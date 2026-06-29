package com.example.ckg.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP Protocol Handler - 处理 JSON-RPC 2.0 消息
 *
 * MCP 协议基于 JSON-RPC 2.0，主要消息类型：
 * - initialize: 客户端初始化连接
 * - tools/list: 列出可用工具
 * - tools/call: 调用工具
 * - notifications/initialized: 初始化完成通知
 */
@Slf4j
@Component
public class McpProtocolHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, McpToolHandler> toolHandlers;
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);

    public McpProtocolHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.toolHandlers = new ConcurrentHashMap<>();
    }

    /**
     * 注册工具处理器
     */
    public void registerTool(String name, McpToolHandler handler) {
        toolHandlers.put(name, handler);
        log.info("Registered MCP tool: {}", name);
    }

    /**
     * 处理传入的 JSON-RPC 消息
     */
    public String handleMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            // 检查是否是通知（无 id 字段）
            if (!jsonNode.has("id")) {
                handleNotification(jsonNode);
                return null; // 通知不需要响应
            }

            String method = jsonNode.path("method").asText();
            int id = jsonNode.path("id").asInt();
            JsonNode params = jsonNode.path("params");

            log.debug("MCP Request: id={}, method={}", id, method);

            switch (method) {
                case "initialize":
                    return handleInitialize(id, params);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolCall(id, params);
                case "ping":
                    return handlePing(id);
                default:
                    return createErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("Error handling MCP message: {}", message, e);
            return createErrorResponse(0, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理初始化请求
     */
    private String handleInitialize(int id, JsonNode params) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode tools = capabilities.putObject("tools");
        tools.putObject("listChanged");

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "ckg-mcp-server");
        serverInfo.put("version", "1.0.0");

        return createSuccessResponse(id, result);
    }

    /**
     * 处理工具列表请求
     */
    private String handleToolsList(int id) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode toolsArray = result.putArray("tools");

        for (Map.Entry<String, McpToolHandler> entry : toolHandlers.entrySet()) {
            McpToolHandler handler = entry.getValue();
            ObjectNode toolNode = toolsArray.addObject();
            toolNode.put("name", handler.getName());
            toolNode.put("description", handler.getDescription());
            toolNode.set("inputSchema", handler.getInputSchema());
        }

        return createSuccessResponse(id, result);
    }

    /**
     * 处理工具调用请求
     */
    private String handleToolCall(int id, JsonNode params) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        McpToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            return createErrorResponse(id, -32602, "Tool not found: " + toolName);
        }

        try {
            McpToolResult result = handler.execute(arguments);

            ObjectNode resultNode = objectMapper.createObjectNode();
            ArrayNode contentArray = resultNode.putArray("content");
            ObjectNode textContent = contentArray.addObject();
            textContent.put("type", "text");
            textContent.put("text", result.content());

            if (!result.success()) {
                resultNode.put("isError", true);
            }

            return createSuccessResponse(id, resultNode);
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return createErrorResponse(id, -32603, "Tool execution error: " + e.getMessage());
        }
    }

    /**
     * 处理 ping 请求
     */
    private String handlePing(int id) {
        return createSuccessResponse(id, objectMapper.createObjectNode());
    }

    /**
     * 处理通知消息
     */
    private void handleNotification(JsonNode jsonNode) {
        String method = jsonNode.path("method").asText();
        log.debug("MCP Notification: {}", method);
        // 目前只记录通知，不需要特殊处理
    }

    /**
     * 创建成功的 JSON-RPC 响应
     */
    private String createSuccessResponse(int id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        return response.toString();
    }

    /**
     * 创建错误的 JSON-RPC 响应
     */
    private String createErrorResponse(int id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);

        return response.toString();
    }
}