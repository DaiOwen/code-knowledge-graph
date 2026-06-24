package com.example.ckg.dto.request;

import lombok.Data;

@Data
public class QARequest {
    private String question;
    private Long projectId;
    private Long sessionId;
}