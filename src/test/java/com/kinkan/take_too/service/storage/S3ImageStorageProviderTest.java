package com.kinkan.take_too.service.storage;

import com.kinkan.take_too.config.ImagemProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class S3ImageStorageProviderTest {
    @Test
    void deveAssinarUploadComChecksumETamanhoDeSessaoSemPermitirSobrescrita() {
        var properties = new ImagemProperties();
        properties.setBucket("bucket-teste"); properties.setRegion("sa-east-1");
        try (var provider = new S3ImageStorageProvider(properties,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))) {
            String checksum = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
            var upload = provider.criarUpload("imagens/teste.png", "image/png", checksum, Instant.now().plusSeconds(900));
            assertEquals("*", upload.headers().get("if-none-match"));
            assertEquals("image/png", upload.headers().get("content-type"));
            assertEquals(checksum, upload.headers().get("x-amz-checksum-sha256"));
            assertFalse(upload.headers().containsKey("host"));
            var expiry = java.util.regex.Pattern.compile("X-Amz-Expires=(\\d+)").matcher(upload.url());
            assertTrue(expiry.find());
            assertTrue(Integer.parseInt(expiry.group(1)) > 850 && Integer.parseInt(expiry.group(1)) <= 900);
            assertTrue(upload.url().contains("if-none-match"));
            assertTrue(provider.visualizar("imagens/teste.png", Instant.now().plusSeconds(600)).contains("X-Amz-Signature="));
        }
    }
}
