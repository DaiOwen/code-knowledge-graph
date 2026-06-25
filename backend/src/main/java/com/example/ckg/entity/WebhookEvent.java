package com.example.ckg.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "event_type")
    private String eventType;  // push, merge_request, pull_request

    @Column(name = "platform")
    private String platform;  // gitlab, github, gitee

    @Column(name = "payload_hash")
    private String payloadHash;  // For deduplication

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "ref")
    private String ref;  // branch name

    @Column(name = "before_commit")
    private String beforeCommit;

    @Column(name = "after_commit")
    private String afterCommit;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum EventStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EventStatus.PENDING;
        }
    }
}
