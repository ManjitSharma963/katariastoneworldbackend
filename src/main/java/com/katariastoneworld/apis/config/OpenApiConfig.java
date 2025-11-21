package com.katariastoneworld.apis.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI katariaStoneWorldOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("Kataria Stone World APIs")
                        .version("1.0.0")
                        .description("""
                                Comprehensive REST API for managing bills, inventory, customers, employees, 
                                and expenses for Kataria Stone World. The API supports location-based data 
                                isolation, JWT authentication, PDF bill generation, and email notifications.
                                
                                ## Features
                                - JWT-based authentication and authorization
                                - Role-based access control (Admin, User)
                                - Bill management (GST and Non-GST)
                                - Inventory/Product management
                                - Customer management
                                - Employee management
                                - Expense tracking
                                - Category management
                                - Hero section management
                                - PDF bill generation
                                - Email notifications
                                
                                ## Authentication
                                Most endpoints require JWT authentication. Register a user or login to get a token, 
                                then include it in the Authorization header as: `Bearer <your_token>`
                                """)
                        .contact(new Contact()
                                .name("Kataria Stone World Development Team")
                                .email("support@katariastoneworld.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://katariastoneworld.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.katariastoneworld.com")
                                .description("Production Server")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Authentication. Enter your token in the format: Bearer <token>")));
    }
}

