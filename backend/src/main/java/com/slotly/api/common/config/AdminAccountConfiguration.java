package com.slotly.api.common.config;

import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountConfiguration implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public AdminAccountConfiguration(
        UserRepository users,
        PasswordEncoder passwords,
        @Value("${slotly.admin.username:}") String username,
        @Value("${slotly.admin.password:}") String password
    ) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || username.length() > 40 || password.length() < 12) {
            throw new IllegalStateException("Admin username and a password of at least 12 characters are required");
        }
        users.findByUsername(username).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Admin username belongs to a different role");
            }
        }, () -> users.save(new User(
            username,
            passwords.encode(password),
            "Slotly",
            "Admin",
            UserRole.ADMIN
        )));
    }
}
