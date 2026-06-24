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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;
    private final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;

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
}