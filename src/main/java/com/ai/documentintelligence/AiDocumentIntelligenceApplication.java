package com.ai.documentintelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiDocumentIntelligenceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiDocumentIntelligenceApplication.class, args);
	}

}
