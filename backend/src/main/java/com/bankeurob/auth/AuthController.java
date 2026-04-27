package com.bankeurob.auth;

import com.bankeurob.auth.dto.AuthResponse;
import com.bankeurob.auth.dto.LoginRequest;
import com.bankeurob.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Rejestracja i logowanie klientów")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Rejestracja nowego klienta", description = "Tworzy konto klienta i zwraca token JWT")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Logowanie klienta", description = "Uwierzytelnia klienta i zwraca token JWT")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("ŻĄDANIE LOGOWANIA DLA: " + request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/login-status/{attemptId}")
    @Operation(summary = "Sprawdź status logowania JUNIOR", description = "Dziecko pyta, czy rodzic zatwierdził logowanie")
    public ResponseEntity<AuthResponse> checkLoginStatus(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(authService.checkLoginStatus(attemptId));
    }
}
