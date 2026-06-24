package com.example.ckg.service.project;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommitInfo {
    private String hash;
    private String message;
    private String authorName;
    private String authorEmail;
    private LocalDateTime authoredAt;
}