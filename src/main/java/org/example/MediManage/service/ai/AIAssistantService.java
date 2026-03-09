package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Centralized AI assistant service routing all AI operations through
 * AIOrchestrator.
 * Replaces direct GeminiService usage and provides domain-specific AI
 * endpoints.
 */
public class AIAssistantService {

    private final AIOrchestrator orchestrator;

    public AIAssistantService() {
        this.orchestrator = AIServiceProvider.get().getOrchestrator();
    }

    // ======================== BILLING / CARE PROTOCOL ========================

    /**
     * Generate a Patient Care Protocol for a list of bill items.
     */
    public CompletableFuture<String> generateCareProtocol(List<BillItem> items) {
        return orchestrator.cloudQuery(AIPromptCatalog.detailedCareProtocolPrompt(items));
    }

    // ======================== PRESCRIPTION VALIDATION ========================

    /**
     * Validate a prescription for drug interactions and safety issues.
     */
    public CompletableFuture<String> validatePrescription(List<String> medicineNames) {
        String medicines = String.join(", ", medicineNames);
        String prompt = AIPromptCatalog.prescriptionValidationPrompt(medicines);
        return orchestrator.processQuery(prompt, true, false);
    }

    // ======================== CUSTOMER ANALYSIS ========================

    /**
     * Analyze a customer's medication history and provide insights.
     */
    public CompletableFuture<String> analyzeCustomerHistory(int customerId, String customerName, String diseases) {
        String prompt = AIPromptCatalog.customerHistoryAnalysisPrompt(customerName, diseases);
        return orchestrator.processQuery(prompt, true, false);
    }

    // ======================== REPORTS ANALYSIS ========================

    /**
     * Generate an AI summary of sales report data and care assistance trends.
     */
    public CompletableFuture<String> generateReportSummary(Map<String, Double> salesData, double totalRevenue, Map<String, Integer> topItems) {
        String prompt = AIPromptCatalog.salesSummaryPrompt(salesData, totalRevenue, topItems);
        return orchestrator.processQuery(prompt, false, false);
    }

    // ======================== INVENTORY SUGGESTIONS ========================

    /**
     * Generate AI-powered restock suggestions based on inventory data.
     */
    public CompletableFuture<String> suggestRestock(String inventorySnapshot) {
        String prompt = AIPromptCatalog.restockSuggestionPrompt(inventorySnapshot);
        return orchestrator.processQuery(prompt, false, false);
    }
}
