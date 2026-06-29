package com.example.ckg.service.qa;

import com.example.ckg.common.ErrorCode;
import com.example.ckg.exception.BusinessException;
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

        } catch (BusinessException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Cypher execution failed: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CYPHER_ERROR, "查询执行失败: " + e.getMessage(), e);
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

        } catch (BusinessException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Cypher execution failed: {}", e.getMessage(), e);
            // Return empty list instead of throwing for raw queries
            // This allows callers to handle empty results gracefully
            return Collections.emptyList();
        }
    }

    /**
     * Execute query and return count
     */
    public long executeCount(String cypher, Long projectId) {
        try {
            var result = neo4jClient.query(cypher)
                .bind(projectId).to("projectId")
                .fetch()
                .one();

            if (result.isPresent()) {
                Object count = result.get().get("count");
                if (count instanceof Number) {
                    return ((Number) count).longValue();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("Count query failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Execute query without returning results (for mutations)
     */
    public void executeUpdate(String cypher, Long projectId) {
        try {
            neo4jClient.query(cypher)
                .bind(projectId).to("projectId")
                .run();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Update query failed: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CYPHER_ERROR, "更新操作失败: " + e.getMessage(), e);
        }
    }

    private void parseRecord(Map<String, Object> record,
                             List<GraphResult.Node> nodes,
                             List<GraphResult.Relationship> relationships) {
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof org.neo4j.driver.types.Node nodeValue) {
                List<String> labelList = new ArrayList<>();
                for (String label : nodeValue.labels()) {
                    labelList.add(label);
                }
                nodes.add(GraphResult.Node.builder()
                    .id(String.valueOf(nodeValue.id()))
                    .labels(labelList)
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