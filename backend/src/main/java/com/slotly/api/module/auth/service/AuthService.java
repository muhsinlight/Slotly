package com.slotly.api.module.auth.service;

import com.slotly.api.module.auth.dto.request.LoginRequest;
import com.slotly.api.module.auth.dto.request.RegisterRequest;
import com.slotly.api.module.auth.dto.response.LoginResponse;
import com.slotly.api.module.auth.dto.response.RegistrationResponse;

public interface AuthService {
    RegistrationResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
