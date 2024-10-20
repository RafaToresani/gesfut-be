package com.gesfut.services.impl;

import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserEntityServiceImpl implements UserEntityService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserEntity findUserByEmail(String email) {
        Optional<UserEntity> user = userRepository.findByEmail(email);
        if(user.isEmpty()) throw new ResourceNotFoundException("El usuario no existe.");

        return user.get();
    }

}
