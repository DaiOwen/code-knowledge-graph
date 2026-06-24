package com.example.ckg.security;

import com.example.ckg.common.ErrorCode;
import com.example.ckg.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileSecurityService {

    private static final String BASE_PATH = "./repos";

    public Path getProjectRoot(Long projectId) {
        return Paths.get(BASE_PATH, "project_" + projectId).normalize();
    }

    public Path validatePath(Long projectId, String filePath) {
        Path projectRoot = getProjectRoot(projectId);
        Path resolvedPath = projectRoot.resolve(filePath).normalize();

        // Security check: ensure the resolved path is within project root
        if (!resolvedPath.startsWith(projectRoot)) {
            throw new BusinessException(ErrorCode.PATH_TRAVERSAL);
        }

        // Check if path exists
        if (!Files.exists(resolvedPath)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "路径不存在: " + filePath);
        }

        return resolvedPath;
    }
}