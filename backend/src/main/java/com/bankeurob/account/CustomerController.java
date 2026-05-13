package com.bankeurob.account;

import com.bankeurob.account.dto.BlikPinRequest;
import com.bankeurob.account.dto.UpdateCustomerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Zarządzanie profilem klienta")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/me")
    @Operation(summary = "Mój profil", description = "Zwraca dane profilowe zalogowanego klienta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil klienta",
            content = @Content(examples = @ExampleObject(value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"jan@example.com\",\"firstName\":\"Jan\",\"lastName\":\"Kowalski\",\"phone\":\"+48123456789\",\"dateOfBirth\":\"1990-05-15\",\"pesel\":\"90051512345\",\"addressStreet\":\"Marszałkowska 10\",\"addressCity\":\"Warszawa\",\"addressCountry\":\"DE\",\"blikPin\":\"******\",\"isActive\":true,\"role\":\"CUSTOMER\",\"createdAt\":\"2026-01-15T10:30:00Z\"}"))),
        @ApiResponse(responseCode = "401", description = "Brak autoryzacji – token JWT wymagany",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Unauthorized\",\"status\":401,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/customers/me\"}")))
    })
    public ResponseEntity<Customer> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(customerService.getMyProfile(authentication));
    }

    @PutMapping("/me")
    @Operation(summary = "Aktualizacja danych kontaktowych", description = "Aktualizuje telefon i adres zalogowanego klienta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane zaktualizowane pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"email\":\"jan@example.com\",\"firstName\":\"Jan\",\"lastName\":\"Kowalski\",\"phone\":\"+48987654321\",\"addressStreet\":\"Nowy Świat 22\",\"addressCity\":\"Warszawa\",\"addressCountry\":\"PL\",\"isActive\":true,\"role\":\"CUSTOMER\"}"))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane w żądaniu",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Nieprawidłowe dane\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/customers/me\"}")))
    })
    public ResponseEntity<Customer> updateContactData(@RequestBody UpdateCustomerRequest request, Authentication authentication) {
        return ResponseEntity.ok(customerService.updateContactData(request, authentication));
    }

    @PutMapping("/me/blik-pin")
    @Operation(summary = "Zmień PIN BLIK", description = "Zmienia PIN BLIK dla zalogowanego klienta (wymaga aktualnego PIN-u)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PIN BLIK zmieniony pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"success\":true}"))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy PIN – musi być 4 cyfry lub nieprawidłowy obecny PIN",
            content = @Content(examples = {
                @ExampleObject(name = "Zły format PIN", value = "{\"error\":\"PIN musi składać się z 4 cyfr\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/customers/me/blik-pin\"}"),
                @ExampleObject(name = "Zły obecny PIN", value = "{\"error\":\"Nieprawidłowy obecny PIN\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/customers/me/blik-pin\"}")
            }))
    })
    public ResponseEntity<Map<String, Object>> updateBlikPin(@RequestBody BlikPinRequest request, Authentication authentication) {
        customerService.updateBlikPin(request, authentication);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
