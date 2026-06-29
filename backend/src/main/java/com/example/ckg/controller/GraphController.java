package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.service.qa.GraphResult;
import com.example.ckg.service.qa.Neo4jExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
public class GraphController {

    private final Neo4jExecutor neo4jExecutor;

    /**
     * Get graph data for a project
     */
    @GetMapping("/{projectId}")
    public Result<GraphResult> getProjectGraph(
            @PathVariable Long projectId,
            @RequestParam(required = false) String nodeType,
            @RequestParam(required = false, defaultValue = "100") int limit) {

        String cypher;
        if (nodeType != null && !nodeType.isEmpty()) {
            cypher = String.format(
                "MATCH (n:%s {projectId: $projectId}) RETURN n LIMIT %d",
                nodeType, Math.min(limit, 500)
            );
        } else {
            cypher = String.format(
                "MATCH (n {projectId: $projectId}) RETURN n LIMIT %d",
                Math.min(limit, 500)
            );
        }

        GraphResult result = neo4jExecutor.execute(cypher, projectId);
        return Result.success(result);
    }

    /**
     * Get callers of a method
     */
    @GetMapping("/{projectId}/callers")
    public Result<Map<String, Object>> getCallers(
            @PathVariable Long projectId,
            @RequestParam String method) {

        validateMethodName(method);

        String cypher = String.format(
            "MATCH (m:Method {projectId: $projectId, name: \"%s\") " +
            "MATCH (caller:Method)-[:CALLS]->(m) " +
            "RETURN caller.name as name, caller.filePath as filePath, caller.startLine as startLine " +
            "LIMIT 50",
            escapeCypherString(method)
        );

        List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

        List<Map<String, Object>> callers = results.stream()
            .map(row -> {
                Map<String, Object> caller = new HashMap<>();
                caller.put("name", row.get("name"));
                caller.put("filePath", row.get("filePath"));
                caller.put("startLine", row.get("startLine"));
                return caller;
            })
            .collect(Collectors.toList());

        return Result.success(Map.of("callers", callers));
    }

    /**
     * Get callees of a method (what this method calls)
     */
    @GetMapping("/{projectId}/callees")
    public Result<Map<String, Object>> getCallees(
            @PathVariable Long projectId,
            @RequestParam String method) {

        validateMethodName(method);

        String cypher = String.format(
            "MATCH (m:Method {projectId: $projectId, name: \"%s\") " +
            "MATCH (m)-[:CALLS]->(callee:Method) " +
            "RETURN callee.name as name, callee.filePath as filePath, callee.startLine as startLine " +
            "LIMIT 50",
            escapeCypherString(method)
        );

        List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

        List<Map<String, Object>> callees = results.stream()
            .map(row -> {
                Map<String, Object> callee = new HashMap<>();
                callee.put("name", row.get("name"));
                callee.put("filePath", row.get("filePath"));
                callee.put("startLine", row.get("startLine"));
                return callee;
            })
            .collect(Collectors.toList());

        return Result.success(Map.of("callees", callees));
    }

    /**
     * Get impact analysis for a method
     */
    @GetMapping("/{projectId}/impact")
    public Result<GraphResult> getImpactAnalysis(
            @PathVariable Long projectId,
            @RequestParam String method,
            @RequestParam(required = false, defaultValue = "3") int depth) {

        validateMethodName(method);
        depth = Math.min(depth, 10);  // Limit depth to prevent excessive queries

        String cypher = String.format(
            "MATCH (m:Method {projectId: $projectId, name: \"%s\") " +
            "MATCH (m)-[:CALLS*1..%d]->(affected:Method) " +
            "RETURN DISTINCT affected as node " +
            "LIMIT 100",
            escapeCypherString(method), depth
        );

        GraphResult result = neo4jExecutor.execute(cypher, projectId);

        // Add the starting method as first node
        String startNodeCypher = String.format(
            "MATCH (m:Method {projectId: $projectId, name: \"%s\") RETURN m as node",
            escapeCypherString(method)
        );
        List<Map<String, Object>> startResult = neo4jExecutor.executeRaw(startNodeCypher, projectId);

        if (!startResult.isEmpty() && result.getNodes() != null) {
            Map<String, Object> startNode = startResult.get(0);
            GraphResult.Node node = GraphResult.Node.builder()
                .id(UUID.randomUUID().toString())
                .labels(List.of("Method"))
                .properties(startNode)
                .build();
            result.getNodes().add(0, node);
        }

        return Result.success(result);
    }

    /**
     * Search nodes by name
     */
    @GetMapping("/{projectId}/search")
    public Result<List<Map<String, Object>>> searchNodes(
            @PathVariable Long projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        if (q == null || q.trim().isEmpty()) {
            return Result.success(List.of());
        }

        validateSearchQuery(q);
        limit = Math.min(limit, 50);

        String cypher;
        if (type != null && !type.isEmpty()) {
            cypher = String.format(
                "MATCH (n:%s {projectId: $projectId}) " +
                "WHERE n.name CONTAINS \"%s\" " +
                "RETURN n.name as name, n.filePath as filePath, n.startLine as startLine, labels(n) as labels " +
                "LIMIT %d",
                type, escapeCypherString(q), limit
            );
        } else {
            cypher = String.format(
                "MATCH (n {projectId: $projectId}) " +
                "WHERE n.name CONTAINS \"%s\" " +
                "RETURN n.name as name, n.filePath as filePath, n.startLine as startLine, labels(n) as labels " +
                "LIMIT %d",
                escapeCypherString(q), limit
            );
        }

        List<Map<String, Object>> results = neo4jExecutor.executeRaw(cypher, projectId);

        return Result.success(results);
    }

    /**
     * Get node detail with relationships
     */
    @GetMapping("/{projectId}/node/{nodeId}")
    public Result<Map<String, Object>> getNodeDetail(
            @PathVariable Long projectId,
            @PathVariable String nodeId) {

        // nodeId format: "type:name:filePath:line" or just a name
        // Try to find by name first
        String cypher = String.format(
            "MATCH (n {projectId: $projectId}) " +
            "WHERE n.name = \"%s\" OR n.id = \"%s\" " +
            "RETURN n as node LIMIT 1",
            escapeCypherString(nodeId), escapeCypherString(nodeId)
        );

        List<Map<String, Object>> nodeResult = neo4jExecutor.executeRaw(cypher, projectId);

        if (nodeResult.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "节点不存在");
        }

        Map<String, Object> node = nodeResult.get(0);

        // Get incoming relationships (who calls/references this node)
        String incomingCypher = String.format(
            "MATCH (other {projectId: $projectId})-[r]->(n {projectId: $projectId}) " +
            "WHERE n.name = \"%s\" " +
            "RETURN type(r) as type, other.name as sourceName, other.filePath as sourceFile " +
            "LIMIT 20",
            escapeCypherString(nodeId)
        );
        List<Map<String, Object>> incoming = neo4jExecutor.executeRaw(incomingCypher, projectId);

        // Get outgoing relationships (what this node calls/references)
        String outgoingCypher = String.format(
            "MATCH (n {projectId: $projectId})-[r]->(other {projectId: $projectId}) " +
            "WHERE n.name = \"%s\" " +
            "RETURN type(r) as type, other.name as targetName, other.filePath as targetFile " +
            "LIMIT 20",
            escapeCypherString(nodeId)
        );
        List<Map<String, Object>> outgoing = neo4jExecutor.executeRaw(outgoingCypher, projectId);

        return Result.success(Map.of(
            "node", node,
            "relationships", Map.of(
                "incoming", incoming,
                "outgoing", outgoing
            )
        ));
    }

    /**
     * Validate method name to prevent Cypher injection
     */
    private void validateMethodName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "方法名不能为空");
        }
        // Only allow alphanumeric, underscore, dot, and common Java method characters
        if (!name.matches("^[a-zA-Z0-9_\\.\\$]+$")) {
            throw new BusinessException(ErrorCode.CYPHER_INJECTION, "非法的方法名格式");
        }
    }

    /**
     * Validate search query to prevent Cypher injection
     */
    private void validateSearchQuery(String query) {
        if (query.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "搜索关键词过长");
        }
        // Remove any dangerous characters
        if (query.contains("\"") || query.contains("'") || query.contains("\\") ||
            query.contains("$") || query.contains("{") || query.contains("}")) {
            throw new BusinessException(ErrorCode.CYPHER_INJECTION, "搜索关键词包含非法字符");
        }
    }

    /**
     * Escape string for safe Cypher query
     */
    private String escapeCypherString(String str) {
        if (str == null) return "";
        // Escape backslash and quotes
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}