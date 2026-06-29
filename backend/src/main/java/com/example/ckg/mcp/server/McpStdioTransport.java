package com.example.ckg.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP stdio 传输层 - 通过标准输入输出通信
 *
 * 用于本地开发环境，AI 助手通过子进程方式启动
 */
@Slf4j
@Component
public class McpStdioTransport {

    private final McpProtocolHandler protocolHandler;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private volatile boolean running = false;

    public McpStdioTransport(McpProtocolHandler protocolHandler, ObjectMapper objectMapper) {
        this.protocolHandler = protocolHandler;
        this.objectMapper = objectMapper;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 启动 stdio 传输
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                log.error("Error reading from stdin", e);
            }
        });

        log.info("MCP stdio transport started");
    }

    /**
     * 处理单条消息
     */
    private void handleMessage(String message) {
        String response = protocolHandler.handleMessage(message);
        if (response != null) {
            System.out.println(response);
            System.out.flush();
        }
    }

    /**
     * 停止传输
     */
    public void stop() {
        running = false;
        executor.shutdown();
        log.info("MCP stdio transport stopped");
    }
}
