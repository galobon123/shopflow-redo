package com.Proyect.Ecommerce.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.user.UserRequestDTO;
import com.Proyect.Ecommerce.dto.user.UserResponseDTO;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponseDTO> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UserResponseDTO findById(Long id) {
        return toResponseDTO(userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id)));
    }

    public UserResponseDTO save(UserRequestDTO dto) {
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);
        return toResponseDTO(userRepository.save(user));
    }

    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    public List<UserResponseDTO> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return userRepository.findByCreatedAtBetween(start, end)
                .stream().map(this::toResponseDTO).toList();
    }

    private UserResponseDTO toResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}