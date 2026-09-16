package com.fabricaescuela.digitalbank.auth.interfaces;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}