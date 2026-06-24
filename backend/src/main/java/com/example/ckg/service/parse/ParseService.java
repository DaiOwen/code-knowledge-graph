package com.example.ckg.service.parse;

import com.example.ckg.entity.ParseTask;
import com.example.ckg.entity.Project;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ParseTaskRepository;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.service.project.CommitInfo;
import com.example.ckg.service.project.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseService {

    private final ProjectRepository projectRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final GitService gitService;
    private final McpClientService mcpClientService;
    private final CodeGraphOutputParser outputParser;
    private final Neo4jBatchWriter neo4jWriter;

    @Async
    @Transactional
    public void parseProject(Long projectId, Long taskId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        ParseTask task = parseTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "任务不存在"));

        try {
            // Update task status
            task.setStatus(ParseTask.ParseStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(5);
            parseTaskRepository.save(task);

            // Step 1: Clone repository (if not exists)
            log.info("Step 1: Clone repository for project {}", projectId);
            String localPath = project.getLocalPath();
            if (localPath == null || !Files.exists(Path.of(localPath))) {
                localPath = gitService.cloneRepository(
                    project.getGitUrl(),
                    project.getName(),
                    project.getBranch(),
                    null // TODO: Add credentials
                );
                project.setLocalPath(localPath);
                projectRepository.save(project);
            }
            task.setProgress(20);
            parseTaskRepository.save(task);

            // Step 2: Start codegraph MCP
            log.info("Step 2: Start codegraph MCP for project {}", projectId);
            mcpClientService.startCodegraph(projectId, Path.of(localPath));
            task.setProgress(30);
            parseTaskRepository.save(task);

            // Step 3: Parse code structure using multiple codegraph calls
            log.info("Step 3: Parse code structure");
            List<CodeGraphResult.NodeData> allNodes = new ArrayList<>();
            List<CodeGraphResult.EdgeData> allEdges = new ArrayList<>();
            Set<String> seenNodeIds = new HashSet<>();
            Set<String> seenEdgeIds = new HashSet<>();

            // 3.1 Get all files
            String filesResult = mcpClientService.callFilesTool(projectId);
            List<FileInfo> files = outputParser.parseFilesResult(filesResult);
            log.info("Found {} files", files.size());
            task.setProgress(35);
            parseTaskRepository.save(task);

            // 3.2 Search for all classes
            String classSearchResult = mcpClientService.callSearchTool(projectId, "", "class", 100);
            List<SymbolInfo> classes = outputParser.parseSearchResult(classSearchResult);
            for (SymbolInfo symbol : classes) {
                String nodeId = generateNodeId(symbol.getName(), "class", symbol.getFilePath(), symbol.getLine());
                if (seenNodeIds.add(nodeId)) {
                    allNodes.add(CodeGraphResult.NodeData.builder()
                        .id(nodeId)
                        .type("class")
                        .name(symbol.getName())
                        .filePath(symbol.getFilePath())
                        .startLine(symbol.getLine())
                        .signature(symbol.getSignature())
                        .build());
                }
            }
            log.info("Found {} classes", classes.size());

            // 3.3 Search for all methods
            String methodSearchResult = mcpClientService.callSearchTool(projectId, "", "method", 500);
            List<SymbolInfo> methods = outputParser.parseSearchResult(methodSearchResult);
            for (SymbolInfo symbol : methods) {
                String nodeId = generateNodeId(symbol.getName(), "method", symbol.getFilePath(), symbol.getLine());
                if (seenNodeIds.add(nodeId)) {
                    allNodes.add(CodeGraphResult.NodeData.builder()
                        .id(nodeId)
                        .type("method")
                        .name(symbol.getName())
                        .filePath(symbol.getFilePath())
                        .startLine(symbol.getLine())
                        .signature(symbol.getSignature())
                        .build());
                }
            }
            log.info("Found {} methods", methods.size());
            task.setProgress(45);
            parseTaskRepository.save(task);

            // 3.4 Get call relationships for each method
            int methodCount = 0;
            for (SymbolInfo method : methods) {
                try {
                    // Get callees (what this method calls)
                    String calleesResult = mcpClientService.callCalleesTool(projectId, method.getName(), 50);
                    List<CallRelation> callees = outputParser.parseCalleesResult(calleesResult, method.getName());

                    for (CallRelation relation : callees) {
                        String edgeId = relation.getCallerName() + "->" + relation.getCalleeName();
                        if (seenEdgeIds.add(edgeId)) {
                            allEdges.add(CodeGraphResult.EdgeData.builder()
                                .from(relation.getCallerName())
                                .to(relation.getCalleeName())
                                .type("calls")
                                .build());
                        }

                        // Add callee as node if not exists
                        String calleeNodeId = generateNodeId(relation.getCalleeName(), relation.getCalleeType(),
                            relation.getCalleeFile(), relation.getCalleeLine());
                        if (seenNodeIds.add(calleeNodeId)) {
                            allNodes.add(CodeGraphResult.NodeData.builder()
                                .id(calleeNodeId)
                                .type(relation.getCalleeType())
                                .name(relation.getCalleeName())
                                .filePath(relation.getCalleeFile())
                                .startLine(relation.getCalleeLine())
                                .build());
                        }
                    }

                    methodCount++;
                    if (methodCount % 10 == 0) {
                        log.info("Processed {}/{} methods for call relationships", methodCount, methods.size());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get callees for method {}: {}", method.getName(), e.getMessage());
                }
            }
            task.setProgress(55);
            parseTaskRepository.save(task);

            log.info("Total nodes: {}, Total edges: {}", allNodes.size(), allEdges.size());

            // Step 4: Write to Neo4j
            log.info("Step 4: Write to Neo4j");

            // Separate class and method nodes
            List<CodeGraphResult.NodeData> classNodes = allNodes.stream()
                .filter(n -> "class".equals(n.getType()) || "interface".equals(n.getType()))
                .collect(Collectors.toList());
            List<CodeGraphResult.NodeData> methodNodes = allNodes.stream()
                .filter(n -> "method".equals(n.getType()))
                .collect(Collectors.toList());

            neo4jWriter.writeClassNodes(projectId, classNodes);
            neo4jWriter.writeMethodNodes(projectId, methodNodes);
            neo4jWriter.writeCallsEdges(projectId, allEdges);
            task.setProgress(70);
            parseTaskRepository.save(task);

            // Step 5: Parse git history
            log.info("Step 5: Parse git history");
            List<CommitInfo> commits = gitService.getCommitHistory(localPath, 1000);
            neo4jWriter.writeCommitNodes(projectId, commits);
            neo4jWriter.writeAuthorNodes(commits);
            task.setProgress(90);
            parseTaskRepository.save(task);

            // Step 6: Cleanup
            mcpClientService.stopCodegraph(projectId);

            // Update status
            task.setStatus(ParseTask.ParseStatus.COMPLETED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            project.setStatus(Project.ProjectStatus.READY);
            project.setLastParsedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Parse completed for project {}", projectId);

        } catch (Exception e) {
            log.error("Parse failed for project {}", projectId, e);
            task.setStatus(ParseTask.ParseStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            project.setStatus(Project.ProjectStatus.ERROR);
            projectRepository.save(project);

            mcpClientService.stopCodegraph(projectId);
        }
    }

    /**
     * Parse nodes from codegraph output.
     *
     * Parses multiple codegraph output formats:
     * 1. Search results: "### OrderService (class)\nsrc/main/java/.../OrderService.java:3"
     * 2. Explore output with blast radius
     * 3. Callees/Callers results
     */
    private List<CodeGraphResult.NodeData> parseNodes(String exploreResult) {
        List<CodeGraphResult.NodeData> nodes = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // Pattern 1: Search result format - "### SymbolName (type)\nfilePath:line"
        Pattern searchPattern = Pattern.compile(
            "###\\s+(\\w+)\\s+\\((\\w+)\\)\\s*\\n\\s*([^:\\s]+):(\\d+)"
        );
        Matcher searchMatcher = searchPattern.matcher(exploreResult);
        while (searchMatcher.find()) {
            String name = searchMatcher.group(1);
            String type = searchMatcher.group(2).toLowerCase();
            String filePath = searchMatcher.group(3).trim();
            int line = Integer.parseInt(searchMatcher.group(4));
            String id = generateNodeId(name, type, filePath, line);

            if (seenIds.add(id)) {
                nodes.add(CodeGraphResult.NodeData.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .filePath(filePath)
                    .startLine(line)
                    .build());
            }
        }

        // Pattern 2: Blast radius format - "`symbolName` (filePath:line)"
        Pattern blastPattern = Pattern.compile(
            "`(\\w+)`\\s+\\(([^:]+):(\\d+)\\)"
        );
        Matcher blastMatcher = blastPattern.matcher(exploreResult);
        while (blastMatcher.find()) {
            String name = blastMatcher.group(1);
            String filePath = blastMatcher.group(2).trim();
            int line = Integer.parseInt(blastMatcher.group(3));
            String type = inferTypeFromName(name);
            String id = generateNodeId(name, type, filePath, line);

            if (seenIds.add(id)) {
                nodes.add(CodeGraphResult.NodeData.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .filePath(filePath)
                    .startLine(line)
                    .build());
            }
        }

        // Pattern 3: Callees/Callers format - "- symbolName (type) - filePath:line"
        Pattern calleesPattern = Pattern.compile(
            "-\\s+(\\w+)\\s+\\((\\w+)\\)\\s+-\\s+([^:]+):(\\d+)"
        );
        Matcher calleesMatcher = calleesPattern.matcher(exploreResult);
        while (calleesMatcher.find()) {
            String name = calleesMatcher.group(1);
            String type = calleesMatcher.group(2).toLowerCase();
            String filePath = calleesMatcher.group(3).trim();
            int line = Integer.parseInt(calleesMatcher.group(4));
            String id = generateNodeId(name, type, filePath, line);

            if (seenIds.add(id)) {
                nodes.add(CodeGraphResult.NodeData.builder()
                    .id(id)
                    .type(type)
                    .name(name)
                    .filePath(filePath)
                    .startLine(line)
                    .build());
            }
        }

        // Pattern 4: Code block header - "#### filePath — symbolName(type), symbolName(type), ..."
        Pattern codeHeaderPattern = Pattern.compile(
            "####\\s+([^\\s]+)\\s+—\\s+(.+)"
        );
        Matcher codeHeaderMatcher = codeHeaderPattern.matcher(exploreResult);
        while (codeHeaderMatcher.find()) {
            String filePath = codeHeaderMatcher.group(1).trim();
            String symbolsPart = codeHeaderMatcher.group(2);

            // Extract symbols from header like "createOrder(method), OrderService(class)"
            Pattern symbolInHeader = Pattern.compile("(\\w+)\\((\\w+)\\)");
            Matcher symbolMatcher = symbolInHeader.matcher(symbolsPart);
            while (symbolMatcher.find()) {
                String name = symbolMatcher.group(1);
                String type = symbolMatcher.group(2).toLowerCase();
                String id = generateNodeId(name, type, filePath, 0);

                if (seenIds.add(id)) {
                    nodes.add(CodeGraphResult.NodeData.builder()
                        .id(id)
                        .type(type)
                        .name(name)
                        .filePath(filePath)
                        .build());
                }
            }
        }

        log.info("Parsed {} unique nodes from codegraph output", nodes.size());
        return nodes;
    }

    /**
     * Parse edges (call relationships) from codegraph output.
     *
     * Parses:
     * 1. Callees results - who this method calls
     * 2. Callers results - who calls this method
     * 3. Blast radius - caller counts indicate relationships
     */
    private List<CodeGraphResult.EdgeData> parseEdges(String exploreResult) {
        List<CodeGraphResult.EdgeData> edges = new ArrayList<>();
        Set<String> seenEdges = new HashSet<>();

        // Pattern 1: Callees format - caller is known from context
        // We need to parse this in context of which symbol we queried
        Pattern calleesPattern = Pattern.compile(
            "## Callees of (\\w+)\\s+.*\\n\\n([\\s\\S]*?)(?=\\n##|$)"
        );
        Matcher calleesSectionMatcher = calleesPattern.matcher(exploreResult);
        while (calleesSectionMatcher.find()) {
            String callerName = calleesSectionMatcher.group(1);
            String calleesSection = calleesSectionMatcher.group(2);

            Pattern calleePattern = Pattern.compile(
                "-\\s+(\\w+)\\s+\\((\\w+)\\)\\s+-\\s+([^:]+):(\\d+)"
            );
            Matcher calleeMatcher = calleePattern.matcher(calleesSection);
            while (calleeMatcher.find()) {
                String calleeName = calleeMatcher.group(1);
                String calleeType = calleeMatcher.group(2).toLowerCase();
                String calleeFile = calleeMatcher.group(3).trim();

                String edgeId = callerName + "->" + calleeName;
                if (seenEdges.add(edgeId)) {
                    edges.add(CodeGraphResult.EdgeData.builder()
                        .from(callerName)
                        .to(calleeName)
                        .type("calls")
                        .build());
                }
            }
        }

        // Pattern 2: Callers format
        Pattern callersPattern = Pattern.compile(
            "## Callers of (\\w+)\\s+.*\\n\\n([\\s\\S]*?)(?=\\n##|$)"
        );
        Matcher callersSectionMatcher = callersPattern.matcher(exploreResult);
        while (callersSectionMatcher.find()) {
            String calleeName = callersSectionMatcher.group(1);
            String callersSection = callersSectionMatcher.group(2);

            Pattern callerPattern = Pattern.compile(
                "-\\s+(\\w+)\\s+\\((\\w+)\\)\\s+-\\s+([^:]+):(\\d+)"
            );
            Matcher callerMatcher = callerPattern.matcher(callersSection);
            while (callerMatcher.find()) {
                String callerName = callerMatcher.group(1);

                String edgeId = callerName + "->" + calleeName;
                if (seenEdges.add(edgeId)) {
                    edges.add(CodeGraphResult.EdgeData.builder()
                        .from(callerName)
                        .to(calleeName)
                        .type("calls")
                        .build());
                }
            }
        }

        // Pattern 3: Blast radius - extract from "X callers" or "X callees"
        Pattern blastRadiusPattern = Pattern.compile(
            "`(\\w+)`[^\\n]*?(\\d+)\\s+caller"
        );
        Matcher blastMatcher = blastRadiusPattern.matcher(exploreResult);
        while (blastMatcher.find()) {
            String symbolName = blastMatcher.group(1);
            int callerCount = Integer.parseInt(blastMatcher.group(2));
            // Note: This indicates there are relationships but we need actual caller info
            // For now, we just log this information
            log.debug("Symbol {} has {} callers", symbolName, callerCount);
        }

        // Pattern 4: Source code analysis - detect method calls in code blocks
        // Extract from code blocks like "validateOrder(request);" which indicates a call
        Pattern codeBlockPattern = Pattern.compile("```\\w*\\n([\\s\\S]*?)```");
        Matcher codeBlockMatcher = codeBlockPattern.matcher(exploreResult);
        while (codeBlockMatcher.find()) {
            String code = codeBlockMatcher.group(1);
            // Find method calls like "methodName(" in the code
            Set<String> methodsInCode = new HashSet<>();
            Pattern methodCallPattern = Pattern.compile("\\b(\\w+)\\s*\\(");
            Matcher methodCallMatcher = methodCallPattern.matcher(code);
            while (methodCallMatcher.find()) {
                methodsInCode.add(methodCallMatcher.group(1));
            }

            // If we found multiple methods in same code block, they might have call relationships
            // This is a heuristic - actual calls need proper parsing
            if (methodsInCode.size() > 1) {
                log.debug("Found {} methods in code block: {}", methodsInCode.size(), methodsInCode);
            }
        }

        log.info("Parsed {} unique edges from codegraph output", edges.size());
        return edges;
    }

    /**
     * Generate a unique node ID
     */
    private String generateNodeId(String name, String type, String filePath, int line) {
        return type + ":" + name + ":" + filePath + (line > 0 ? ":" + line : "");
    }

    /**
     * Infer symbol type from naming conventions
     */
    private String inferTypeFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        // Common method name patterns
        if (name.startsWith("get") || name.startsWith("set") || name.startsWith("is") ||
            name.startsWith("has") || name.startsWith("can") || name.startsWith("should") ||
            name.startsWith("add") || name.startsWith("remove") || name.startsWith("update") ||
            name.startsWith("delete") || name.startsWith("create") || name.startsWith("find") ||
            name.startsWith("save") || name.startsWith("load") || name.startsWith("validate") ||
            name.startsWith("check") || name.startsWith("build") || name.startsWith("calculate") ||
            Character.isLowerCase(name.charAt(0))) {
            return "method";
        }
        // Class names typically start with uppercase
        if (Character.isUpperCase(name.charAt(0))) {
            return "class";
        }
        return "unknown";
    }
}