package com.kinkan.take_too.service.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ImageStorageProvider {
    String id();
    Upload criarUpload(String chave, String tipo, String checksum, Instant expiraEm);
    Optional<Objeto> consultar(String chave);
    InputStream abrir(String chave);
    String visualizar(String chave, Instant expiraEm);
    void excluir(String chave);
    record Upload(String url, Map<String, String> headers) {}
    record Objeto(long tamanhoBytes, String tipo) {}
}
