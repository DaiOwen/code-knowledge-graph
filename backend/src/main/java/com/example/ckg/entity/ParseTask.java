package com.example.ckg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "parse_tasks")
public class ParseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParseType type;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ParseStatus status = ParseStatus.PENDING;

    @Builder.Default
    private Integer progress = 0;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ParseType {
        FULL,        // 全量解析
        INCREMENTAL  // 增量解析
    }

    public enum ParseStatus {
        PENDING,     // 待执行
        RUNNING,     // 执行中
        COMPLETED,   // 完成
        FAILED,      // 失败
        CANCELLED    // 已取消
    }
}