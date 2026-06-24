package com.example.ckg.controller;

import com.example.ckg.common.Result;
import com.example.ckg.dto.request.LoginRequest;
import com.example.ckg.dto.request.RegisterRequest;
import com.example.ckg.dto.response.LoginResponse;
import com.example.ckg.service.user.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}