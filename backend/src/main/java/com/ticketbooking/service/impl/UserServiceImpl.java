package com.ticketbooking.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketbooking.dto.LoginResponse;
import com.ticketbooking.dto.UserDTO;
import com.ticketbooking.dto.UserRegistrationDTO;
import com.ticketbooking.exception.InvalidCredentialsException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.exception.UserAlreadyExistsException;
import com.ticketbooking.model.User;
import com.ticketbooking.repository.UserRepository;
import com.ticketbooking.security.JwtService;
import com.ticketbooking.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;
    
    @Override
    @Transactional
    public UserDTO registerUser(UserRegistrationDTO registrationDto) {
        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + registrationDto.getEmail());
        }
        
        User user = new User();
        user.setName(registrationDto.getName());
        user.setEmail(registrationDto.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDto.getPassword()));
        user.setPhoneNumber(registrationDto.getPhoneNumber());
        user.setRole(registrationDto.getRole()); // Added role
        
        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }
    
    @Override
    public UserDTO getUserById(Long id) {
        User user = getUserEntityById(id);
        return mapToDTO(user);
    }
    
    @Override
    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
    
    @Override
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToDTO(user);
    }
    
    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDto) {
        User user = getUserEntityById(id);
        
        // Update fields
        user.setName(userDto.getName());
        user.setPhoneNumber(userDto.getPhoneNumber());
        user.setRole(userDto.getRole()); // Added role update
        
        // Email update requires additional check
        if (!user.getEmail().equals(userDto.getEmail())) {
            if (userRepository.existsByEmail(userDto.getEmail())) {
                throw new UserAlreadyExistsException("Email already in use: " + userDto.getEmail());
            }
            user.setEmail(userDto.getEmail());
        }
        
        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }
    
    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
    
    private UserDTO mapToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setRole(user.getRole()); // Added role mapping
        return dto;
    }

    @Override
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        // Passwords are stored as BCrypt hashes; matches() hashes the attempt with the stored salt
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(jwtService.createToken(user), mapToDTO(user));
    }
}
