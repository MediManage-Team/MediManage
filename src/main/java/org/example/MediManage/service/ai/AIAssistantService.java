package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        StringBuilder medicineList = new StringBuilder();
        items.forEach(item -> medicineList.append("- ").append(item.getName()).append("\n"));

        String prompt = "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                medicineList + "\n" +
                "For EACH medicine, provide these sections with EXACT section names as headers:\n" +
                "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n" +
                "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n" +
                "Format each section as: 'SectionName: content on same line'. " +
                "Start each medicine with its full name on its own line. " +
                "Do NOT use markdown formatting like ** or #.";

        return orchestrator.cloudQuery(prompt);
    }

    // ======================== PRESCRIPTION VALIDATION ========================

    /**
     * Validate a prescription for drug interactions and safety issues.
     */
    public CompletableFuture<String> validatePrescription(List<String> medicineNames) {
        String medicines = String.join(", ", medicineNames);
        String prompt = "Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. "
                +
                "List any concerns concisely:\n\n" + medicines;
        return orchestrator.processQuery(prompt, true, false);
    }

    // ======================== CUSTOMER ANALYSIS ========================

    /**
     * Analyze a customer's medication history and provide insights.
     */
    public CompletableFuture<String> analyzeCustomerHistory(int customerId, String customerName, String diseases) {
        // Build context from customer data
        StringBuilder context = new StringBuilder();
        context.append("Customer: ").append(customerName).append("\n");
        if (diseases != null && !diseases.isEmpty()) {
            context.append("Known Conditions: ").append(diseases).append("\n");
        }

        String prompt = "As a pharmacist's AI assistant, analyze this customer profile:\n\n" + context +
                "\nProvide:\n" +
                "1. Health risk summary based on known conditions\n" +
                "2. Medication recommendations and precautions for these conditions\n" +
                "3. Drug interaction warnings to watch for\n" +
                "4. Lifestyle and dietary suggestions\n\n" +
                "Be concise and clinically relevant.";

        return orchestrator.processQuery(prompt, true, false);
    }

    // ======================== REPORTS ANALYSIS ========================

    /**
     * Generate an AI summary of sales report data.
     */
    public CompletableFuture<String> generateReportSummary(Map<String, Double> salesData, double totalRevenue) {
        String dataStr = salesData.entrySet().stream()
                .map(e -> e.getKey() + ": ₹" + String.format("%.2f", e.getValue()))
                .collect(Collectors.joining("\n"));

        String prompt = "Analyze the following pharmacy sales data and provide a concise business summary:\n\n" +
                "Daily Sales:\n" + dataStr + "\n" +
                "Total Revenue: ₹" + String.format("%.2f", totalRevenue) + "\n\n" +
                "Provide:\n" +
                "1. Sales trend observation (up/down/stable)\n" +
                "2. Peak and low days\n" +
                "3. Revenue optimization suggestions\n" +
                "4. One actionable recommendation\n\n" +
                "Keep it brief — max 5 lines.";

        return orchestrator.processQuery(prompt, false, false);
    }

    // ======================== INVENTORY SUGGESTIONS ========================

    /**
     * Generate AI-powered restock suggestions based on inventory data.
     */
    public CompletableFuture<String> suggestRestock(String inventorySnapshot) {
        String prompt = "Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n" +
                inventorySnapshot + "\n\n" +
                "Prioritize by: critically low stock → high demand → seasonal needs.\n" +
                "Format as a numbered list with quantities to order.";

        return orchestrator.processQuery(prompt, false, false);
    }
}
