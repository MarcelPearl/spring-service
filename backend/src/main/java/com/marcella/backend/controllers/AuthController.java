package com.marcella.backend.controllers;

import com.marcella.backend.authDtos.*;
import com.marcella.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/signin")
    public AuthResponse signin(@RequestBody SigninRequest request) {
        return authService.signin(request);
    }

    @PostMapping("/google")
    public AuthResponse authenticateWithGoogle(@RequestBody GoogleAuthRequest request) {
        return authService.authenticateWithGoogle(request.getToken());
    }

    @PostMapping("/otpVerification")
    public ResponseEntity<Boolean> otpVerification(@RequestBody OtpRequest request) {
        return ResponseEntity.ok(authService.otpValidate(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("Password reset link has been sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Password has been successfully reset.");
    }
}
