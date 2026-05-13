package com.bankeurob.auth;

import com.bankeurob.auth.dto.AuthResponse;
import com.bankeurob.auth.dto.LoginRequest;
import com.bankeurob.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Konto utworzone pomyślnie, zwraca token JWT",
            content = @Content(schema = @Schema(implementation = AuthResponse.class),
                examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"customerId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"jan@example.com\",\"firstName\":\"Jan\",\"lastName\":\"Kowalski\",\"role\":\"CUSTOMER\"}"))),
        @ApiResponse(responseCode = "400", description = "Błąd walidacji – nieprawidłowe dane rejestracji",
            content = @Content(examples = {
                @ExampleObject(name = "Email zajęty", value = "{\"error\":\"Email jest już zajęty: jan@example.com\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/auth/register\"}"),
                @ExampleObject(name = "Zły kod kraju", value = "{\"error\":\"Kod kraju musi być 2-znakowym kodem ISO (np. DE, PL, FR). Podano: rzeszóff\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/auth/register\"}")
            })),
        @ApiResponse(responseCode = "422", description = "Błąd walidacji pól – np. zbyt krótkie hasło, nieprawidłowy email",
            content = @Content(examples = @ExampleObject(value = "{\"password\":\"Hasło musi mieć od 8 do 100 znaków\",\"email\":\"Podaj prawidłowy adres email\"}")))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Logowanie klienta", description = "Uwierzytelnia klienta i zwraca token JWT. Dla kont JUNIOR wymaga zgody rodzica.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zalogowano pomyślnie (lub wymagana zgoda rodzica dla JUNIOR)",
            content = @Content(schema = @Schema(implementation = AuthResponse.class),
                examples = {
                    @ExampleObject(name = "Logowanie standardowe", value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"customerId\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"jan@example.com\",\"firstName\":\"Jan\",\"lastName\":\"Kowalski\",\"role\":\"CUSTOMER\",\"requiresParentApproval\":false}"),
                    @ExampleObject(name = "Logowanie Junior (wymaga zgody)", value = "{\"token\":null,\"tokenType\":\"Bearer\",\"customerId\":\"660e8400-e29b-41d4-a716-446655440001\",\"email\":\"kacper@example.com\",\"firstName\":\"Kacper\",\"lastName\":\"Kowalski\",\"role\":\"JUNIOR\",\"requiresParentApproval\":true,\"loginAttemptId\":\"770e8400-e29b-41d4-a716-446655440002\"}")
                })),
        @ApiResponse(responseCode = "401", description = "Nieprawidłowy email lub hasło",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Bad credentials\",\"status\":401,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/auth/login\"}")))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("ŻĄDANIE LOGOWANIA DLA: " + request.getEmail());
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/login-status/{attemptId}")
    @Operation(summary = "Sprawdź status logowania JUNIOR", description = "Dziecko pyta, czy rodzic zatwierdził logowanie. Zwraca token JWT gdy APPROVED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status logowania (PENDING, APPROVED z tokenem, lub CONSUMED)",
            content = @Content(schema = @Schema(implementation = AuthResponse.class),
                examples = {
                    @ExampleObject(name = "Oczekuje", value = "{\"requiresParentApproval\":true,\"loginAttemptId\":\"770e8400-e29b-41d4-a716-446655440002\"}"),
                    @ExampleObject(name = "Zatwierdzony", value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\",\"tokenType\":\"Bearer\",\"customerId\":\"660e8400-e29b-41d4-a716-446655440001\",\"email\":\"kacper@example.com\",\"firstName\":\"Kacper\",\"lastName\":\"Kowalski\",\"role\":\"JUNIOR\",\"requiresParentApproval\":false}")
                })),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono próby logowania",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Nie znaleziono próby logowania\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/auth/login-status/...\"}"))),
        @ApiResponse(responseCode = "403", description = "Logowanie odrzucone przez rodzica",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Logowanie odrzucone przez rodzica\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/auth/login-status/...\"}")))
    })
    public ResponseEntity<AuthResponse> checkLoginStatus(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(authService.checkLoginStatus(attemptId));
    }
}
