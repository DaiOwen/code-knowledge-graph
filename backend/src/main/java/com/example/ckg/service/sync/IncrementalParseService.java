package com.example.ckg.service.sync;

import com.example.ckg.entity.ParseTask;
import com.example.ckg.entity.Project;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.common.ErrorCode;
import com.example.ckg.repository.ParseTaskRepository;
import com.example.ckg.repository.ProjectRepository;
import com.example.ckg.service.parse.McpClientService;
import com.example.ckg.service.parse.Neo4jBatchWriter;
import com.example.ckg.service.parse.CodeGraphOutputParser;
import com.example.ckg.service.parse.CodeGraphResult;
import com.example.ckg.service.parse.FileInfo;
import com.example.ckg.service.parse.SymbolInfo;
import com.example.ckg.service.parse.CallRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewetwalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncrementalParseService {

    private final ProjectRepository projectRepository;
    private final ParseTaskRepository parseTaskRepository;
    private final McpClientService mcpClientService;
    private final CodeGraphOutputParser outputParser;
    private final Neo4jBatchWriter neo4jWriter;

    /**
     * Parse only changed files incrementally
     */
    @Async
    public void parseIncremental(Long projectId, String fromCommit, String toCommit, String[] changedFiles) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        log.info("Starting incremental parse for project {}: {} files changed",
            projectId, changedFiles.length);

        try {
            // Create parse task
            ParseTask task = ParseTask.builder()
                .projectId(projectId)
                .type(ParseTask.ParseType.INCREMENTAL)
                .status(ParseTask.ParseStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .progress(0)
                .build();
            task = parseTaskRepository.save(task);

            // Categorize changed files
            List<String> toAdd = new ArrayList<>();
            List<String> toModify = new ArrayList<>();
            List<String> toDelete = new ArrayList<>();

            categorizeChanges(project.getLocalPath(), fromCommit, toCommit, toAdd, toModify, toDelete);

            task.setProgress(20);
            parseTaskRepository.save(task);

            // Start MCP client
            mcpClientService.startCodegraph(projectId, Path.of(project.getLocalPath()));

            // Parse new and modified files
            Set<CodeGraphResult.NodeData> newNodes = new HashSet<>();
            Set<CodeGraphResult.EdgeData> newEdges = new HashSet<>();

            List<String> toParse = new ArrayList<>();
            toParse.addAll(toAdd);
            toParse.addAll(toModify);

            task.setProgress(30);
            parseTaskRepository.save(task);

            for (String filePath : toParse) {
                try {
                    // Get file content and parse
                    String searchResult = mcpClientService.callSearchTool(projectId, filePath, "method", 100);
                    List<SymbolInfo> methods = outputParser.parseSearchResult(searchResult);

                    for (SymbolInfo method : methods) {
                        String nodeId = method.getType() + ":" + method.getName() + ":" + method.getFilePath();
                        newNodes.add(CodeGraphResult.NodeData.builder()
                            .id(nodeId)
                            .type(method.getType())
                            .name(method.getName())
                            .filePath(method.getFilePath())
                            .startLine(method.getLine())
                            .signature(method.getSignature())
                            .build());

                        // Get call relationships
                        String calleesResult = mcpClientService.callCalleesTool(projectId, method.getName(), 20);
                        List<CallRelation> callees = outputParser.parseCalleesResult(calleesResult, method.getName());

                        for (CallRelation rel : callees) {
                            newEdges.add(CodeGraphResult.EdgeData.builder()
                                .from(rel.getCallerName())
                                .to(rel.getCalleeName())
                                .type("calls")
                                .build());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file: {}", filePath, e);
                }
            }

            task.setProgress(60);
            parseTaskRepository.save(task);

            // Delete removed nodes from Neo4j
            for (String filePath : toDelete) {
                neo4jWriter.deleteNodesByFilePath(projectId, filePath);
            }

            task.setProgress(70);
            parseTaskRepository.save(task);

            // Write new/updated nodes
            List<CodeGraphResult.NodeData> nodeList = new ArrayList<>(newNodes);
            List<CodeGraphResult.EdgeData> edgeList = new ArrayList<>(newEdges);

            neo4jWriter.writeMethodNodes(projectId, nodeList.stream()
                .filter(n -> "method".equals(n.getType()))
                .collect(Collectors.toList()));
            neo4jWriter.writeClassNodes(projectId, nodeList.stream()
                .filter(n -> "class".equals(n.getType()) || "interface".equals(n.getType()))
                .collect(Collectors.toList()));
            neo4jWriter.writeCallsEdges(projectId, edgeList);

            task.setProgress(90);
            parseTaskRepository.save(task);

            // Stop MCP client
            mcpClientService.stopCodegraph(projectId);

            // Update task status
            task.setStatus(ParseTask.ParseStatus.COMPLETED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            // Update project
            project.setLastParsedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("Incremental parse completed for project {}", projectId);

        } catch (Exception e) {
            log.error("Incremental parse failed for project {}", projectId, e);

            ParseTask task = parseTaskRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .orElse(new ParseTask());
            task.setStatus(ParseTask.ParseStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            parseTaskRepository.save(task);

            mcpClientService.stopCodegraph(projectId);
        }
    }

    private void categorizeChanges(String repoPath, String fromCommit, String toCommit,
                                   List<String> toAdd, List<String> toModify, List<String> toDelete) {
        try {
            Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoPath, ".git"))
                .build();

            try (Git git = new Git(repository)) {
                ObjectId oldHead = repository.resolve(fromCommit);
                ObjectId newHead = repository.resolve(toCommit);

                if (oldHead == null || newHead == null) {
                    return;
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(out);
                formatter.setRepository(repository);

                List<DiffEntry> diffs = formatter.scan(oldHead, newHead);

                for (DiffEntry entry : diffs) {
                    String oldPath = entry.getOldPath();
                    String newPath = entry.getNewPath();

                    switch (entry.getChangeType()) {
                        case ADD:
                            if (isCodeFile(newPath)) {
                                toAdd.add(newPath);
                            }
                            break;
                        case MODIFY:
                            if (isCodeFile(newPath)) {
                                toModify.add(newPath);
                            }
                            break;
                        case DELETE:
                            if (isCodeFile(oldPath)) {
                                toDelete.add(oldPath);
                            }
                            break;
                        case RENAME:
                            if (isCodeFile(oldPath)) {
                                toDelete.add(oldPath);
                            }
                            if (isCodeFile(newPath)) {
                                toAdd.add(newPath);
                            }
                            break;
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to categorize changes", e);
        }
    }

    private boolean isCodeFile(String path) {
        if (path == null || path.isEmpty()) return false;

        String[] codeExtensions = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx",
            ".py", ".go", ".rs", ".c", ".cpp", ".h", ".vue"};

        for (String ext : codeExtensions) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }
}