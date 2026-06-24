package com.example.ckg.service.qa;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class IntentResult {
    private String intent;      // CALL_CHAIN, IMPACT_ANALYSIS, AUTHOR_TRACE, etc.
    private List<EntityInfo> entities;
    private Double confidence;

    @Data
    @Builder
    public static class EntityInfo {
        private String name;
        private String type;   // method, class, service, person
    }
}