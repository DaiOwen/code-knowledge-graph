package com.example.ckg.service.qa;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GraphResult {
    private List<Node> nodes;
    private List<Relationship> relationships;
    private String rawCypher;

    @Data
    @Builder
    public static class Node {
        private String id;
        private List<String> labels;
        private Map<String, Object> properties;

        // Convenience method to get name
        public String getName() {
            if (properties != null && properties.containsKey("name")) {
                return (String) properties.get("name");
            }
            return id;
        }
    }

    @Data
    @Builder
    public static class Relationship {
        private String id;
        private String type;
        private String startNodeId;
        private String endNodeId;
        private Map<String, Object> properties;
    }

    // Legacy support - NodeResult maps to Node
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