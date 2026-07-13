package com.hospital.management.service;

import com.hospital.management.dto.auth.*;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    TokenRefreshResponse refresh(TokenRefreshRequest request);
    void logout(String token);
    void register(RegisterRequest request);
}
