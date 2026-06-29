package com.example.ckg.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MCP SSE 传输层 - 通过 Server-Sent Events 通信
 *
 * 用于生产环境，MCP 客户端通过 HTTP SSE 连接
 * Endpoint: GET /api/mcp/sse
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpSseTransport {

    private final McpProtocolHandler protocolHandler;
    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public McpSseTransport(McpProtocolHandler protocolHandler, ObjectMapper objectMapper) {
        this.protocolHandler = protocolHandler;
        this.objectMapper = objectMapper;

        // 启动心跳保持连接
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * SSE 端点 - MCP 客户端连接入口
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseEndpoint() {
        String sessionId = java.util.UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // 无超时

        emitters.put(sessionId, emitter);
        log.info("MCP SSE client connected: {}", sessionId);

        // 发送端点信息
        try {
            emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/api/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            log.warn("Failed to send endpoint info to {}", sessionId);
        }

        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            log.info("MCP SSE client disconnected: {}", sessionId);
        });

        emitter.onError(e -> {
            emitters.remove(sessionId);
            log.warn("MCP SSE client error: {}", sessionId, e);
        });

        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            log.warn("MCP SSE client timeout: {}", sessionId);
        });

        return emitter;
    }

    /**
     * 消息接收端点 - MCP 客户端发送消息
     */
    @PostMapping("/message")
    public void messageEndpoint(
            @RequestParam String sessionId,
            @RequestBody String message) {

        log.debug("MCP message from {}: {}", sessionId, message);

        String response = protocolHandler.handleMessage(message);
        if (response != null) {
            sendToClient(sessionId, response);
        }
    }

    /**
     * 向指定客户端发送消息
     */
    private void sendToClient(String sessionId, String message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(message));
            } catch (IOException e) {
                log.warn("Failed to send message to {}", sessionId, e);
                emitters.remove(sessionId);
            }
        }
    }

    /**
     * 发送心跳保持连接
     */
    private void sendHeartbeats() {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                    .name("ping")
                    .data("{}"));
            } catch (IOException e) {
                log.debug("Heartbeat failed for {}, removing", entry.getKey());
                emitters.remove(entry.getKey());
            }
        }
    }
}
