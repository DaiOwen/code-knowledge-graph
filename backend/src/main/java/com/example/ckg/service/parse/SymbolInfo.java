package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SymbolInfo {
    private String name;
    private String type;       // class, method, field
    private String filePath;
    private Integer line;
    private String signature;
}