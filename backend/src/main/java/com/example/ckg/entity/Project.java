package com.example.ckg.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String gitUrl;

    @Column(length = 50)
    private String branch;

    private Long credentialId;

    @Column(length = 20)
    private String language;

    @Column(length = 500)
    private String parseScope;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PENDING;

    private LocalDateTime lastParsedAt;

    @Column(length = 100)
    private String webhookSecret;

    @Column(length = 500)
    private String localPath;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ProjectStatus {
        PENDING,      // 刚创建，未解析
        PARSING,      // 正在解析
        READY,        // 解析完成，可用
        ERROR,        // 解析失败
        NO_SOURCE     // 无源代码
    }
}