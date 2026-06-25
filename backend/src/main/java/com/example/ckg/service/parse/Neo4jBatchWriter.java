package com.example.ckg.service.parse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jBatchWriter {

    private final Neo4jClient neo4jClient;

    private static final int BATCH_SIZE = 500;

    /**
     * Batch write method nodes
     */
    public int writeMethodNodes(Long projectId, List<CodeGraphResult.NodeData> nodes) {
        int total = 0;
        List<List<CodeGraphResult.NodeData>> batches = partition(nodes, BATCH_SIZE);

        for (List<CodeGraphResult.NodeData> batch : batches) {
            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (CodeGraphResult.NodeData node : batch) {
                nodeList.add(Map.of(
                    "projectId", projectId,
                    "name", node.getName() != null ? node.getName() : "",
                    "fullName", node.getFullName() != null ? node.getFullName() : "",
                    "filePath", node.getFilePath() != null ? node.getFilePath() : "",
                    "startLine", node.getStartLine() != null ? node.getStartLine() : 0,
                    "endLine", node.getEndLine() != null ? node.getEndLine() : 0,
                    "signature", node.getSignature() != null ? node.getSignature() : "",
                    "returnType", node.getReturnType() != null ? node.getReturnType() : ""
                ));
            }

            neo4jClient.query("""
                UNWIND $nodes AS node
                MERGE (m:Method {
                    projectId: node.projectId,
                    filePath: node.filePath,
                    name: node.name,
                    startLine: node.startLine
                })
                SET m.fullName = node.fullName,
                    m.endLine = node.endLine,
                    m.signature = node.signature,
                    m.returnType = node.returnType
                """)
                .bind(nodeList).to("nodes")
                .run();

            total += batch.size();
            log.debug("Written {} method nodes", total);
        }

        log.info("Total method nodes written: {}", total);
        return total;
    }

    /**
     * Batch write class nodes
     */
    public int writeClassNodes(Long projectId, List<CodeGraphResult.NodeData> nodes) {
        int total = 0;
        List<List<CodeGraphResult.NodeData>> batches = partition(nodes, BATCH_SIZE);

        for (List<CodeGraphResult.NodeData> batch : batches) {
            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (CodeGraphResult.NodeData node : batch) {
                nodeList.add(Map.of(
                    "projectId", projectId,
                    "name", node.getName() != null ? node.getName() : "",
                    "fullName", node.getFullName() != null ? node.getFullName() : "",
                    "filePath", node.getFilePath() != null ? node.getFilePath() : "",
                    "startLine", node.getStartLine() != null ? node.getStartLine() : 0,
                    "type", node.getType() != null ? node.getType() : "CLASS"
                ));
            }

            neo4jClient.query("""
                UNWIND $nodes AS node
                MERGE (c:Class {
                    projectId: node.projectId,
                    fullName: node.fullName
                })
                SET c.name = node.name,
                    c.filePath = node.filePath,
                    c.startLine = node.startLine,
                    c.type = node.type
                """)
                .bind(nodeList).to("nodes")
                .run();

            total += batch.size();
        }

        log.info("Total class nodes written: {}", total);
        return total;
    }

    /**
     * Batch write CALLS relationships
     */
    public int writeCallsEdges(Long projectId, List<CodeGraphResult.EdgeData> edges) {
        int total = 0;
        List<List<CodeGraphResult.EdgeData>> batches = partition(edges, BATCH_SIZE);

        for (List<CodeGraphResult.EdgeData> batch : batches) {
            List<Map<String, Object>> edgeList = new ArrayList<>();
            for (CodeGraphResult.EdgeData edge : batch) {
                edgeList.add(Map.of(
                    "projectId", projectId,
                    "fromName", edge.getFrom() != null ? edge.getFrom() : "",
                    "toName", edge.getTo() != null ? edge.getTo() : "",
                    "type", edge.getType() != null ? edge.getType() : "calls"
                ));
            }

            // Create CALLS relationship using method names
            neo4jClient.query("""
                UNWIND $edges AS edge
                MATCH (from:Method {projectId: edge.projectId, name: edge.fromName})
                MATCH (to:Method {projectId: edge.projectId, name: edge.toName})
                MERGE (from)-[r:CALLS]->(to)
                """)
                .bind(edgeList).to("edges")
                .run();

            total += batch.size();
            log.debug("Written {} CALLS edges", total);
        }

        log.info("Total CALLS edges written: {}", total);
        return total;
    }

    /**
     * Write HAS_METHOD relationships between Class and Method
     */
    public int writeHasMethodRelations(Long projectId, List<CodeGraphResult.NodeData> methods) {
        int total = 0;

        for (CodeGraphResult.NodeData method : methods) {
            if (method.getParentId() != null) {
                neo4jClient.query("""
                    MATCH (c:Class {projectId: $projectId, id: $classId})
                    MATCH (m:Method {projectId: $projectId, id: $methodId})
                    MERGE (c)-[:HAS_METHOD]->(m)
                    """)
                    .bind(projectId).to("projectId")
                    .bind(method.getParentId()).to("classId")
                    .bind(method.getId()).to("methodId")
                    .run();
                total++;
            }
        }

        log.info("Total HAS_METHOD relations written: {}", total);
        return total;
    }

    /**
     * Write commit nodes for traceability
     */
    public int writeCommitNodes(Long projectId, List<CommitInfo> commits) {
        int total = 0;
        List<List<CommitInfo>> batches = partition(commits, BATCH_SIZE);

        for (List<CommitInfo> batch : batches) {
            List<Map<String, Object>> commitList = new ArrayList<>();
            for (CommitInfo commit : batch) {
                commitList.add(Map.of(
                    "projectId", projectId,
                    "hash", commit.getHash(),
                    "message", commit.getMessage() != null ? commit.getMessage() : "",
                    "authorName", commit.getAuthorName() != null ? commit.getAuthorName() : "",
                    "authorEmail", commit.getAuthorEmail() != null ? commit.getAuthorEmail() : "",
                    "authoredAt", commit.getAuthoredAt() != null ? commit.getAuthoredAt().toString() : ""
                ));
            }

            neo4jClient.query("""
                UNWIND $commits AS commit
                MERGE (c:Commit {
                    projectId: commit.projectId,
                    hash: commit.hash
                })
                SET c.message = commit.message,
                    c.authoredAt = commit.authoredAt
                """)
                .bind(commitList).to("commits")
                .run();

            total += batch.size();
        }

        log.info("Total commit nodes written: {}", total);
        return total;
    }

    /**
     * Write author nodes
     */
    public int writeAuthorNodes(List<CommitInfo> commits) {
        Set<Map<String, String>> authors = new HashSet<>();
        for (CommitInfo commit : commits) {
            if (commit.getAuthorEmail() != null) {
                authors.add(Map.of(
                    "email", commit.getAuthorEmail(),
                    "name", commit.getAuthorName() != null ? commit.getAuthorName() : ""
                ));
            }
        }

        for (Map<String, String> author : authors) {
            neo4jClient.query("""
                MERGE (a:Author {email: $email})
                SET a.name = $name
                """)
                .bind(author.get("email")).to("email")
                .bind(author.get("name")).to("name")
                .run();
        }

        log.info("Total author nodes written: {}", authors.size());
        return authors.size();
    }

    /**
     * Delete all nodes for a project (for re-parse)
     */
    public void deleteProjectNodes(Long projectId) {
        neo4jClient.query("""
            MATCH (n) WHERE n.projectId = $projectId
            DETACH DELETE n
            """)
            .bind(projectId).to("projectId")
            .run();

        log.info("Deleted all nodes for project: {}", projectId);
    }

    /**
     * Delete nodes by file path (for incremental parse)
     */
    public void deleteNodesByFilePath(Long projectId, String filePath) {
        neo4jClient.query("""
            MATCH (n) WHERE n.projectId = $projectId AND n.filePath = $filePath
            DETACH DELETE n
            """)
            .bind(projectId).to("projectId")
            .bind(filePath).to("filePath")
            .run();

        log.info("Deleted nodes for file: {}", filePath);
    }

    /**
     * Delete a specific method node by name and project
     */
    public void deleteMethodNode(Long projectId, String methodName) {
        neo4jClient.query("""
            MATCH (m:Method) WHERE m.projectId = $projectId AND m.name = $name
            DETACH DELETE m
            """)
            .bind(projectId).to("projectId")
            .bind(methodName).to("name")
            .run();

        log.debug("Deleted method node: {}", methodName);
    }

    /**
     * Get count of nodes for a project
     */
    public long getNodeCount(Long projectId) {
        var result = neo4jClient.query("""
            MATCH (n) WHERE n.projectId = $projectId
            RETURN count(n) as count
            """)
            .bind(projectId).to("projectId")
            .fetch()
            .one();

        if (result.isPresent()) {
            return ((Number) result.get().get("count")).longValue();
        }
        return 0;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}