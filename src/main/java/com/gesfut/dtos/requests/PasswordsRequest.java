package com.gesfut.dtos.requests;

public record PasswordsRequest(
        String oldPassword,
        String newPassword
) {
}
