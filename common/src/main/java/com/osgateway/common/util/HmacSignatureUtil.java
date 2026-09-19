package com.osgateway.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class HmacSignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignatureUtil() {
    }

    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute HMAC signature", ex);
        }
    }

    public static boolean verify(String payload, String secret, String expectedSignature) {
        if (expectedSignature == null) {
            return false;
        }
        String actual = sign(payload, secret);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}
