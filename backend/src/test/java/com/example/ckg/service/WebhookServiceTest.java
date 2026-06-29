package com.example.ckg.service;

import com.example.ckg.BaseTest;
import com.example.ckg.entity.Project;
import com.example.ckg.entity.WebhookEvent;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.repository.WebhookEventRepository;
import com.example.ckg.service.webhook.WebhookService;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebhookService 单元测试
 *
 * 测试内容：
 * 1. GitLab Token 验证
 * 2. GitHub HMAC 签名验证
 * 3. Payload 解析
 */
class WebhookServiceTest extends BaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        // Setup default mock behaviors
    }

    @Test
    @DisplayName("测试 GitLab Token 验证 - Token 匹配")
    void testValidateGitLabTokenMatch() {
        Long projectId = 1L;
        String token = "secret-token";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret("secret-token")
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        boolean result = webhookService.validateGitLabToken(token, projectId);

        assertTrue(result);
        verify(projectRepository).findById(projectId);
    }

    @Test
    @DisplayName("测试 GitLab Token 验证 - Token 不匹配")
    void testValidateGitLabTokenMismatch() {
        Long projectId = 1L;
        String token = "wrong-token";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret("secret-token")
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        boolean result = webhookService.validateGitLabToken(token, projectId);

        assertFalse(result);
    }

    @Test
    @DisplayName("测试 GitLab Token 验证 - 项目不存在")
    void testValidateGitLabTokenProjectNotFound() {
        Long projectId = 999L;
        String token = "any-token";

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> {
            webhookService.validateGitLabToken(token, projectId);
        });
    }

    @Test
    @DisplayName("测试 GitLab Token 验证 - 项目无 Webhook Secret")
    void testValidateGitLabTokenNoSecret() {
        Long projectId = 1L;
        String token = "any-token";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret(null)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        boolean result = webhookService.validateGitLabToken(token, projectId);

        assertFalse(result);
    }

    @Test
    @DisplayName("测试 GitHub HMAC 签名验证 - 有效签名")
    void testValidateGitHubSignatureValid() {
        Long projectId = 1L;
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String secret = "webhook-secret";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret(secret)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Calculate actual HMAC-SHA256
        String expectedSignature = computeHmacSha256(secret, payload);
        String signature = "sha256=" + expectedSignature;

        boolean result = webhookService.validateGitHubSignature(signature, payload, projectId);

        assertTrue(result);
    }

    @Test
    @DisplayName("测试 GitHub HMAC 签名验证 - 无效签名")
    void testValidateGitHubSignatureInvalid() {
        Long projectId = 1L;
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String secret = "webhook-secret";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret(secret)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        String signature = "sha256=invalidsignature123";

        boolean result = webhookService.validateGitHubSignature(signature, payload, projectId);

        assertFalse(result);
    }

    @Test
    @DisplayName("测试 GitHub HMAC 签名验证 - 缺少 sha256 前缀")
    void testValidateGitHubSignatureMissingPrefix() {
        Long projectId = 1L;
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String secret = "webhook-secret";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret(secret)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        String signature = "invalidsignature123";  // No sha256 prefix

        boolean result = webhookService.validateGitHubSignature(signature, payload, projectId);

        assertFalse(result);
    }

    @Test
    @DisplayName("测试 GitHub HMAC 签名验证 - 使用不同密钥生成的签名")
    void testValidateGitHubSignatureDifferentSecret() {
        Long projectId = 1L;
        String payload = "{\"ref\":\"refs/heads/main\"}";
        String actualSecret = "webhook-secret";
        String wrongSecret = "wrong-secret";

        Project project = Project.builder()
            .id(projectId)
            .webhookSecret(actualSecret)
            .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // Calculate HMAC with wrong secret
        String wrongSignature = computeHmacSha256(wrongSecret, payload);
        String signature = "sha256=" + wrongSignature;

        boolean result = webhookService.validateGitHubSignature(signature, payload, projectId);

        assertFalse(result);  // Should fail because secrets don't match
    }

    @Test
    @DisplayName("测试重放攻击防护 - Redis 检查")
    void testReplayAttackProtection() {
        String eventId = "abc123:1";

        when(redisTemplate.hasKey("webhook:processed:" + eventId)).thenReturn(true);

        boolean isReplay = webhookService.isReplayAttack(eventId);

        assertTrue(isReplay);
        verify(redisTemplate).hasKey("webhook:processed:" + eventId);
    }

    @Test
    @DisplayName("测试重放攻击防护 - 首次请求通过")
    void testReplayAttackProtectionFirstRequest() {
        String eventId = "def456:1";

        when(redisTemplate.hasKey("webhook:processed:" + eventId)).thenReturn(false);

        boolean isReplay = webhookService.isReplayAttack(eventId);

        assertFalse(isReplay);
        verify(redisTemplate).hasKey("webhook:processed:" + eventId);
    }

    @Test
    @DisplayName("测试 GitLab Push Event 解析")
    void testProcessGitLabPushEvent() {
        Long projectId = 1L;
        String payload = """
            {
              "ref": "refs/heads/main",
              "before": "abc123",
              "after": "def456",
              "checkout_sha": "def456",
              "commits": [
                {
                  "added": ["src/NewFile.java"],
                  "modified": ["src/ModifiedFile.java"],
                  "removed": []
                }
              ]
            }
            """;

        when(webhookEventRepository.existsByPayloadHashAndCreatedAtAfter(anyString(), any()))
            .thenReturn(false);
        when(webhookEventRepository.save(any())).thenAnswer(invocation -> {
            WebhookEvent event = invocation.getArgument(0);
            event.setId(1L);
            return event;
        });
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        WebhookEvent result = webhookService.processGitLabPushEvent(projectId, payload);

        assertNotNull(result);
        assertEquals(projectId, result.getProjectId());
        assertEquals("push", result.getEventType());
        assertEquals("gitlab", result.getPlatform());
        assertEquals("refs/heads/main", result.getRef());
        assertEquals("abc123", result.getBeforeCommit());
        assertEquals("def456", result.getAfterCommit());
        assertEquals(WebhookEvent.EventStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("测试重复 Payload 去重")
    void testDuplicatePayloadDeduplication() {
        Long projectId = 1L;
        String payload = "{\"ref\":\"refs/heads/main\",\"before\":\"abc\",\"after\":\"def\"}";

        when(webhookEventRepository.existsByPayloadHashAndCreatedAtAfter(anyString(), any()))
            .thenReturn(true);

        WebhookEvent result = webhookService.processGitLabPushEvent(projectId, payload);

        assertNull(result);  // Duplicate is ignored
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("测试变更文件提取")
    void testExtractChangedFiles() {
        String payload = """
            {
              "commits": [
                {
                  "added": ["src/main/java/NewFile.java"],
                  "modified": ["src/main/java/ModifiedFile.java"],
                  "removed": ["src/main/java/DeletedFile.java"]
                },
                {
                  "added": ["src/main/java/AnotherNew.java"],
                  "modified": [],
                  "removed": []
                }
              ]
            }
            """;

        String[] changedFiles = webhookService.extractChangedFiles(payload, "gitlab");

        assertNotNull(changedFiles);
        assertEquals(4, changedFiles.length);

        // Verify all file types are extracted
        assertTrue(java.util.Arrays.asList(changedFiles).contains("src/main/java/NewFile.java"));
        assertTrue(java.util.Arrays.asList(changedFiles).contains("src/main/java/ModifiedFile.java"));
        assertTrue(java.util.Arrays.asList(changedFiles).contains("src/main/java/DeletedFile.java"));
        assertTrue(java.util.Arrays.asList(changedFiles).contains("src/main/java/AnotherNew.java"));
    }

    @Test
    @DisplayName("测试 WebhookEvent 状态流转")
    void testWebhookEventStatusFlow() {
        WebhookEvent event = WebhookEvent.builder()
            .projectId(1L)
            .eventType("push")
            .platform("gitlab")
            .status(WebhookEvent.EventStatus.PENDING)
            .build();

        assertEquals(WebhookEvent.EventStatus.PENDING, event.getStatus());

        // Simulate processing
        event.setStatus(WebhookEvent.EventStatus.PROCESSING);
        assertEquals(WebhookEvent.EventStatus.PROCESSING, event.getStatus());

        // Simulate completion
        event.setStatus(WebhookEvent.EventStatus.COMPLETED);
        event.setProcessedAt(LocalDateTime.now());
        assertEquals(WebhookEvent.EventStatus.COMPLETED, event.getStatus());
        assertNotNull(event.getProcessedAt());
    }

    /**
     * Helper method to compute HMAC-SHA256 (correct implementation)
     */
    private String computeHmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    /**
     * Convert bytes to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}