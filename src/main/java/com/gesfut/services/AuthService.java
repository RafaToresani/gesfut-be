package com.gesfut.services;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;


public interface AuthService {
    AuthResponse logIn(LoginRequest request);

    AuthResponse singUp(RegisterRequest request);

    void resetPasswordSendEmail(String email);

    void changePassword(String token, String newPassword);

    void changePasswordWithOldPassword(String oldPassword, String newPassword, String token);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);

}
