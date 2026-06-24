package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NodeDetail {
    private String filePath;
    private Integer line;
    private List<MemberInfo> members;
}