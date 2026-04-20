package com.bankeurob.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "BankEuroB API",
                version = "1.0",
                description = "REST API systemu bankowego BankEuroB – przelewy wewnętrzne, SEPA, SWIFT, konta, karty, AML",
                contact = @Contact(name = "BankEuroB Dev Team", email = "dev@bankeurob.eu")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Wklej token JWT otrzymany z /api/auth/login"
)
public class OpenApiConfig {
}
