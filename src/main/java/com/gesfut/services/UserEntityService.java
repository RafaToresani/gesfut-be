package com.gesfut.services;

import com.gesfut.models.user.UserEntity;

public interface UserEntityService {
    UserEntity findUserByEmail(String email);

}
