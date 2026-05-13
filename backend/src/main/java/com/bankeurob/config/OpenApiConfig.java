package com.bankeurob.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "BankEuroB API",
                version = "1.0.0",
                description = """
                        # BankEuroB REST API
                        
                        Kompleksowy system bankowości elektronicznej obsługujący:
                        * **Konta bankowe** – zarządzanie kontami, sprawdzanie salda
                        * **Przelewy** – wewnętrzne (INTERNAL), SEPA SCT, SEPA Instant, SWIFT
                        * **Autoryzacja JWT** – rejestracja i logowanie klientów
                        * **Konta Junior** – zakładanie kont dziecięcych z nadzorem rodzica
                        * **BLIK** – zarządzanie PIN-em BLIK
                        * **Karty płatnicze** – integracja z zewnętrznym systemem wydawania kart
                        * **AML & Monitoring** – wykrywanie podejrzanych transakcji (RabbitMQ)
                        
                        ---
                        **Autoryzacja:** Większość endpointów wymaga tokena JWT w nagłówku `Authorization: Bearer <token>`.
                        Token można uzyskać poprzez `POST /api/auth/login`.
                        """,
                contact = @Contact(
                        name = "BankEuroB Dev Team",
                        email = "dev@bankeurob.eu",
                        url = "https://bankeurob.eu"
                ),
                license = @License(
                        name = "Proprietary",
                        url = "https://bankeurob.eu/license"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Lokalne środowisko deweloperskie"),
                @Server(url = "https://api.bankeurob.eu", description = "Środowisko produkcyjne")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Wklej token JWT otrzymany z `POST /api/auth/login` lub `POST /api/auth/register`"
)
public class OpenApiConfig {

    /**
     * Rejestruje wszystkie schematy DTO ręcznie, aby Swagger UI pokazywał
     * pełne modele danych dla każdego endpointa.
     */
    @Bean
    public OpenApiCustomizer globalCustomizer() {
        return openApi -> {
            var schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                schemas = new LinkedHashMap<>();
                openApi.getComponents().setSchemas(schemas);
            }

            // ─────────────────────────────────────────────────
            // LoginRequest
            // ─────────────────────────────────────────────────
            schemas.put("LoginRequest", new Schema<>()
                    .type("object")
                    .description("Dane logowania klienta")
                    .addProperties("email", new StringSchema()
                            .description("Adres email klienta")
                            .example("jan.kowalski@example.com"))
                    .addProperties("password", new StringSchema()
                            .description("Hasło klienta")
                            .example("MojeTajneHaslo123!"))
                    .required(List.of("email", "password"))
            );

            // ─────────────────────────────────────────────────
            // RegisterRequest
            // ─────────────────────────────────────────────────
            schemas.put("RegisterRequest", new Schema<>()
                    .type("object")
                    .description("Dane rejestracji nowego klienta")
                    .addProperties("email", new StringSchema()
                            .description("Adres email")
                            .example("jan.kowalski@example.com"))
                    .addProperties("password", new StringSchema()
                            .description("Hasło (min. 8 znaków)")
                            .example("MojeTajneHaslo123!"))
                    .addProperties("firstName", new StringSchema()
                            .description("Imię")
                            .example("Jan"))
                    .addProperties("lastName", new StringSchema()
                            .description("Nazwisko")
                            .example("Kowalski"))
                    .addProperties("dateOfBirth", new StringSchema()
                            .description("Data urodzenia (YYYY-MM-DD)")
                            .example("1990-05-15"))
                    .addProperties("phone", new StringSchema()
                            .description("Numer telefonu")
                            .example("+48123456789"))
                    .addProperties("addressStreet", new StringSchema()
                            .description("Ulica i numer")
                            .example("Marszałkowska 10"))
                    .addProperties("addressCity", new StringSchema()
                            .description("Miasto")
                            .example("Warszawa"))
                    .addProperties("addressCountry", new StringSchema()
                            .description("Kod kraju (ISO 3166-1 alpha-2)")
                            .example("DE"))
                    .addProperties("pesel", new StringSchema()
                            .description("PESEL (opcjonalnie)")
                            .example("90051512345"))
                    .required(List.of("email", "password", "firstName", "lastName", "dateOfBirth"))
            );

            // ─────────────────────────────────────────────────
            // AuthResponse
            // ─────────────────────────────────────────────────
            schemas.put("AuthResponse", new Schema<>()
                    .type("object")
                    .description("Odpowiedź po udanym logowaniu/rejestracji")
                    .addProperties("token", new StringSchema()
                            .description("Token JWT")
                            .example("eyJhbGciOiJIUzI1NiIs..."))
                    .addProperties("tokenType", new StringSchema()
                            .description("Typ tokena")
                            .example("Bearer")
                            ._default("Bearer"))
                    .addProperties("customerId", new UUIDSchema()
                            .description("ID klienta")
                            .example("550e8400-e29b-41d4-a716-446655440000"))
                    .addProperties("email", new StringSchema()
                            .description("Email klienta")
                            .example("jan.kowalski@example.com"))
                    .addProperties("firstName", new StringSchema()
                            .description("Imię klienta")
                            .example("Jan"))
                    .addProperties("lastName", new StringSchema()
                            .description("Nazwisko klienta")
                            .example("Kowalski"))
                    .addProperties("role", new StringSchema()
                            .description("Rola klienta (CUSTOMER, ADMIN, EMPLOYEE)")
                            .example("CUSTOMER"))
                    .addProperties("requiresParentApproval", new BooleanSchema()
                            .description("Czy wymagana jest zgoda rodzica (dla kont JUNIOR)")
                            .example(false))
                    .addProperties("loginAttemptId", new UUIDSchema()
                            .description("ID próby logowania (gdy wymagana zgoda rodzica)")
                            .example("660e8400-e29b-41d4-a716-446655440001"))
            );

            // ─────────────────────────────────────────────────
            // AccountDto
            // ─────────────────────────────────────────────────
            schemas.put("AccountDto", new Schema<>()
                    .type("object")
                    .description("Szczegóły konta bankowego")
                    .addProperties("id", new UUIDSchema()
                            .description("ID konta")
                            .example("550e8400-e29b-41d4-a716-446655440002"))
                    .addProperties("iban", new StringSchema()
                            .description("Międzynarodowy numer rachunku bankowego (IBAN)")
                            .example("DE89370400440532013000"))
                    .addProperties("bic", new StringSchema()
                            .description("Kod BIC/SWIFT banku")
                            .example("BKEUDEBBXXX"))
                    .addProperties("accountType", new StringSchema()
                            .description("Typ konta (STANDARD, SAVINGS, JUNIOR)")
                            .example("STANDARD"))
                    .addProperties("currency", new StringSchema()
                            .description("Waluta (ISO 4217)")
                            .example("EUR"))
                    .addProperties("balance", new NumberSchema()
                            .description("Aktualne saldo")
                            .example(new BigDecimal("12500.50")))
                    .addProperties("availableBalance", new NumberSchema()
                            .description("Dostępne środki (saldo - blokady)")
                            .example(new BigDecimal("12000.00")))
                    .addProperties("dailyLimit", new NumberSchema()
                            .description("Dzienny limit transakcyjny")
                            .example(new BigDecimal("5000.00")))
                    .addProperties("isActive", new BooleanSchema()
                            .description("Czy konto jest aktywne")
                            .example(true))
                    .addProperties("createdAt", new DateTimeSchema()
                            .description("Data utworzenia konta")
                            .example("2026-01-15T10:30:00Z"))
            );

            // ─────────────────────────────────────────────────
            // TransferRequest
            // ─────────────────────────────────────────────────
            schemas.put("TransferRequest", new Schema<>()
                    .type("object")
                    .description("Zlecenie przelewu bankowego")
                    .addProperties("senderIban", new StringSchema()
                            .description("IBAN rachunku nadawcy")
                            .example("DE89370400440532013000"))
                    .addProperties("receiverIban", new StringSchema()
                            .description("IBAN rachunku odbiorcy")
                            .example("FR1420041010050500013M02606"))
                    .addProperties("receiverName", new StringSchema()
                            .description("Nazwa odbiorcy")
                            .example("Marie Curie S.A."))
                    .addProperties("amount", new NumberSchema()
                            .description("Kwota przelewu (max 100 000 EUR)")
                            .example(new BigDecimal("2500.00")))
                    .addProperties("title", new StringSchema()
                            .description("Tytuł przelewu (max 140 znaków)")
                            .example("Faktura nr 123/2026"))
                    .addProperties("transferType", new StringSchema()
                            .description("""
                                    Typ przelewu:
                                    * `INTERNAL` – przelew wewnętrzny (BankEuroB → BankEuroB)
                                    * `SEPA_SCT` – standardowy przelew SEPA
                                    * `SEPA_INSTANT` – natychmiastowy przelew SEPA
                                    * `SWIFT` – przelew zagraniczny SWIFT
                                    """)
                            .example("SEPA_SCT")
                            ._default("INTERNAL"))
                    .required(List.of("senderIban", "receiverIban", "receiverName", "amount"))
            );

            // ─────────────────────────────────────────────────
            // TransactionDto
            // ─────────────────────────────────────────────────
            schemas.put("TransactionDto", new Schema<>()
                    .type("object")
                    .description("Szczegóły transakcji / przelewu")
                    .addProperties("id", new UUIDSchema()
                            .description("ID transakcji")
                            .example("550e8400-e29b-41d4-a716-446655440003"))
                    .addProperties("referenceNumber", new StringSchema()
                            .description("Numer referencyjny transakcji")
                            .example("BKEU-20260512-000001"))
                    .addProperties("transactionType", new StringSchema()
                            .description("Typ transakcji (INTERNAL, SEPA_SCT, SEPA_INSTANT, SWIFT)")
                            .example("SEPA_SCT"))
                    .addProperties("status", new StringSchema()
                            .description("Status transakcji (COMPLETED, PENDING, REJECTED, FAILED)")
                            .example("COMPLETED"))
                    .addProperties("senderIban", new StringSchema()
                            .description("IBAN nadawcy")
                            .example("DE89370400440532013000"))
                    .addProperties("senderName", new StringSchema()
                            .description("Nazwa nadawcy")
                            .example("Jan Kowalski"))
                    .addProperties("receiverIban", new StringSchema()
                            .description("IBAN odbiorcy")
                            .example("FR1420041010050500013M02606"))
                    .addProperties("receiverName", new StringSchema()
                            .description("Nazwa odbiorcy")
                            .example("Marie Curie S.A."))
                    .addProperties("amount", new NumberSchema()
                            .description("Kwota transakcji")
                            .example(new BigDecimal("2500.00")))
                    .addProperties("currency", new StringSchema()
                            .description("Waluta (ISO 4217)")
                            .example("EUR"))
                    .addProperties("title", new StringSchema()
                            .description("Tytuł przelewu")
                            .example("Faktura nr 123/2026"))
                    .addProperties("requestedAt", new DateTimeSchema()
                            .description("Data zlecenia przelewu")
                            .example("2026-05-12T14:30:00Z"))
                    .addProperties("completedAt", new DateTimeSchema()
                            .description("Data realizacji przelewu")
                            .example("2026-05-12T14:30:05Z"))
            );

            // ─────────────────────────────────────────────────
            // UpdateCustomerRequest
            // ─────────────────────────────────────────────────
            schemas.put("UpdateCustomerRequest", new Schema<>()
                    .type("object")
                    .description("Aktualizacja danych kontaktowych klienta")
                    .addProperties("phone", new StringSchema()
                            .description("Numer telefonu")
                            .example("+48123456789"))
                    .addProperties("addressStreet", new StringSchema()
                            .description("Ulica i numer")
                            .example("Nowy Świat 22"))
                    .addProperties("addressCity", new StringSchema()
                            .description("Miasto")
                            .example("Warszawa"))
                    .addProperties("addressCountry", new StringSchema()
                            .description("Kod kraju (ISO 3166-1 alpha-2)")
                            .example("PL"))
            );

            // ─────────────────────────────────────────────────
            // BlikPinRequest
            // ─────────────────────────────────────────────────
            schemas.put("BlikPinRequest", new Schema<>()
                    .type("object")
                    .description("Zmiana PIN-u BLIK")
                    .addProperties("currentPin", new StringSchema()
                            .description("Aktualny PIN BLIK (6 cyfr)")
                            .example("123456"))
                    .addProperties("newPin", new StringSchema()
                            .description("Nowy PIN BLIK (6 cyfr)")
                            .example("654321"))
            );

            // ─────────────────────────────────────────────────
            // JuniorAccountRequest
            // ─────────────────────────────────────────────────
            schemas.put("JuniorAccountRequest", new Schema<>()
                    .type("object")
                    .description("Dane do założenia konta Junior (dziecko)")
                    .addProperties("firstName", new StringSchema()
                            .description("Imię dziecka")
                            .example("Kacper"))
                    .addProperties("lastName", new StringSchema()
                            .description("Nazwisko dziecka")
                            .example("Kowalski"))
                    .addProperties("email", new StringSchema()
                            .description("Email dziecka")
                            .example("kacper.kowalski@example.com"))
                    .addProperties("password", new StringSchema()
                            .description("Hasło dziecka")
                            .example("DziecieceHaslo1"))
                    .addProperties("dateOfBirth", new StringSchema()
                            .description("Data urodzenia dziecka (YYYY-MM-DD)")
                            .example("2012-03-20"))
                    .addProperties("pesel", new StringSchema()
                            .description("PESEL dziecka")
                            .example("12345678901"))
                    .addProperties("phone", new StringSchema()
                            .description("Telefon dziecka")
                            .example("+48123456789"))
                    .addProperties("addressStreet", new StringSchema()
                            .description("Ulica zamieszkania")
                            .example("Marszałkowska 10"))
                    .addProperties("addressCity", new StringSchema()
                            .description("Miasto")
                            .example("Warszawa"))
                    .addProperties("addressCountry", new StringSchema()
                            .description("Kod kraju")
                            .example("DE"))
            );

            // ─────────────────────────────────────────────────
            // Customer (profil klienta)
            // ─────────────────────────────────────────────────
            schemas.put("Customer", new Schema<>()
                    .type("object")
                    .description("Profil klienta banku")
                    .addProperties("id", new UUIDSchema()
                            .description("ID klienta")
                            .example("550e8400-e29b-41d4-a716-446655440000"))
                    .addProperties("email", new StringSchema()
                            .description("Adres email")
                            .example("jan.kowalski@example.com"))
                    .addProperties("firstName", new StringSchema()
                            .description("Imię")
                            .example("Jan"))
                    .addProperties("lastName", new StringSchema()
                            .description("Nazwisko")
                            .example("Kowalski"))
                    .addProperties("phone", new StringSchema()
                            .description("Numer telefonu")
                            .example("+48123456789"))
                    .addProperties("dateOfBirth", new StringSchema()
                            .description("Data urodzenia")
                            .example("1990-05-15"))
                    .addProperties("pesel", new StringSchema()
                            .description("PESEL")
                            .example("90051512345"))
                    .addProperties("addressStreet", new StringSchema()
                            .description("Adres - ulica")
                            .example("Marszałkowska 10"))
                    .addProperties("addressCity", new StringSchema()
                            .description("Adres - miasto")
                            .example("Warszawa"))
                    .addProperties("addressCountry", new StringSchema()
                            .description("Adres - kod kraju")
                            .example("DE"))
                    .addProperties("blikPin", new StringSchema()
                            .description("PIN BLIK (ukryty)")
                            .example("******"))
                    .addProperties("isActive", new BooleanSchema()
                            .description("Czy konto aktywne")
                            .example(true))
                    .addProperties("role", new StringSchema()
                            .description("Rola (CUSTOMER, ADMIN, EMPLOYEE)")
                            .example("CUSTOMER"))
                    .addProperties("createdAt", new DateTimeSchema()
                            .description("Data utworzenia konta")
                            .example("2026-01-15T10:30:00Z"))
            );

            // ─────────────────────────────────────────────────
            // ErrorResponse (standardowy błąd)
            // ─────────────────────────────────────────────────
            schemas.put("ErrorResponse", new Schema<>()
                    .type("object")
                    .description("Standardowa odpowiedź błędu")
                    .addProperties("error", new StringSchema()
                            .description("Komunikat błędu")
                            .example("Nie znaleziono konta o podanym ID"))
                    .addProperties("status", new IntegerSchema()
                            .description("Kod statusu HTTP")
                            .example(404))
                    .addProperties("timestamp", new StringSchema()
                            .description("Znacznik czasu")
                            .example("2026-05-12T14:30:00Z"))
                    .addProperties("path", new StringSchema()
                            .description("Ścieżka endpointa")
                            .example("/api/accounts/123"))
            );

            // ─────────────────────────────────────────────────
            // PendingLogin (oczekujące logowanie Junior)
            // ─────────────────────────────────────────────────
            schemas.put("PendingLogin", new Schema<>()
                    .type("object")
                    .description("Oczekująca próba logowania dziecka (Junior)")
                    .addProperties("id", new UUIDSchema()
                            .description("ID próby logowania")
                            .example("660e8400-e29b-41d4-a716-446655440001"))
                    .addProperties("status", new StringSchema()
                            .description("Status (PENDING, APPROVED, REJECTED)")
                            .example("PENDING"))
                    .addProperties("createdAt", new DateTimeSchema()
                            .description("Data próby logowania")
                            .example("2026-05-12T14:30:00Z"))
            );

            // ─────────────────────────────────────────────────
            // CardIntegrationResponse
            // ─────────────────────────────────────────────────
            schemas.put("CardIntegrationResponse", new Schema<>()
                    .type("object")
                    .description("Odpowiedź z zewnętrznego systemu wydawania kart")
                    .addProperties("status", new StringSchema()
                            .description("Status odpowiedzi")
                            .example("success"))
                    .addProperties("message", new StringSchema()
                            .description("Komunikat z systemu kart")
                            .example("Karta została wydana pomyślnie"))
                    .addProperties("cardId", new StringSchema()
                            .description("ID wydanej karty")
                            .example("CARD-20260512-0001"))
            );
        };
    }
}
