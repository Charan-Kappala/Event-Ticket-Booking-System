package com.ticketbooking.service;

import java.util.List;

import com.ticketbooking.dto.LoginResponse;
import com.ticketbooking.dto.UserDTO;
import com.ticketbooking.dto.UserRegistrationDTO;
import com.ticketbooking.model.User;

public interface UserService {
    UserDTO registerUser(UserRegistrationDTO registrationDto);
    UserDTO getUserById(Long id);
    UserDTO getUserByEmail(String email);
    List<UserDTO> getAllUsers();
    UserDTO updateUser(Long id, UserDTO userDto);
    void deleteUser(Long id);
    User getUserEntityById(Long id);
    LoginResponse login(String email,String password);
}
