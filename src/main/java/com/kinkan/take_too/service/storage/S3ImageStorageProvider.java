package com.kinkan.take_too.service.storage;

import com.kinkan.take_too.config.ImagemProperties;
import com.kinkan.take_too.exception.ArmazenamentoException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.imagem.enabled", havingValue = "true")
public class S3ImageStorageProvider implements ImageStorageProvider, AutoCloseable {
    private final ImagemProperties properties;
    private final AwsCredentialsProvider credentials;
    private final S3Client client;
    private final S3Presigner presigner;

    @Autowired
    public S3ImageStorageProvider(ImagemProperties properties) {
        this(properties, DefaultCredentialsProvider.builder().build());
    }
    S3ImageStorageProvider(ImagemProperties properties, AwsCredentialsProvider credentials) {
        this.properties = properties;
        this.credentials = credentials;
        var configuration = S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyle()).build();
        var builder = S3Client.builder().region(Region.of(properties.getRegion())).credentialsProvider(credentials)
                .serviceConfiguration(configuration).httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)));
        var signing = S3Presigner.builder().region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials).serviceConfiguration(configuration);
        if (!properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            signing.endpointOverride(URI.create(properties.getEndpoint()));
        }
        this.client = builder.build();
        this.presigner = signing.build();
    }
    public String id() { return "s3"; }
    private String bucket() {
        if (properties.getBucket().isBlank()) throw new ArmazenamentoException("Bucket de imagens não configurado.");
        return properties.getBucket();
    }
    public Upload criarUpload(String chave, String tipo, String checksum, Instant expiraEm) {
        try {
            var request = PutObjectRequest.builder().bucket(bucket()).key(chave).contentType(tipo)
                    .checksumSHA256(checksum).ifNoneMatch("*").build();
            var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.between(Instant.now(), expiraEm)).putObjectRequest(request).build());
            var headers = new java.util.LinkedHashMap<String, String>();
            signed.signedHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) headers.put(name, String.join(",", values));
            });
            return new Upload(signed.url().toString(), Map.copyOf(headers));
        } catch (ArmazenamentoException ex) { throw ex; }
        catch (Exception ex) { throw new ArmazenamentoException("Não foi possível preparar o upload de imagem."); }
    }
    public Optional<Objeto> consultar(String chave) {
        try {
            var response = client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(chave).build());
            return Optional.of(new Objeto(response.contentLength(), response.contentType()));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return Optional.empty();
            throw new ArmazenamentoException("Não foi possível consultar a imagem.");
        } catch (Exception ex) { throw new ArmazenamentoException("Não foi possível consultar a imagem."); }
    }
    public InputStream abrir(String chave) {
        try { return client.getObject(GetObjectRequest.builder().bucket(bucket()).key(chave).build()); }
        catch (Exception ex) { throw new ArmazenamentoException("Não foi possível validar o arquivo de imagem."); }
    }
    public String visualizar(String chave, Instant expiraEm) {
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.between(Instant.now(), expiraEm))
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket()).key(chave)
                            .responseContentDisposition("inline").build()).build()).url().toString();
        } catch (Exception ex) { throw new ArmazenamentoException("Não foi possível preparar a visualização da imagem."); }
    }
    public void excluir(String chave) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(chave).build()); }
        catch (Exception ex) { throw new ArmazenamentoException("Não foi possível excluir a imagem."); }
    }
    @PreDestroy public void close() {
        client.close(); presigner.close();
        if (credentials instanceof DefaultCredentialsProvider defaults) defaults.close();
    }
}
