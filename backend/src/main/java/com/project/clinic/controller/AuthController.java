package com.project.clinic.controller;

import com.project.clinic.entity.Account;
import com.project.clinic.security.ClinicPrincipal;
import com.project.clinic.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    public record AuthenticatedUser(int accountId, String username, String fullName, String email,
                                    Account.Role role, String avatarUrl) {}

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/me")
    public AuthenticatedUser me(@AuthenticationPrincipal ClinicPrincipal principal) {
        Account account = accounts.findById(principal.accountId());
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.getFullName(),
                account.getEmail(), account.getRole(), account.getAvatarUrl());
    }
}
