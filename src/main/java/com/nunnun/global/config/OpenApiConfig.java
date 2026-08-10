package com.nunnun.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nunnunOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NUNNUN API")
                        .version("v1")
                        .description("NUNNUN mobile application backend API"));
    }
}
