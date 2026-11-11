package com.slotly.api.controller.auth;

import com.slotly.api.dto.request.LoginRequest;
import com.slotly.api.dto.request.RegisterRequest;
import com.slotly.api.response.ApiResponse;
import com.slotly.api.dto.response.LoginResponse;
import com.slotly.api.dto.response.RegistrationResponse;
import com.slotly.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.<RegistrationResponse>builder()
            .message("User registered successfully")
            .data(authService.register(request))
            .status(HttpStatus.CREATED)
            .response();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.<LoginResponse>builder()
            .message("Login successful")
            .data(authService.login(request))
            .response();
    }
}
