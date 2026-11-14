package com.slotly.api.service;

import com.slotly.api.dto.request.LoginRequest;
import com.slotly.api.dto.request.RegisterRequest;
import com.slotly.api.dto.response.LoginResponse;
import com.slotly.api.dto.response.RegistrationResponse;

public interface AuthService {
    RegistrationResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
