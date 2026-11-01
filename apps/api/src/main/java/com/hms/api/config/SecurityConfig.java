package com.hms.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.dto.ErrorResponse;
import com.hms.api.security.ClientIpResolver;
import com.hms.api.security.JwtAuthenticationFilter;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuditLogService auditLogService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthFilter,
        AuditLogService auditLogService,
        ClientIpResolver clientIpResolver,
        ObjectMapper objectMapper
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.auditLogService = auditLogService;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                // Consistent 401 Unauthorized + Audit Logging (only when Authorization header was present)
                .authenticationEntryPoint((request, response, authException) -> {
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader != null && !authHeader.isBlank()) {
                        String clientIp = clientIpResolver.resolveClientIp(request);
                        auditLogService.log(
                            null,
                            "UNAUTHORIZED_ACCESS",
                            "ENDPOINT",
                            request.getRequestURI(),
                            clientIp,
                            authException.getMessage()
                        );
                    }

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse error = ErrorResponse.of(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Unauthorized",
                        "Full authentication is required to access this resource",
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(error));
                })
                // Consistent 403 Forbidden + Audit Logging
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String clientIp = clientIpResolver.resolveClientIp(request);
                    Long userId = null;
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
                        userId = securityUser.getId();
                    }

                    auditLogService.log(
                        userId,
                        "ACCESS_DENIED",
                        "ENDPOINT",
                        request.getRequestURI(),
                        clientIp,
                        accessDeniedException.getMessage()
                    );

                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse error = ErrorResponse.of(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden",
                        "Access denied: insufficient permissions to access this resource",
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(error));
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public Health & Documentation
                .requestMatchers(
                    "/api/health",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()

                // Public Authentication Endpoints
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/auth/otp/**",
                    "/api/auth/register-patient",
                    "/api/auth/password-reset/**"
                ).permitAll()

                // Public Read for Website: Doctors, Branches, Departments, Specialties, Public Files
                .requestMatchers(HttpMethod.GET,
                    "/api/doctors",
                    "/api/doctors/**",
                    "/api/branches",
                    "/api/branches/**",
                    "/api/departments",
                    "/api/departments/**",
                    "/api/specialties",
                    "/api/specialties/**",
                    "/api/files/**"
                ).permitAll()

                // Master data writes require ADMIN role
                .requestMatchers(HttpMethod.POST, "/api/branches/**", "/api/departments/**", "/api/specialties/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/branches/**", "/api/departments/**", "/api/specialties/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/branches/**", "/api/departments/**", "/api/specialties/**", "/api/rooms/**").hasRole("ADMIN")

                // Rooms read requires authentication
                .requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/**").authenticated()

                // Admin endpoints require ADMIN role
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Files upload requires authentication
                .requestMatchers(HttpMethod.POST, "/api/files/upload").authenticated()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
