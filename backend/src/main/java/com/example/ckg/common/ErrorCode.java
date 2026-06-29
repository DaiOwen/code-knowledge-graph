package com.example.ckg.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 用户相关 1000-1999
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    TOKEN_INVALID(1003, "Token 无效"),
    TOKEN_EXPIRED(1004, "Token 已过期"),
    USER_EXISTS(1005, "用户名已存在"),
    EMAIL_EXISTS(1006, "邮箱已被使用"),

    // 项目相关 2000-1999
    PROJECT_NOT_FOUND(2001, "项目不存在"),
    PROJECT_PARSING(2002, "项目正在解析中"),
    GIT_CLONE_FAILED(2003, "Git 仓库克隆失败"),
    PROJECT_EXISTS(2004, "项目已存在"),
    GIT_ERROR(2005, "Git 操作失败"),

    // 解析相关 3000-3999
    MCP_CALL_FAILED(3001, "MCP 调用失败"),
    PARSE_TIMEOUT(3002, "解析超时"),
    UNSUPPORTED_LANGUAGE(3003, "不支持的编程语言"),
    NO_SOURCE_CODE(3004, "无源代码文件"),

    // LLM 相关 4000-4999
    LLM_CALL_FAILED(4001, "LLM 调用失败"),
    LLM_TOKEN_LIMIT(4002, "Token 超出限制"),
    LLM_TIMEOUT(4003, "LLM 响应超时"),

    // 图谱相关 5000-5999
    NEO4J_CONNECTION_FAILED(5001, "Neo4j 连接失败"),
    CYPHER_ERROR(5002, "Cypher 语法错误"),
    QUERY_TIMEOUT(5003, "查询超时"),

    // 安全相关 6000-6999
    PATH_TRAVERSAL(6001, "非法路径访问"),
    WEBHOOK_VALIDATION_FAILED(6002, "Webhook 验证失败"),
    CYPHER_INJECTION(6003, "Cypher 注入风险"),

    // 通用 9000-9999
    UNKNOWN_ERROR(9999, "系统内部错误"),
    PARAM_ERROR(9001, "参数错误");

    private final Integer code;
    private final String message;
}