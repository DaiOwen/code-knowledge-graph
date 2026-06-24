package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.repository.ParseTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
public class GraphController {

    private final ParseTaskRepository taskRepository;

    @GetMapping("/{projectId}/search")
    public Result<Map<String, Object>> searchNodes(
        @PathVariable Long projectId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        return Result.success(Map.of(
            "message", "图谱搜索功能开发中",
            "query", q,
            "projectId", projectId
        ));
    }
}