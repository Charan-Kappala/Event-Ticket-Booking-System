package com.ticketbooking.security;

import com.ticketbooking.model.Role;

/**
 * The logged-in user, taken from a verified JWT. Controllers receive it with
 * {@code @AuthenticationPrincipal CurrentUser user}; it is the only source of "who is calling".
 */
public record CurrentUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
