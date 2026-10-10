package com.kinkan.take_too.controller;

import com.kinkan.take_too.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks de vídeo")
public class VideoWebhookController {
    private final VideoService service;

    @PostMapping(value = "/api/webhooks/video/mux", consumes = "application/json")
    @Operation(summary = "Recebe eventos do Mux autenticados por assinatura sobre o corpo original")
    public ResponseEntity<Void> mux(HttpServletRequest request,
                                    @RequestHeader(value = "Mux-Signature", required = false) String signature) throws IOException {
        byte[] body = request.getInputStream().readNBytes(262145);
        if (body.length > 262144) { throw new IllegalArgumentException("Webhook de vídeo excede 256 KiB."); }
        service.receberWebhook("mux", body, signature);
        return ResponseEntity.noContent().build();
    }
}
