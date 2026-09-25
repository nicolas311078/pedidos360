package com.pedidos360.users.service;

import com.pedidos360.users.dto.AuthResponse;
import com.pedidos360.users.dto.LoginRequest;
import com.pedidos360.users.dto.RegisterRequest;
import com.pedidos360.users.exception.UserAlreadyExistException;
import com.pedidos360.users.model.Role;
import com.pedidos360.users.model.User;
import com.pedidos360.users.repository.UserRepository;
import com.pedidos360.users.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Deprecated(forRemoval = true)
    public AuthResponse register (RegisterRequest request){

        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())){
            throw new UserAlreadyExistException("Ya se ha utilizado el username de: " + request.getUsername());
        }

        if(userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new UserAlreadyExistException("Ya se ha utilizado el email de: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CLIENTE)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return new AuthResponse(token);
    }

    @Deprecated(forRemoval = true)
    public AuthResponse login (LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()
                )
        );

        User user = userRepository.findByUsernameIgnoreCase (request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }
}
