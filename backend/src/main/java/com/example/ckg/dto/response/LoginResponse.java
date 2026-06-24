package com.example.ckg.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String token;
    private UserDTO user;

    @Data
    @Builder
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String nickname;
    }
}