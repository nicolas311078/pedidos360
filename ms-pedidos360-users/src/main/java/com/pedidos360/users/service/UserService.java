package com.pedidos360.users.service;

import com.pedidos360.users.dto.UserResponse;
import com.pedidos360.users.model.User;
import com.pedidos360.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }
    public UserResponse getProfile(){
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(()-> new UsernameNotFoundException("user no encontrado: " + username));
        return mapToResponse(user);
    }

    public List<UserResponse> getAllUsers(){
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}
