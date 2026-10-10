package com.fabricaescuela.digitalbank.auth.interfaces.services;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;

public interface IAuthService {
    LoginResponse login(LoginRequest request);
}