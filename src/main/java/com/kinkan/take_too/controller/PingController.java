package com.kinkan.take_too.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health Check", description = "Endpoints para verificação de status")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Verifica se a API está online")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "message", "TakeToo API is running!");
    }
}
