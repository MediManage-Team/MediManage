package org.example.MediManage.service.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class AIActionPromptBuilder {

    private AIActionPromptBuilder() {
    }

    static boolean supportsDirectCloudFallback(String action) {
        return switch (action) {
            case "checkout_protocol",
                    "detailed_protocol",
                    "validate_prescription",
                    "generic_composition",
                    "inventory_trend",
                    "expiry_strategy",
                    "customer_history",
                    "sales_summary",
                    "restock_suggestion",
                    "db_report_analysis",
                    "combined_analysis",
                    "raw_chat" -> true;
            default -> false;
        };
    }

    static String buildPrompt(String action, JSONObject data) {
        JSONObject safeData = data == null ? new JSONObject() : data;

        return switch (action) {
            case "checkout_protocol" -> checkoutCareProtocolPrompt(toStringList(safeData.optJSONArray("medicines")));
            case "detailed_protocol" -> detailedCareProtocolPrompt(toStringList(safeData.optJSONArray("medicines")));
            case "validate_prescription" -> prescriptionValidationPrompt(safeData.optString("medicines_text", ""));
            case "generic_composition" -> genericCompositionPrompt(safeData.optString("brand_name", ""));
            case "inventory_trend" -> inventoryTrendAnalysisPrompt();
            case "expiry_strategy" -> expiryStrategyPrompt();
            case "customer_history" -> customerHistoryAnalysisPrompt(
                    safeData.optString("customer_name", ""),
                    safeData.optString("diseases", ""));
            case "sales_summary" -> salesSummaryPrompt(
                    safeData.optString("sales_data", ""),
                    safeData.optDouble("total_revenue", 0.0),
                    safeData.optString("top_items", ""));
            case "restock_suggestion" -> restockSuggestionPrompt(safeData.optString("inventory_snapshot", ""));
            case "db_report_analysis" -> dbReportAnalysisPrompt(safeData.optString("report_type", ""));
            case "combined_analysis", "raw_chat" -> contextualPrompt(
                    safeData.optString("prompt", ""),
                    safeData.optString("business_context", ""));
            default -> "";
        };
    }

    static String buildDirectCloudFallbackPrompt(String action, JSONObject data) {
        JSONObject safeData = data == null ? new JSONObject() : data;
        if ("combined_analysis".equals(action)) {
            return combinedMedicalPrecisionPrompt(
                    combinedBusinessFallbackPrompt(safeData.optString("business_context", "")),
                    safeData.optString("prompt", ""));
        }
        return buildPrompt(action, safeData);
    }

    private static List<String> toStringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String checkoutCareProtocolPrompt(List<String> medicines) {
        String meds = String.join("\n", medicines);
        return "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n"
                + meds + "\n"
                + "For EACH medicine, provide a 7-point guide:\n"
                + "1. Mechanism (Simplified)\n"
                + "2. Usage Guide (When/How)\n"
                + "3. Dietary Advice\n"
                + "4. Side Effects\n"
                + "5. Stop Protocol\n"
                + "Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n"
                + "Format as a clean, printable guide.";
    }

    private static String detailedCareProtocolPrompt(List<String> medicines) {
        String meds = String.join("\n", medicines);
        return "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n"
                + meds + "\n"
                + "For EACH medicine, provide these sections with EXACT section names as headers:\n"
                + "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n"
                + "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n"
                + "Format each section as: 'SectionName: content on same line'. "
                + "Start each medicine with its full name on its own line. "
                + "Do NOT use markdown formatting like ** or #.";
    }

    private static String prescriptionValidationPrompt(String medicinesText) {
        return "Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. "
                + "List any concerns concisely:\n\n" + medicinesText;
    }

    private static String genericCompositionPrompt(String brandName) {
        return "What is the generic composition of the medicine '" + brandName + "'? "
                + "Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text.";
    }

    private static String inventoryTrendAnalysisPrompt() {
        return "Analyze the sales trends. "
                + "1. Identify top-selling and slow-moving items.\n"
                + "2. Suggest seasonal stock adjustments.\n"
                + "3. Generate a 'To-Buy List' for the distributor with recommended quantities.";
    }

    private static String expiryStrategyPrompt() {
        return "For these expiring medicines:\n"
                + "1. Suggest discount strategies to clear stock before expiry.\n"
                + "2. Provide chemical-specific disposal instructions for safety.\n"
                + "3. Flag any controlled or hazardous substances requiring special handling.";
    }

    private static String customerHistoryAnalysisPrompt(String customerName, String diseases) {
        String token = customerName == null || customerName.isBlank()
                ? "Unknown"
                : String.format("CUST_%04d", Math.abs(customerName.hashCode()) % 10000);
        StringBuilder context = new StringBuilder("Customer Token: ").append(token).append('\n');
        if (diseases != null && !diseases.isBlank()) {
            context.append("Known Conditions: ").append(diseases).append('\n');
        }
        return "As a pharmacist's AI assistant, analyze this customer profile:\n\n"
                + context
                + "Provide:\n"
                + "1. Health risk summary based on known conditions\n"
                + "2. Medication recommendations and precautions for these conditions\n"
                + "3. Drug interaction warnings to watch for\n"
                + "4. Lifestyle and dietary suggestions\n\n"
                + "Be concise and clinically relevant.";
    }

    private static String salesSummaryPrompt(String salesData, double totalRevenue, String topItems) {
        return "I am a Pharmacist. Create a highly detailed 'Patient Care Assistance Report' based on the following sales data:\n"
                + "Top Selling Medicines: " + topItems + "\n\n"
                + "Provide a structured, extensive multi-paragraph guide with these EXACT section names as headers:\n"
                + "Public Health Trend\n"
                + "Care Assistance Advice\n"
                + "Inventory Recommendations\n\n"
                + "Format each section as: 'SectionName: content'. "
                + "Under 'Care Assistance Advice', provide highly detailed and specific lifestyle, dietary, and non-medical advice that pharmacists should give to patients buying these top-selling medicines. "
                + "Do NOT use markdown formatting like ** or #. \n\n"
                + "CRITICAL: The response MUST be highly detailed, clinical, and extensive. Write at least 4-6 comprehensive sentences (a full paragraph) for EACH of the three sections. Provide thorough, professional depth and completely fill the text area to ensure deep insight is delivered.";
    }

    private static String restockSuggestionPrompt(String inventorySnapshot) {
        return "Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n"
                + inventorySnapshot + "\n\n"
                + "Prioritize by: critically low stock -> high demand -> seasonal needs.\n"
                + "Format as a numbered list with quantities to order.";
    }

    private static String dbReportAnalysisPrompt(String reportType) {
        if (reportType.contains("Inventory")) {
            return "Analyze this pharmacy inventory data. Summarize the key findings: "
                    + "total medicines shown, price range, stock levels. "
                    + "Flag any concerns and give 2-3 actionable recommendations.";
        }
        if (reportType.contains("Low Stock")) {
            return "Analyze these low stock items. Which medicines need urgent reordering? "
                    + "Prioritize by criticality. Give specific reorder recommendations.";
        }
        if (reportType.contains("Expiring")) {
            return "Analyze these expiring medicines. Which should be discounted for quick sale? "
                    + "Which should be returned to supplier? Prioritize by urgency.";
        }
        if (reportType.contains("Sales")) {
            return "Analyze this sales data. How is today's performance? "
                    + "Compare with the 30-day trend. Any insights or suggestions?";
        }
        if (reportType.contains("Customer")) {
            return "Analyze customer balances. Who are the highest debtors? "
                    + "Suggest a follow-up strategy for debt recovery.";
        }
        return "Analyze this pharmacy data and provide a helpful summary with actionable insights.";
    }

    private static String contextualPrompt(String prompt, String businessContext) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        String safeContext = businessContext == null ? "" : businessContext.trim();
        if (!safeContext.isBlank() && !safePrompt.isBlank()) {
            return "### Business Data\n" + safeContext + "\n\n### Query\n" + safePrompt;
        }
        if (!safeContext.isBlank()) {
            return safeContext;
        }
        return safePrompt;
    }

    private static String combinedBusinessFallbackPrompt(String businessContext) {
        return "Business Data Summary:\n" + (businessContext == null ? "" : businessContext);
    }

    private static String combinedMedicalPrecisionPrompt(String localResult, String prompt) {
        return "Based on this business analysis:\n\n" + localResult + "\n\n"
                + "Now answer the following with medical/pharmaceutical precision:\n"
                + (prompt == null ? "" : prompt);
    }
}
