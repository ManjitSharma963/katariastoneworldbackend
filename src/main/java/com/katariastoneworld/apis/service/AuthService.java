package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.AuthResponseDTO;
import com.katariastoneworld.apis.dto.LoginRequestDTO;
import com.katariastoneworld.apis.dto.RegisterRequestDTO;
import com.katariastoneworld.apis.dto.RegisterResponseDTO;
import com.katariastoneworld.apis.dto.UserResponseDTO;
import com.katariastoneworld.apis.entity.User;
import com.katariastoneworld.apis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public RegisterResponseDTO register(RegisterRequestDTO registerRequest) {
        // Validate location
        String location = registerRequest.getLocation();
        if (!location.equals("Bhondsi") && !location.equals("Tapugada")) {
            throw new IllegalArgumentException("Location must be either 'Bhondsi' or 'Tapugada'");
        }
        
        // Validate role
        String role = registerRequest.getRole();
        if (role == null || role.trim().isEmpty()) {
            role = "user"; // Default role
        } else if (!role.equals("user") && !role.equals("admin")) {
            throw new IllegalArgumentException("Role must be either 'user' or 'admin'");
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Create new user
        User user = new User();
        user.setName(registerRequest.getName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setLocation(registerRequest.getLocation());
        user.setRole(role);
        
        User savedUser = userRepository.save(user);
        
        // Return simple success response without token
        return new RegisterResponseDTO("User registered successfully", savedUser.getEmail());
    }
    
    public AuthResponseDTO login(LoginRequestDTO loginRequest) {
        // Find user by email
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        
        // Verify password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        
        // Generate JWT token with location
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole(), user.getLocation());
        
        // Create response
        UserResponseDTO userResponse = convertToUserResponseDTO(user);
        return new AuthResponseDTO(token, userResponse);
    }
    
    public UserResponseDTO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return convertToUserResponseDTO(user);
    }
    
    private UserResponseDTO convertToUserResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setLocation(user.getLocation());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}

