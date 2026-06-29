package com.example.ckg.mcp;

import com.example.ckg.mcp.server.McpProtocolHandler;
import com.example.ckg.mcp.server.McpToolHandler;
import com.example.ckg.mcp.server.McpStdioTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 服务启动器 - 自动注册所有工具并启动传输层
 */
@Slf4j
@Component
public class McpServerBootstrap implements ApplicationRunner {

    private final McpProtocolHandler protocolHandler;
    private final List<McpToolHandler> toolHandlers;
    private final McpStdioTransport stdioTransport;

    public McpServerBootstrap(McpProtocolHandler protocolHandler,
                              List<McpToolHandler> toolHandlers,
                              McpStdioTransport stdioTransport) {
        this.protocolHandler = protocolHandler;
        this.toolHandlers = toolHandlers;
        this.stdioTransport = stdioTransport;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 注册所有工具
        for (McpToolHandler handler : toolHandlers) {
            protocolHandler.registerTool(handler.getName(), handler);
        }

        log.info("Registered {} MCP tools", toolHandlers.size());

        // 如果启用了 stdio 模式，启动 stdio 传输
        if (args.containsOption("mcp.stdio")) {
            log.info("Starting MCP in stdio mode...");
            stdioTransport.start();
        } else {
            log.info("MCP SSE mode available at /api/mcp/sse");
        }
    }
}
