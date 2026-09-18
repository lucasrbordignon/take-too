package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.ClienteCreateDTO;
import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Endpoints para gerenciamento de clientes do profissional logado")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @Operation(summary = "Lista todos os clientes do profissional logado")
    public ResponseEntity<List<ClienteDTO>> listar(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(clienteService.listarClientes(user.getId()));
    }

    @PostMapping
    @Operation(summary = "Cria um novo cliente e vincula ao profissional logado")
    public ResponseEntity<ClienteDTO> criar(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody @Valid ClienteCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.criarCliente(user.getId(), dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca os detalhes de um cliente específico")
    public ResponseEntity<ClienteDTO> buscarPorId(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clienteService.buscarPorId(user.getId(), Objects.requireNonNull(id)));
    }
}
