package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.security.FileSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/projects/{projectId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileSecurityService fileSecurityService;

    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> getFileTree(
        @PathVariable Long projectId,
        @RequestParam(required = false, defaultValue = ".") String path
    ) {
        try {
            Path projectRoot = fileSecurityService.getProjectRoot(projectId);
            Path targetPath = fileSecurityService.validatePath(projectId, path);

            if (Files.isDirectory(targetPath)) {
                List<Map<String, Object>> tree;
                try (Stream<Path> files = Files.list(targetPath)) {
                    tree = files.map(p -> Map.<String, Object>of(
                        "name", p.getFileName().toString(),
                        "path", projectRoot.relativize(p).toString(),
                        "isDirectory", Files.isDirectory(p)
                    )).collect(Collectors.toList());
                }
                return Result.success(tree);
            } else {
                return Result.success(List.of(Map.of(
                    "name", targetPath.getFileName().toString(),
                    "path", path,
                    "isDirectory", false
                )));
            }
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    @GetMapping("/{filePath:.*}")
    public Result<Map<String, Object>> getFileContent(
        @PathVariable Long projectId,
        @PathVariable String filePath,
        @RequestParam(required = false) Integer startLine,
        @RequestParam(required = false) Integer endLine
    ) {
        try {
            Path file = fileSecurityService.validatePath(projectId, filePath);
            if (!Files.isRegularFile(file)) {
                return Result.error(9001, "文件不存在");
            }

            List<String> allLines = Files.readAllLines(file);
            int totalLines = allLines.size();

            int start = startLine != null ? Math.max(0, startLine - 1) : 0;
            int end = endLine != null ? Math.min(totalLines, endLine) : totalLines;

            String content = String.join("\n", allLines.subList(start, end));

            return Result.success(Map.of(
                "path", filePath,
                "language", detectLanguage(filePath),
                "totalLines", totalLines,
                "startLine", start + 1,
                "endLine", end,
                "content", content
            ));
        } catch (Exception e) {
            return Result.error(9001, "读取文件失败: " + e.getMessage());
        }
    }

    private String detectLanguage(String fileName) {
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".vue")) return "vue";
        if (fileName.endsWith(".ts")) return "typescript";
        if (fileName.endsWith(".js")) return "javascript";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".xml")) return "xml";
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) return "yaml";
        return "plaintext";
    }
}