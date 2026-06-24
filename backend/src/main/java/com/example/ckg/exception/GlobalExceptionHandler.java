package com.example.ckg.exception;

import com.example.ckg.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(getHttpStatus(e.getCode()))
            .body(Result.<Void>builder()
                .success(false)
                .code(e.getCode())
                .message(e.getMessage())
                .traceId(MDC.get("traceId"))
                .build());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Result.<Void>builder()
                .success(false)
                .code(1002)
                .message("认证失败")
                .traceId(MDC.get("traceId"))
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Result.<Void>builder()
                .success(false)
                .code(1003)
                .message("权限不足")
                .traceId(MDC.get("traceId"))
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Result.<Void>builder()
                .success(false)
                .code(9001)
                .message(message)
                .traceId(MDC.get("traceId"))
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("未知异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.<Void>builder()
                .success(false)
                .code(9999)
                .message("系统内部错误")
                .traceId(MDC.get("traceId"))
                .build());
    }

    private HttpStatus getHttpStatus(Integer code) {
        if (code >= 1000 && code < 2000) {
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 2000 && code < 3000) {
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 6000) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}