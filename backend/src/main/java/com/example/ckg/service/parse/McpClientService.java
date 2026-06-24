package com.example.ckg.service.parse;

import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class McpClientService {

    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();

    /**
     * Start codegraph MCP server and initialize connection
     */
    public void startCodegraph(Long projectId, Path projectPath) {
        try {
            // Kill existing process if any
            stopCodegraph(projectId);

            // Start codegraph serve --mcp
            ProcessBuilder pb = new ProcessBuilder(
                "codegraph", "serve", "--mcp", "--path", projectPath.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            activeProcesses.put(projectId, process);

            // Wait for initialization
            TimeUnit.SECONDS.sleep(2);

            log.info("Codegraph MCP started for project: {}", projectId);

        } catch (Exception e) {
            log.error("Failed to start codegraph MCP", e);
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, "无法启动 codegraph: " + e.getMessage());
        }
    }

    /**
     * Stop codegraph MCP server
     */
    public void stopCodegraph(Long projectId) {
        Process process = activeProcesses.remove(projectId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            log.info("Codegraph MCP stopped for project: {}", projectId);
        }
    }

    /**
     * Call codegraph_files tool
     */
    public String callFilesTool(Long projectId) throws IOException {
        return callTool(projectId, "codegraph_files", Map.of());
    }

    /**
     * Call codegraph_explore tool
     */
    public String callExploreTool(Long projectId, String query, int maxFiles) throws IOException {
        return callTool(projectId, "codegraph_explore", Map.of(
            "query", query,
            "maxFiles", maxFiles
        ));
    }

    /**
     * Call codegraph_search tool
     */
    public String callSearchTool(Long projectId, String query, String kind, int limit) throws IOException {
        return callTool(projectId, "codegraph_search", Map.of(
            "query", query,
            "kind", kind,
            "limit", limit
        ));
    }

    /**
     * Call codegraph_callers tool
     */
    public String callCallersTool(Long projectId, String symbol, int limit) throws IOException {
        return callTool(projectId, "codegraph_callers", Map.of(
            "symbol", symbol,
            "limit", limit
        ));
    }

    /**
     * Call codegraph_callees tool
     */
    public String callCalleesTool(Long projectId, String symbol, int limit) throws IOException {
        return callTool(projectId, "codegraph_callees", Map.of(
            "symbol", symbol,
            "limit", limit
        ));
    }

    /**
     * Call codegraph_node tool
     */
    public String callNodeTool(Long projectId, String symbol, boolean includeCode) throws IOException {
        return callTool(projectId, "codegraph_node", Map.of(
            "symbol", symbol,
            "includeCode", includeCode
        ));
    }

    /**
     * Generic MCP tool call
     */
    private String callTool(Long projectId, String toolName, Map<String, Object> args) throws IOException {
        Process process = activeProcesses.get(projectId);
        if (process == null || !process.isAlive()) {
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, "MCP 进程未运行");
        }

        // Build MCP request
        String requestId = String.valueOf(System.currentTimeMillis());
        String request = buildMcpRequest(requestId, toolName, args);

        // Write to process stdin
        OutputStream stdin = process.getOutputStream();
        stdin.write((request + "\n").getBytes());
        stdin.flush();

        // Read from process stdout
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String response = reader.readLine();

        return parseMcpResponse(response);
    }

    private String buildMcpRequest(String id, String method, Map<String, Object> params) {
        StringBuilder json = new StringBuilder();
        json.append("{\"jsonrpc\":\"2.0\",\"id\":\"").append(id).append("\",");
        json.append("\"method\":\"tools/call\",");
        json.append("\"params\":{\"name\":\"").append(method).append("\",\"arguments\":{");

        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
        }

        json.append("}}}");
        return json.toString();
    }

    private String parseMcpResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new BusinessException(ErrorCode.MCP_CALL_FAILED, "MCP 响应为空");
        }

        // Parse JSON and extract content
        // MCP response format: {"jsonrpc":"2.0","id":"...","result":{"content":[{"type":"text","text":"..."}]}}
        try {
            Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher matcher = textPattern.matcher(response);
            if (matcher.find()) {
                return unescapeJson(matcher.group(1));
            }
            return response;
        } catch (Exception e) {
            log.warn("Failed to parse MCP response: {}", e.getMessage());
            return response;
        }
    }

    private String unescapeJson(String text) {
        return text
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }
}