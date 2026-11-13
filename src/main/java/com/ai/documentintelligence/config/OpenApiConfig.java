package com.ai.documentintelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** OpenAPI metadata. Swagger UI: {@code /swagger-ui.html}, spec: {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

	private static final String SCHEME = "apiKey";

	@Bean
	OpenAPI openApi(AppProperties props) {
		return new OpenAPI()
				.info(new Info()
						.title("AI Document Intelligence API")
						.version("v1")
						.description("Upload documents, then ask questions and get grounded answers with source citations."))
				.components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY)
						.in(SecurityScheme.In.HEADER)
						.name(props.security().apiKeyHeader())))
				.addSecurityItem(new SecurityRequirement().addList(SCHEME));
	}
}
