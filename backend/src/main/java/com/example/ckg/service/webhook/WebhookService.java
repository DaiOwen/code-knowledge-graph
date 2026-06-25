package com.example.ckg.service.webhook;

import com.example.ckg.entity.Project;
import com.example.ckg.entity.WebhookEvent;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final ProjectRepository projectRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long REPLAY_PROTECTION_WINDOW = 300; // 5 minutes

    /**
     * Validate GitLab webhook token
     */
    public boolean validateGitLabToken(String token, Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        String expectedToken = project.getWebhookSecret();
        return expectedToken != null && expectedToken.equals(token);
    }

    /**
     * Validate GitHub webhook HMAC signature
     */
    public boolean validateGitHubSignature(String signature, String payload, Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        String secret = project.getWebhookSecret();
        if (secret == null || signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedSignature = computeHmacSha256(secret, payload);
            String providedSignature = signature.substring(7); // Remove "sha256=" prefix

            // Use MessageDigest.isEqual for timing-safe comparison
            return MessageDigest.isEqual(
                hexDecode(expectedSignature),
                hexDecode(providedSignature)
            );
        } catch (Exception e) {
            log.error("GitHub signature validation failed", e);
            return false;
        }
    }

    /**
     * Check for replay attack using Redis
     */
    public boolean isReplayAttack(String eventId) {
        String key = "webhook:processed:" + eventId;
        Boolean existed = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(existed)) {
            log.warn("Replay attack detected for event: {}", eventId);
            return true;
        }
        // Mark as processed
        redisTemplate.opsForValue().set(key, "processed", REPLAY_PROTECTION_WINDOW, TimeUnit.SECONDS);
        return false;
    }

    /**
     * Process GitLab push event
     */
    @Transactional
    public WebhookEvent processGitLabPushEvent(Long projectId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String ref = root.path("ref").asText();
            String before = root.path("before").asText();
            String after = root.path("after").asText();
            String checkoutSha = root.path("checkout_sha").asText();

            // Create event hash for deduplication
            String payloadHash = computeSha256(payload);

            // Check for duplicate
            if (webhookEventRepository.existsByPayloadHashAndCreatedAtAfter(
                payloadHash, LocalDateTime.now().minusMinutes(REPLAY_PROTECTION_WINDOW))) {
                log.info("Duplicate webhook event detected: {}", payloadHash);
                return null;
            }

            // Check replay attack
            String eventId = after + ":" + projectId;
            if (isReplayAttack(eventId)) {
                return null;
            }

            WebhookEvent event = WebhookEvent.builder()
                .projectId(projectId)
                .eventType("push")
                .platform("gitlab")
                .payloadHash(payloadHash)
                .payload(payload)
                .ref(ref)
                .beforeCommit(before)
                .afterCommit(after)
                .status(WebhookEvent.EventStatus.PENDING)
                .build();

            return webhookEventRepository.save(event);

        } catch (Exception e) {
            log.error("Failed to parse GitLab push event", e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的 Webhook 数据");
        }
    }

    /**
     * Process GitHub push event
     */
    @Transactional
    public WebhookEvent processGitHubPushEvent(Long projectId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            String ref = root.path("ref").asText();
            String before = root.path("before").asText();
            String after = root.path("after").asText();

            String payloadHash = computeSha256(payload);

            if (webhookEventRepository.existsByPayloadHashAndCreatedAtAfter(
                payloadHash, LocalDateTime.now().minusMinutes(REPLAY_PROTECTION_WINDOW))) {
                return null;
            }

            String eventId = after + ":" + projectId;
            if (isReplayAttack(eventId)) {
                return null;
            }

            WebhookEvent event = WebhookEvent.builder()
                .projectId(projectId)
                .eventType("push")
                .platform("github")
                .payloadHash(payloadHash)
                .payload(payload)
                .ref(ref)
                .beforeCommit(before)
                .afterCommit(after)
                .status(WebhookEvent.EventStatus.PENDING)
                .build();

            return webhookEventRepository.save(event);

        } catch (Exception e) {
            log.error("Failed to parse GitHub push event", e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的 Webhook 数据");
        }
    }

    /**
     * Extract changed files from webhook payload
     */
    public String[] extractChangedFiles(String payload, String platform) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode commits = root.path("commits");

            if (commits.isArray()) {
                java.util.Set<String> changedFiles = new java.util.HashSet<>();

                for (JsonNode commit : commits) {
                    // Added files
                    JsonNode added = commit.path("added");
                    if (added.isArray()) {
                        for (JsonNode file : added) {
                            changedFiles.add(file.asText());
                        }
                    }

                    // Modified files
                    JsonNode modified = commit.path("modified");
                    if (modified.isArray()) {
                        for (JsonNode file : modified) {
                            changedFiles.add(file.asText());
                        }
                    }

                    // Removed files
                    JsonNode removed = commit.path("removed");
                    if (removed.isArray()) {
                        for (JsonNode file : removed) {
                            changedFiles.add(file.asText());
                        }
                    }
                }

                return changedFiles.toArray(new String[0]);
            }
        } catch (Exception e) {
            log.error("Failed to extract changed files", e);
        }
        return new String[0];
    }

    private String computeHmacSha256(String secret, String payload) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(secret.getBytes(StandardCharsets.UTF_8));
        byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return data;
    }
}