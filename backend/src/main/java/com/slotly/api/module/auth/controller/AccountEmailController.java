package com.slotly.api.module.auth.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.auth.service.AccountEmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountEmailController {
    private final AccountEmailService service;

    public AccountEmailController(AccountEmailService service) {
        this.service = service;
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    @GetMapping("/account/email")
    public ResponseEntity<ApiResponse<AccountEmailService.EmailStatus>> status(Authentication authentication) {
        return ApiResponse.<AccountEmailService.EmailStatus>builder()
            .message("E-posta durumu")
            .data(service.status(UUID.fromString(authentication.getName())))
            .response();
    }

    @PostMapping("/account/email")
    public ResponseEntity<ApiResponse<Void>> request(
        Authentication authentication,
        @Valid @RequestBody EmailRequest request
    ) {
        service.requestVerification(UUID.fromString(authentication.getName()), request.email());
        return ApiResponse.<Void>builder().message("Doğrulama bağlantısı gönderim kuyruğuna alındı.").response();
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody TokenRequest request) {
        service.verify(request.token());
        return ApiResponse.<Void>builder().message("E-posta doğrulandı.").response();
    }
}
