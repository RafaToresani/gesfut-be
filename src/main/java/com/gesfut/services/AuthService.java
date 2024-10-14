package com.gesfut.services;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;


public interface AuthService {
    AuthResponse logIn(LoginRequest request);

    AuthResponse singUp(RegisterRequest request);
}
