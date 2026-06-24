package com.example.ckg.service.parse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberInfo {
    private String name;
    private String type;       // method, field
    private String signature;
}