package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Centralized AI assistant service routing all AI operations through
 * AIOrchestrator.
 * Now securely forwards data structures to Python's /orchestrate endpoint.
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
        JSONObject data = new JSONObject();
        JSONArray meds = new JSONArray();
        for (BillItem item : items) meds.put(item.getName());
        data.put("medicines", meds);
        return orchestrator.processOrchestration("detailed_protocol", data, "cloud_only", false);
    }

    // ======================== PRESCRIPTION VALIDATION ========================

    /**
     * Validate a prescription for drug interactions and safety issues.
     */
    public CompletableFuture<String> validatePrescription(List<String> medicineNames) {
        String medicines = String.join(", ", medicineNames);
        JSONObject data = new JSONObject().put("medicines_text", medicines);
        return orchestrator.processOrchestration("validate_prescription", data, "cloud_only", false);
    }

    // ======================== CUSTOMER ANALYSIS ========================

    /**
     * Analyze a customer's medication history and provide insights.
     */
    public CompletableFuture<String> analyzeCustomerHistory(int customerId, String customerName, String diseases) {
        JSONObject data = new JSONObject()
            .put("customer_name", customerName)
            .put("diseases", diseases);
        return orchestrator.processOrchestration("customer_history", data, "auto", false);
    }

    // ======================== REPORTS ANALYSIS ========================

    /**
     * Generate an AI summary of sales report data and care assistance trends.
     */
    public CompletableFuture<String> generateReportSummary(Map<String, Double> salesData, double totalRevenue, Map<String, Integer> topItems) {
        JSONObject data = new JSONObject()
            .put("sales_data", salesData.toString())
            .put("total_revenue", totalRevenue)
            .put("top_items", topItems.toString());
        return orchestrator.processOrchestration("sales_summary", data, "cloud_only", false);
    }

    // ======================== INVENTORY SUGGESTIONS ========================

    /**
     * Generate AI-powered restock suggestions based on inventory data.
     */
    public CompletableFuture<String> suggestRestock(String inventorySnapshot) {
        JSONObject data = new JSONObject().put("inventory_snapshot", inventorySnapshot);
        return orchestrator.processOrchestration("restock_suggestion", data, "auto", false);
    }
}
