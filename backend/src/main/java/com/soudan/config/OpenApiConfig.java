package com.soudan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Mô tả API (Swagger UI tại /swagger-ui.html) kèm ô nhập Bearer token. */
@Configuration
public class OpenApiConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	OpenAPI soudanOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Soudan API").version("v1"))
				.components(new Components().addSecuritySchemes(BEARER,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}
}
