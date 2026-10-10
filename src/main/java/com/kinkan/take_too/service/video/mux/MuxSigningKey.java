package com.kinkan.take_too.service.video.mux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/** Aceita PEM PKCS#8 ou PKCS#1, inclusive PEM em base64 retornado pelo Mux. */
final class MuxSigningKey {
    private MuxSigningKey() {}

    static RSAPrivateKey parse(String configuredKey) throws Exception {
        String pem = configuredKey.strip().replace("\\n", "\n");
        if (!pem.startsWith("-----BEGIN")) {
            pem = new String(Base64.getDecoder().decode(pem), StandardCharsets.US_ASCII).strip();
        }
        boolean pkcs1 = pem.startsWith("-----BEGIN RSA PRIVATE KEY-----");
        String begin = pkcs1 ? "-----BEGIN RSA PRIVATE KEY-----" : "-----BEGIN PRIVATE KEY-----";
        String end = pkcs1 ? "-----END RSA PRIVATE KEY-----" : "-----END PRIVATE KEY-----";
        if (!pem.startsWith(begin) || !pem.endsWith(end)) {
            throw new IllegalArgumentException("Formato da chave inválido");
        }
        byte[] der = Base64.getDecoder().decode(pem.replace(begin, "").replace(end, "").replaceAll("\\s", ""));
        if (pkcs1) {
            var content = new ByteArrayOutputStream();
            content.writeBytes(HexFormat.of().parseHex("020100300d06092a864886f70d0101010500"));
            content.writeBytes(tag(0x04, der));
            der = tag(0x30, content.toByteArray());
        }
        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (key.getModulus().bitLength() < 2048) { throw new IllegalArgumentException("Chave RSA curta"); }
        return key;
    }

    private static byte[] tag(int tag, byte[] value) {
        var out = new ByteArrayOutputStream();
        out.write(tag);
        if (value.length < 128) {
            out.write(value.length);
        } else {
            int bytes = (32 - Integer.numberOfLeadingZeros(value.length) + 7) / 8;
            out.write(0x80 | bytes);
            for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) { out.write(value.length >> shift & 0xff); }
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
