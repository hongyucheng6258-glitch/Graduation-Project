package com.campus.platform.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {
    @Test
    void generatedTokenKeepsMillisecondIssuedAtClaim() throws InterruptedException {
        JwtUtils jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "unit-test-secret-not-for-production");
        ReflectionTestUtils.setField(jwt, "expireDays", 7L);

        long before = System.currentTimeMillis();
        String token = jwt.generate(7L, "admin");
        long issuedAt = jwt.getIssuedAtMillis(token);

        assertThat(issuedAt).isGreaterThanOrEqualTo(before);
        assertThat(issuedAt).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(jwt.parse(token).get("iat_ms", Number.class)).isEqualTo(issuedAt);
    }
}
