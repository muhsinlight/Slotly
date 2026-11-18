package com.slotly.api.module.auth.controller;

import com.slotly.api.common.response.ApiResponse;
import com.slotly.api.module.auth.service.PasswordRecoveryService;
import com.slotly.api.module.auth.validation.AccountPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;

    public PasswordRecoveryController(PasswordRecoveryService service) {
        this.service = service;
    }

    public record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetRequest(
        @NotBlank @Size(max = 100) String token,
        @AccountPassword String password
    ) {
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> request(@Valid @RequestBody RecoveryRequest request) {
        service.request(request.email());
        return ApiResponse.<Void>builder()
            .message("Adres doğrulanmış bir hesaba aitse sıfırlama bağlantısı gönderim kuyruğuna alındı.")
            .response();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody ResetRequest request) {
        service.reset(request.token(), request.password());
        return ApiResponse.<Void>builder().message("Parola değiştirildi. Yeniden giriş yap.").response();
    }
}
