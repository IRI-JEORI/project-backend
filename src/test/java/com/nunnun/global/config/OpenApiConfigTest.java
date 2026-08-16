package com.nunnun.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void appliesBearerAuthOnlyToProtectedEndpoints() {
        Operation demoAccounts = new Operation();
        Operation demoLogin = new Operation();
        Operation today = new Operation();
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/demo-accounts", new PathItem().get(demoAccounts))
                .addPathItem("/auth/demo-login", new PathItem().post(demoLogin))
                .addPathItem("/me/today", new PathItem().get(today)));

        new OpenApiConfig().bearerAuthOpenApiCustomizer().customise(openApi);

        assertThat(demoAccounts.getSecurity()).isNull();
        assertThat(demoLogin.getSecurity()).isNull();
        assertThat(today.getSecurity()).singleElement().satisfies(requirement ->
                assertThat(requirement).containsKey("bearerAuth")
        );
    }
}
