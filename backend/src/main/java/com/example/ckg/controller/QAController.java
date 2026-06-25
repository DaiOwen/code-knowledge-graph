package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.dto.request.QARequest;
import com.example.ckg.dto.response.QAResponse;
import com.example.ckg.entity.ChatSession;
import com.example.ckg.entity.Message;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ChatSessionRepository;
import com.example.ckg.repository.MessageRepository;
import com.example.ckg.service.qa.QAService;
import com.example.ckg.service.qa.StreamingQAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;
    private final StreamingQAService streamingQAService;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody Map<String, Object> body,
                                              @AuthenticationPrincipal Long userId) {
        Long projectId = body.containsKey("projectId") ?
            ((Number) body.get("projectId")).longValue() : null;
        String title = (String) body.getOrDefault("title", "新对话");

        ChatSession session = ChatSession.builder()
            .userId(userId)
            .projectId(projectId)
            .title(title)
            .build();
        session = sessionRepository.save(session);

        return Result.success(session);
    }

    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions(@AuthenticationPrincipal Long userId,
                                                    @RequestParam(required = false) Long projectId) {
        List<ChatSession> sessions;
        if (projectId != null) {
            sessions = sessionRepository.findByUserIdAndProjectIdOrderByCreatedAtDesc(userId, projectId);
        } else {
            sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return Result.success(sessions);
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionRepository.deleteById(id);
        return Result.success();
    }

    @PostMapping("/sessions/{id}/ask")
    public Result<QAResponse> ask(@PathVariable Long id,
                                   @RequestBody QARequest request,
                                   @AuthenticationPrincipal Long userId) {
        request.setSessionId(id);
        return Result.success(qaService.ask(request, userId));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        return Result.success(messageRepository.findBySessionIdOrderByCreatedAtAsc(id));
    }

    @PostMapping("/ask")
    public Result<QAResponse> askQuick(@RequestBody QARequest request,
                                        @AuthenticationPrincipal Long userId) {
        return Result.success(qaService.ask(request, userId));
    }

    /**
     * SSE streaming endpoint for Q&A
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(@RequestBody QARequest request,
                                   @AuthenticationPrincipal Long userId) {
        SseEmitter emitter = new SseEmitter(60000L); // 60s timeout

        executor.execute(() -> {
            try {
                streamingQAService.streamAnswer(request, userId, new StreamingQAService.StreamingCallback() {
                    @Override
                    public void onToken(String token) {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                        } catch (IOException e) {
                            log.warn("Failed to send token: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete(QAResponse response) {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(response));
                            emitter.complete();
                        } catch (IOException e) {
                            log.warn("Failed to send completion: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(e.getMessage()));
                        } catch (IOException ex) {
                            log.warn("Failed to send error: {}", ex.getMessage());
                        }
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                log.error("Streaming error", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE connection timed out");
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("SSE connection error: {}", e.getMessage());
        });

        return emitter;
    }
}