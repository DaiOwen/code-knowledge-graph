package com.example.ckg.service.qa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jExecutor {

    private final Neo4jClient neo4jClient;

    private static final long QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESULTS = 100;

    /**
     * Execute Cypher query and return graph result
     */
    public GraphResult execute(String cypher, Long projectId) {
        try {
            List<GraphResult.Node> nodes = new ArrayList<>();
            List<GraphResult.Relationship> relationships = new ArrayList<>();

            neo4jClient.query(cypher)
                .bind(projectId).to("projectId")
                .fetch()
                .one()
                .ifPresent(record -> {
                    // Parse result based on query type
                    // This is a simplified implementation
                    log.debug("Query returned record: {}", record);
                });

            // For now, we need to parse the actual results
            // Let's use a more direct approach
            var resultSet = neo4jClient.query(cypher)
                .bind(projectId).to("projectId")
                .fetch()
                .all();

            for (var record : resultSet) {
                // Parse nodes from result
                parseRecord(record, nodes, relationships);
            }

            log.info("Query returned {} nodes, {} relationships", nodes.size(), relationships.size());

            return GraphResult.builder()
                .nodes(nodes)
                .relationships(relationships)
                .build();

        } catch (Exception e) {
            log.error("Cypher execution failed: {}", e.getMessage());
            throw new RuntimeException("查询执行失败: " + e.getMessage());
        }
    }

    /**
     * Execute query and return raw results
     */
    public List<Map<String, Object>> executeRaw(String cypher, Long projectId) {
        try {
            List<Map<String, Object>> results = new ArrayList<>();

            neo4jClient.query(cypher)
                .bind(projectId).to("projectId")
                .fetch()
                .all()
                .forEach(record -> {
                    Map<String, Object> row = new HashMap<>();
                    record.forEach((key, value) -> {
                        row.put(key, extractValue(value));
                    });
                    results.add(row);
                });

            return results;

        } catch (Exception e) {
            log.error("Cypher execution failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void parseRecord(Map<String, Object> record,
                             List<GraphResult.Node> nodes,
                             List<GraphResult.Relationship> relationships) {
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof org.neo4j.driver.types.Node nodeValue) {
                nodes.add(GraphResult.Node.builder()
                    .id(String.valueOf(nodeValue.id()))
                    .labels(new ArrayList<>(nodeValue.labels()))
                    .properties(convertProperties(nodeValue.asMap()))
                    .build());
            } else if (value instanceof org.neo4j.driver.types.Relationship relValue) {
                relationships.add(GraphResult.Relationship.builder()
                    .id(String.valueOf(relValue.id()))
                    .type(relValue.type())
                    .startNodeId(String.valueOf(relValue.startNodeId()))
                    .endNodeId(String.valueOf(relValue.endNodeId()))
                    .properties(convertProperties(relValue.asMap()))
                    .build());
            } else if (value instanceof Map mapValue) {
                // Could be a projection result
                nodes.add(GraphResult.Node.builder()
                    .id(UUID.randomUUID().toString())
                    .labels(Collections.emptyList())
                    .properties(convertProperties(mapValue))
                    .build());
            }
        }
    }

    private Map<String, Object> convertProperties(Map<String, Object> props) {
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            converted.put(entry.getKey(), extractValue(entry.getValue()));
        }
        return converted;
    }

    private Object extractValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof org.neo4j.driver.Value neoValue) {
            return neoValue.asObject();
        }
        if (value instanceof org.neo4j.driver.types.Node node) {
            return node.asMap();
        }
        if (value instanceof org.neo4j.driver.types.Relationship rel) {
            return rel.asMap();
        }
        return value;
    }
}