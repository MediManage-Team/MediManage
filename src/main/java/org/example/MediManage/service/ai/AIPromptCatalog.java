package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central prompt templates used across AI features.
 */
public final class AIPromptCatalog {

    private AIPromptCatalog() {
    }

    public static String checkoutCareProtocolPrompt(List<BillItem> billItems) {
        return "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                medicineBulletList(billItems) + "\n" +
                "For EACH medicine, provide a 7-point guide:\n" +
                "1. Mechanism (Simplified)\n" +
                "2. Usage Guide (When/How)\n" +
                "3. Dietary Advice\n" +
                "4. Side Effects\n" +
                "5. Stop Protocol\n" +
                "Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n" +
                "Format as a clean, printable guide.";
    }

    public static String detailedCareProtocolPrompt(List<BillItem> billItems) {
        return "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                medicineBulletList(billItems) + "\n" +
                "For EACH medicine, provide these sections with EXACT section names as headers:\n" +
                "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n" +
                "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n" +
                "Format each section as: 'SectionName: content on same line'. " +
                "Start each medicine with its full name on its own line. " +
                "Do NOT use markdown formatting like ** or #.";
    }

    public static String prescriptionValidationPrompt(String medicinesText) {
        return "Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. " +
                "List any concerns concisely:\n\n" + medicinesText;
    }

    public static String genericCompositionPrompt(String brandName) {
        return "What is the generic composition of the medicine '" + brandName + "'? " +
                "Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text.";
    }

    public static String inventoryTrendAnalysisPrompt() {
        return "Analyze the sales trends. " +
                "1. Identify top-selling and slow-moving items.\n" +
                "2. Suggest seasonal stock adjustments.\n" +
                "3. Generate a 'To-Buy List' for the distributor with recommended quantities.";
    }

    public static String expiryStrategyPrompt() {
        return "For these expiring medicines:\n" +
                "1. Suggest discount strategies to clear stock before expiry.\n" +
                "2. Provide chemical-specific disposal instructions for safety.\n" +
                "3. Flag any controlled or hazardous substances requiring special handling.";
    }

    public static String customerHistoryAnalysisPrompt(String customerName, String diseases) {
        StringBuilder context = new StringBuilder();
        context.append("Customer: ").append(customerName).append("\n");
        if (diseases != null && !diseases.isEmpty()) {
            context.append("Known Conditions: ").append(diseases).append("\n");
        }

        return "As a pharmacist's AI assistant, analyze this customer profile:\n\n" + context +
                "\nProvide:\n" +
                "1. Health risk summary based on known conditions\n" +
                "2. Medication recommendations and precautions for these conditions\n" +
                "3. Drug interaction warnings to watch for\n" +
                "4. Lifestyle and dietary suggestions\n\n" +
                "Be concise and clinically relevant.";
    }

    public static String salesSummaryPrompt(Map<String, Double> salesData, double totalRevenue) {
        String data = salesData.entrySet().stream()
                .map(e -> e.getKey() + ": INR " + String.format("%.2f", e.getValue()))
                .collect(Collectors.joining("\n"));

        return "Analyze the following pharmacy sales data and provide a concise business summary:\n\n" +
                "Daily Sales:\n" + data + "\n" +
                "Total Revenue: INR " + String.format("%.2f", totalRevenue) + "\n\n" +
                "Provide:\n" +
                "1. Sales trend observation (up/down/stable)\n" +
                "2. Peak and low days\n" +
                "3. Revenue optimization suggestions\n" +
                "4. One actionable recommendation\n\n" +
                "Keep it brief - max 5 lines.";
    }

    public static String restockSuggestionPrompt(String inventorySnapshot) {
        return "Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n" +
                inventorySnapshot + "\n\n" +
                "Prioritize by: critically low stock -> high demand -> seasonal needs.\n" +
                "Format as a numbered list with quantities to order.";
    }

    public static String dbReportAnalysisPrompt(String reportType) {
        if (reportType.contains("Inventory")) {
            return "Analyze this pharmacy inventory data. Summarize the key findings: " +
                    "total medicines shown, price range, stock levels. " +
                    "Flag any concerns and give 2-3 actionable recommendations.";
        }
        if (reportType.contains("Low Stock")) {
            return "Analyze these low stock items. Which medicines need urgent reordering? " +
                    "Prioritize by criticality. Give specific reorder recommendations.";
        }
        if (reportType.contains("Expiring")) {
            return "Analyze these expiring medicines. Which should be discounted for quick sale? " +
                    "Which should be returned to supplier? Prioritize by urgency.";
        }
        if (reportType.contains("Sales")) {
            return "Analyze this sales data. How is today's performance? " +
                    "Compare with the 30-day trend. Any insights or suggestions?";
        }
        if (reportType.contains("Customer")) {
            return "Analyze customer balances. Who are the highest debtors? " +
                    "Suggest a follow-up strategy for debt recovery.";
        }
        return "Analyze this pharmacy data and provide a helpful summary with actionable insights.";
    }

    public static String combinedBusinessSummaryPrompt(String prompt) {
        return "Analyze this business data and produce a concise summary with key findings:\n" + prompt;
    }

    public static String combinedBusinessFallbackPrompt(String businessContext) {
        return "Business Data Summary:\n" + businessContext;
    }

    public static String combinedMedicalPrecisionPrompt(String localResult, String prompt) {
        return "Based on this business analysis:\n\n" +
                localResult + "\n\n" +
                "Now answer the following with medical/pharmaceutical precision:\n" + prompt;
    }

    public static String inventorySummaryDbQueryPrompt() {
        return "Show inventory summary - list top medicines with stock quantities and prices";
    }

    public static String lowStockDbQueryPrompt() {
        return "Show low stock medicines that are running out";
    }

    public static String expiryDbQueryPrompt() {
        return "Show medicines expiring soon within the next 90 days";
    }

    public static String salesDbQueryPrompt() {
        return "Show today's sales summary and revenue";
    }

    public static String customerBalancesDbQueryPrompt() {
        return "Show customer balances and outstanding debts";
    }

    public static String topSellersDbQueryPrompt() {
        return "Show top 20 best-selling medicines by total quantity sold from bill items";
    }

    public static String profitDbQueryPrompt() {
        return "Show profit analysis - total revenue, total bills, average bill value, and revenue by payment mode";
    }

    public static String prescriptionOverviewDbQueryPrompt() {
        return "Show recent prescriptions with patient name, doctor, status, and medicines prescribed";
    }

    public static String drugInteractionCheckDbQueryPrompt() {
        return "Show recent bills with multiple medicines to check for potential drug-drug interactions. " +
                "List patient and all medicines per bill";
    }

    public static String reorderSuggestionsDbQueryPrompt() {
        return "Show medicines with stock below 20 units that have been sold recently - " +
                "suggest reorder quantities based on past sales velocity";
    }

    public static String dailySummaryDbQueryPrompt() {
        return "Give a complete daily summary: total sales today, number of bills, new customers, " +
                "pending prescriptions, low stock alerts, and expiring medicines count";
    }

    private static String medicineBulletList(List<BillItem> billItems) {
        List<BillItem> source = billItems == null ? List.of() : billItems;
        StringBuilder medicineList = new StringBuilder();
        for (BillItem item : source) {
            medicineList.append("- ").append(item.getName()).append("\n");
        }
        return medicineList.toString();
    }
}
