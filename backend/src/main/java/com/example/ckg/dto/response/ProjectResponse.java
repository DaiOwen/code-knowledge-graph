package com.example.ckg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {
    private Long id;
    private String name;
    private String gitUrl;
    private String branch;
    private String language;
    private String parseScope;
    private String status;
    private LocalDateTime lastParsedAt;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}