package com.project.clinic.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

@Component
public class ClinicPasswordEncoder implements PasswordEncoder {
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.toString().isBlank() || bytes(rawPassword).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có từ 1 đến 72 byte.");
        }
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (BCRYPT_HASH.matcher(storedPassword).matches()) {
            return bytes(rawPassword).length <= 72 && bcrypt.matches(rawPassword, storedPassword);
        }
        // Keep existing accounts usable until the password is changed.
        return MessageDigest.isEqual(bytes(rawPassword), bytes(storedPassword));
    }

    private byte[] bytes(CharSequence password) {
        return password.toString().getBytes(StandardCharsets.UTF_8);
    }
}
