package com.example.ckg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QAResponse {
    private String answer;
    private List<Citation> citations;

    @Data
    @Builder
    public static class Citation {
        private String filePath;
        private Integer line;
        private String snippet;
    }
}