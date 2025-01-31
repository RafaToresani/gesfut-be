package com.gesfut.services.impl;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.user.ERole;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.AuthService;
import com.gesfut.services.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmailService emailService;

    @Override
    public AuthResponse logIn(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        Optional<UserEntity> user = userRepository.findByEmail(request.email());
        if(user.isEmpty()) throw new ResourceNotFoundException("El email ingresado no es válido.");
        String token = jwtService.getToken(user.get(), user.get().getAuthorities());
        return new AuthResponse(user.get().getName(), user.get().getLastname(), token, user.get().getRole().name());
    }


    @Override
    public AuthResponse singUp(RegisterRequest request) {
        Optional<UserEntity> opt = this.userRepository.findByEmail(request.email());
        if(opt.isPresent()) throw new ResourceAlreadyExistsException("El email ingresado ya existe.");

        UserEntity user = UserEntity.builder()
                .name(request.name())
                .lastname(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(ERole.MANAGER)
                .build();


        user = this.userRepository.save(user);
        Set<PlayerRequest> players = new HashSet<>();
        players.add(new PlayerRequest("Free", "Free", true, true, 10));
        this.teamService.createDummyTeam(new TeamRequest("Free", "#FFF", players), user);
        return new AuthResponse(
                user.getName(),
                user.getLastname(),
                jwtService.getToken(user, user.getAuthorities()),
                user.getRole().name());
    }

    public void resetPasswordSendEmail(String email) {
        Optional<UserEntity> user = userRepository.findByEmail(email);
        if (user.isEmpty()) throw new ResourceNotFoundException("El email ingresado no es válido.");

        String token = jwtService.generateToken(email);
        System.out.printf("TOKEN GENERADO: %s%n", token);

        user.get().setResetToken(token);
        user.get().setResetTokenExpiration(LocalDateTime.now().plusMinutes(30));
        userRepository.save(user.get());

        this.emailService.sendEmail(email, "GESFUT - Recuperación de contraseña",
                "Hola, para recuperar tu contraseña, haz click en el siguiente enlace: " +
                        "http://localhost:4200/auth/reset-password/" + token);
    }


    public void changePassword(String token, String newPassword) {
        Optional<UserEntity> user = userRepository.findByResetToken(token);
        if (user.isEmpty()) throw new ResourceNotFoundException("El token es inválido.");
        if (user.get().getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("El token ha expirado.");
        }
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        user.get().setResetToken(null);
        user.get().setResetTokenExpiration(null);
        userRepository.save(user.get());
    }

    @Override
    public void changePasswordWithOldPassword(String oldPassword, String newPassword, String token) {
        Optional<UserEntity> user = userRepository.findByEmail(jwtService.getUsernameFromToken(token));
        if (user.isEmpty()) throw new ResourceNotFoundException("El token es inválido.");
        if (!passwordEncoder.matches(oldPassword, user.get().getPassword())) {
            throw new IllegalStateException("La contraseña antigua no coincide.");
        }
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        userRepository.save(user.get());
    }

}
