package com.osgateway.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignatureUtilTest {

    @Test
    void signAndVerify_roundTrip() {
        String payload = "gateway-heartbeat-payload";
        String secret = "super-secret-hmac-key";
        String signature = HmacSignatureUtil.sign(payload, secret);

        assertTrue(HmacSignatureUtil.verify(payload, secret, signature));
        assertFalse(HmacSignatureUtil.verify(payload, secret, "deadbeef"));
    }
}
