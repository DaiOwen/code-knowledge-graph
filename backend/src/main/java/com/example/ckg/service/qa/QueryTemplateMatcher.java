package com.example.ckg.service.qa;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class QueryTemplateMatcher {

    private final Map<String, QueryTemplate> templates = new HashMap<>();

    public QueryTemplateMatcher() {
        // CALL_CHAIN template
        templates.put("CALL_CHAIN", QueryTemplate.builder()
            .intent("CALL_CHAIN")
            .keywords(new String[]{"谁调用了", "哪些地方调用", "调用者", "哪里调用了", "被谁调用"})
            .cypher("MATCH (m:Method {projectId: $projectId, name: $entity}) " +
                    "MATCH (caller:Method)-[:CALLS]->(m) " +
                    "RETURN caller.name, caller.filePath, caller.startLine")
            .entityType("method")
            .build());

        // IMPACT_ANALYSIS template
        templates.put("IMPACT_ANALYSIS", QueryTemplate.builder()
            .intent("IMPACT_ANALYSIS")
            .keywords(new String[]{"会影响", "影响范围", "修改后影响", "会波及"})
            .cypher("MATCH (m:Method {projectId: $projectId, name: $entity}) " +
                    "MATCH (m)-[:CALLS*1..5]->(affected:Method) " +
                    "RETURN DISTINCT affected.name, affected.filePath, affected.startLine")
            .entityType("method")
            .build());

        // AUTHOR_TRACE template
        templates.put("AUTHOR_TRACE", QueryTemplate.builder()
            .intent("AUTHOR_TRACE")
            .keywords(new String[]{"谁修改", "谁写的", "谁提交", "最后修改", "作者"})
            .cypher("MATCH (m:Method {projectId: $projectId, name: $entity}) " +
                    "MATCH (m)-[:LAST_MODIFIED_BY]->(c:Commit) " +
                    "MATCH (c)-[:AUTHORED_BY]->(a:Author) " +
                    "RETURN c.message, c.authoredAt, a.name, a.email")
            .entityType("method")
            .build());

        // CLASS_METHODS template
        templates.put("CLASS_METHODS", QueryTemplate.builder()
            .intent("CLASS_METHODS")
            .keywords(new String[]{"有哪些方法", "方法列表", "包含哪些方法"})
            .cypher("MATCH (c:Class {projectId: $projectId, name: $entity}) " +
                    "MATCH (c)-[:HAS_METHOD]->(m:Method) " +
                    "RETURN m.name, m.signature, m.filePath, m.startLine")
            .entityType("class")
            .build());

        // METHOD_HISTORY template
        templates.put("METHOD_HISTORY", QueryTemplate.builder()
            .intent("METHOD_HISTORY")
            .keywords(new String[]{"修改历史", "提交记录", "变更历史", "git历史"})
            .cypher("MATCH (m:Method {projectId: $projectId, name: $entity}) " +
                    "MATCH (m)-[:MODIFIED_BY]->(c:Commit) " +
                    "MATCH (c)-[:AUTHORED_BY]->(a:Author) " +
                    "RETURN c.hash, c.message, c.authoredAt, a.name " +
                    "ORDER BY c.authoredAt DESC LIMIT 10")
            .entityType("method")
            .build());
    }

    public Optional<QueryTemplate> match(IntentResult intent) {
        QueryTemplate template = templates.get(intent.getIntent());
        if (template != null) {
            return Optional.of(template);
        }
        return Optional.empty();
    }

    public Optional<QueryTemplate> matchByKeywords(String question) {
        for (QueryTemplate template : templates.values()) {
            for (String keyword : template.getKeywords()) {
                if (question.contains(keyword)) {
                    return Optional.of(template);
                }
            }
        }
        return Optional.empty();
    }

    @Data
    @Builder
    public static class QueryTemplate {
        private String intent;
        private String[] keywords;
        private String cypher;
        private String entityType;
    }
}