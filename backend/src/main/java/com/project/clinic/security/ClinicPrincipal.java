package com.project.clinic.security;

import com.project.clinic.entity.Account;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public class ClinicPrincipal extends User {
    private final int accountId;
    private final Account.Role role;

    public ClinicPrincipal(Account account) {
        super(account.getUsername(), account.getPassword(), account.isActive(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.accountId = account.getId();
        this.role = account.getRole();
    }

    public int accountId() {
        return accountId;
    }

    public Account.Role role() {
        return role;
    }
}
