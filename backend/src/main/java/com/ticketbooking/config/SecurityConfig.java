package com.ticketbooking.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.exception.ErrorResponse;
import com.ticketbooking.idempotency.IdempotencyService;
import com.ticketbooking.security.JwtAuthenticationFilter;
import com.ticketbooking.security.JwtService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Who may call what, by role. Ownership ("only your own booking / event") is checked in the
 * service layer, because it depends on the data, not just the URL.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, ObjectMapper json)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless API with bearer tokens, no cookies
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: log in, sign up, and browse events and seat maps
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/events/*/seats",
                        "/api/events/category/*").permitAll()

                // Organizers manage events (only their own: checked in EventService); reports also for admins
                .requestMatchers(HttpMethod.GET, "/api/events/*/analytics").hasAnyRole("ORGANIZER", "ADMIN")
                .requestMatchers("/api/events/**").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.GET, "/api/bookings/event/*").hasAnyRole("ORGANIZER", "ADMIN")

                // Admin only: user management, all bookings, manual status changes
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/bookings").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/bookings/*/status").hasRole("ADMIN")

                // Everything else (booking, paying, cancelling) needs a login; ownership checked in BookingService
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, e) ->
                        writeError(response, json, HttpStatus.UNAUTHORIZED, "Login required"))
                .accessDeniedHandler((request, response, e) ->
                        writeError(response, json, HttpStatus.FORBIDDEN, "You don't have permission to do that")))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json, HttpStatus status, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new ErrorResponse(status.value(), message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Let the browser read this response header (it tells the UI a retry was replayed)
        configuration.setExposedHeaders(List.of(IdempotencyService.REPLAYED_HEADER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
