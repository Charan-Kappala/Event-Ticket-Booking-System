package com.ticketbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.ticketbooking.model.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);  // Search by username

    Optional<User> findByEmail(String email); // Find user by email
    
    boolean existsByEmail(String email); // Check if email is already used
    
    boolean existsByUsername(String username); // Check if username is already used
}
