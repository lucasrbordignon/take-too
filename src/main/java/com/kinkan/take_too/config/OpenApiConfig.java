package com.kinkan.take_too.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("TakeToo API")
                        .version("1.0")
                        .description("API para o MVP de Gestão de Projetos Audiovisuais (TakeToo). "
                                + "Rotas protegidas retornam 401 para autenticação ausente, inválida ou expirada "
                                + "e 403 para usuário autenticado sem permissão. Após 401, profissionais podem "
                                + "renovar a sessão em POST /api/auth/refresh; magic links exigem novo acesso autorizado.")
                        .contact(new Contact().name("Lucas").email("lucas@example.com")));
    }
}
