package com.example.ckg.service.qa;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GraphResult {
    private List<NodeResult> nodes;
    private List<EdgeResult> edges;
    private String rawCypher;

    @Data
    @Builder
    public static class NodeResult {
        private String id;
        private String type;
        private String name;
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    public static class EdgeResult {
        private String fromId;
        private String toId;
        private String type;
    }
}