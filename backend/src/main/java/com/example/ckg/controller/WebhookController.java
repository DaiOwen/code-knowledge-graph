package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.entity.WebhookEvent;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.service.webhook.WebhookService;
import com.example.ckg.service.parse.ParseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final ProjectRepository projectRepository;
    private final ParseService parseService;

    /**
     * GitLab Webhook endpoint
     *
     * Headers:
     * - X-Gitlab-Token: Secret token
     * - X-Gitlab-Event: Event type (Push Hook, Merge Request Hook, etc.)
     */
    @PostMapping("/gitlab/{projectId}")
    public ResponseEntity<Map<String, Object>> handleGitLabWebhook(
        @PathVariable Long projectId,
        @RequestBody String payload,
        HttpServletRequest request
    ) {
        log.info("Received GitLab webhook for project: {}", projectId);

        String token = request.getHeader("X-Gitlab-Token");
        String event = request.getHeader("X-Gitlab-Event");

        // Validate token
        if (!webhookService.validateGitLabToken(token, projectId)) {
            log.warn("Invalid GitLab webhook token for project: {}", projectId);
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Invalid token"
            ));
        }

        // Process based on event type
        if ("Push Hook".equals(event) || "push".equalsIgnoreCase(event)) {
            WebhookEvent webhookEvent = webhookService.processGitLabPushEvent(projectId, payload);
            if (webhookEvent != null) {
                // Trigger incremental parse
                triggerIncrementalParse(projectId, webhookEvent);

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Webhook processed",
                    "eventId", webhookEvent.getId()
                ));
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Event already processed (duplicate)"
            ));
        }

        // Other event types
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Event type not handled: " + event
        ));
    }

    /**
     * GitHub Webhook endpoint
     *
     * Headers:
     * - X-Hub-Signature-256: HMAC SHA256 signature
     * - X-GitHub-Event: Event type (push, pull_request, etc.)
     */
    @PostMapping("/github/{projectId}")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
        @PathVariable Long projectId,
        @RequestBody String payload,
        HttpServletRequest request
    ) {
        log.info("Received GitHub webhook for project: {}", projectId);

        String signature = request.getHeader("X-Hub-Signature-256");
        String event = request.getHeader("X-GitHub-Event");

        // Validate signature
        if (!webhookService.validateGitHubSignature(signature, payload, projectId)) {
            log.warn("Invalid GitHub webhook signature for project: {}", projectId);
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Invalid signature"
            ));
        }

        // Process push event
        if ("push".equals(event)) {
            WebhookEvent webhookEvent = webhookService.processGitHubPushEvent(projectId, payload);
            if (webhookEvent != null) {
                triggerIncrementalParse(projectId, webhookEvent);

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Webhook processed",
                    "eventId", webhookEvent.getId()
                ));
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Event already processed (duplicate)"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Event type not handled: " + event
        ));
    }

    /**
     * Gitee Webhook endpoint (similar to GitLab)
     */
    @PostMapping("/gitee/{projectId}")
    public ResponseEntity<Map<String, Object>> handleGiteeWebhook(
        @PathVariable Long projectId,
        @RequestBody String payload,
        HttpServletRequest request
    ) {
        log.info("Received Gitee webhook for project: {}", projectId);

        String token = request.getHeader("X-Gitee-Token");
        String event = request.getHeader("X-Gitee-Event");

        if (!webhookService.validateGitLabToken(token, projectId)) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Invalid token"
            ));
        }

        if ("Push Hook".equals(event)) {
            WebhookEvent webhookEvent = webhookService.processGitLabPushEvent(projectId, payload);
            if (webhookEvent != null) {
                triggerIncrementalParse(projectId, webhookEvent);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Webhook processed"
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Event processed"
        ));
    }

    /**
     * Trigger incremental parse for the changed files
     */
    private void triggerIncrementalParse(Long projectId, WebhookEvent event) {
        String[] changedFiles = webhookService.extractChangedFiles(
            event.getPayload(),
            event.getPlatform()
        );

        log.info("Triggering incremental parse for project {}, {} files changed",
            projectId, changedFiles.length);

        // TODO: Implement incremental parse logic
        // For now, trigger a full re-parse
        // parseService.parseProject(projectId, taskId);
    }

    /**
     * Get webhook event history
     */
    @GetMapping("/events/{projectId}")
    public Result<?> getWebhookEvents(@PathVariable Long projectId) {
        return Result.success(
            projectRepository.findById(projectId)
                .map(p -> webhookService.getClass()) // placeholder
                .orElse(null)
        );
    }
}