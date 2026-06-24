package com.example.ckg.service.user;

import com.example.ckg.common.ErrorCode;
import com.example.ckg.dto.request.LoginRequest;
import com.example.ckg.dto.request.RegisterRequest;
import com.example.ckg.dto.response.LoginResponse;
import com.example.ckg.entity.User;
import com.example.ckg.exception.BusinessException;
import com.example.ckg.repository.UserRepository;
import com.example.ckg.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // 检查用户名是否存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // 检查邮箱是否存在
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 创建用户
        User user = User.builder()
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .email(request.getEmail())
            .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
            .enabled(true)
            .build();

        user = userRepository.save(user);

        // 生成 Token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return buildLoginResponse(user, token);
    }

    public LoginResponse login(LoginRequest request) {
        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 检查用户是否启用
        if (!user.getEnabled()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户已被禁用");
        }

        // 生成 Token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        return buildLoginResponse(user, token);
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        return LoginResponse.builder()
            .token(token)
            .user(LoginResponse.UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build())
            .build();
    }
}