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
     * Generate a unique node ID
     */
    private String generateNodeId(String name, String type, String filePath, int line) {
        return type + ":" + name + ":" + filePath + (line > 0 ? ":" + line : "");
    }
}