package org.example.MediManage.security;

import java.net.http.HttpRequest;
import java.security.SecureRandom;
import java.util.Base64;

public final class LocalAdminTokenManager {
    public static final String HEADER_NAME = "X-MediManage-Admin-Token";
    public static final String ENV_NAME = "MEDIMANAGE_LOCAL_API_TOKEN";

    private static final String STORE_KEY = "local.ai.admin.token";
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile String cachedToken;

    private LocalAdminTokenManager() {
    }

    public static String getOrCreateToken() {
        String token = cachedToken;
        if (token != null && !token.isBlank()) {
            return token;
        }

        synchronized (LocalAdminTokenManager.class) {
            token = cachedToken;
            if (token != null && !token.isBlank()) {
                return token;
            }

            String stored = SecureSecretStore.get(STORE_KEY);
            if (stored == null || stored.isBlank()) {
                stored = generateToken();
                SecureSecretStore.put(STORE_KEY, stored);
            }

            cachedToken = stored;
            return stored;
        }
    }

    public static void applyHeader(HttpRequest.Builder builder) {
        String token = getOrCreateToken();
        if (token != null && !token.isBlank()) {
            builder.header(HEADER_NAME, token);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
