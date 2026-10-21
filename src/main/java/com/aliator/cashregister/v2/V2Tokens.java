package com.aliator.cashregister.v2;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Base64;

final class V2Tokens {
    private static final SecureRandom RAND = new SecureRandom();
    private V2Tokens() {}
    static String random() { byte[] data = new byte[32]; RAND.nextBytes(data); return Base64.getUrlEncoder().withoutPadding().encodeToString(data); }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
