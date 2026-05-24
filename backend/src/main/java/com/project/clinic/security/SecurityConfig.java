package com.project.clinic.security;

import com.project.clinic.repository.AccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return login -> accounts.findByUsername(login)
                .or(() -> accounts.findByEmail(login))
                .filter(account -> account.getPassword() != null && account.getRole() != null)
                .map(ClinicPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .usernameParameter("login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> response.setStatus(401)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**", "/api/rooms/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/rooms", "/api/shifts", "/api/shifts/**").authenticated()
                        .requestMatchers("/api/rooms", "/api/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/receptionist/**").hasAnyRole("RECEPTIONIST", "DOCTOR")
                        .requestMatchers("/api/receptionist/**", "/api/shifts", "/api/shifts/**").hasRole("RECEPTIONIST")
                        .requestMatchers(HttpMethod.POST, "/api/records/**").hasRole("RECEPTIONIST")
                        .requestMatchers(HttpMethod.DELETE, "/api/records/**").hasRole("DOCTOR")
                        .requestMatchers("/api/records/**").hasAnyRole("RECEPTIONIST", "DOCTOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }
}
