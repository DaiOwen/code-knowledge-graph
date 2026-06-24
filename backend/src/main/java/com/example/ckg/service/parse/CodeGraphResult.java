package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CodeGraphResult {

    private List<NodeData> nodes;
    private List<EdgeData> edges;

    @Data
    @Builder
    public static class NodeData {
        private String id;
        private String type;       // class, method, field, interface
        private String name;
        private String fullName;
        private String filePath;
        private Integer startLine;
        private Integer endLine;
        private String signature;
        private String returnType;
        private String parentId;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    public static class EdgeData {
        private String from;
        private String to;
        private String type;       // calls, extends, implements
        private Map<String, Object> attributes;
    }
}