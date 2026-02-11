package org.example.MediManage.service.ai;

import java.util.concurrent.CompletableFuture;

public interface AIService {
    CompletableFuture<String> chat(String prompt);

    boolean isAvailable();

    String getProviderName();
}
