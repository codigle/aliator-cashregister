package com.aliator.cashregister.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class StaffAccess {
    private final byte[] expected;
    public StaffAccess(@Value("${app.staff-token:}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }
    public void require(String provided) {
        if (expected.length < 16 || provided == null ||
            !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff access required.");
        }
    }
}
