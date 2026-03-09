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
                String template = "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                                "{{MEDICINES}}\n" +
                                "For EACH medicine, provide a 7-point guide:\n" +
                                "1. Mechanism (Simplified)\n" +
                                "2. Usage Guide (When/How)\n" +
                                "3. Dietary Advice\n" +
                                "4. Side Effects\n" +
                                "5. Stop Protocol\n" +
                                "Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n" +
                                "Format as a clean, printable guide.";
                return replaceToken(template, "MEDICINES", medicineBulletList(billItems));
        }

        public static String detailedCareProtocolPrompt(List<BillItem> billItems) {
                String template = "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                                "{{MEDICINES}}\n" +
                                "For EACH medicine, provide these sections with EXACT section names as headers:\n" +
                                "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n"
                                +
                                "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n" +
                                "Format each section as: 'SectionName: content on same line'. " +
                                "Start each medicine with its full name on its own line. " +
                                "Do NOT use markdown formatting like ** or #.";
                return replaceToken(template, "MEDICINES", medicineBulletList(billItems));
        }

        public static String prescriptionValidationPrompt(String medicinesText) {
                String template = "Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. "
                                +
                                "List any concerns concisely:\n\n{{MEDICINES_TEXT}}";
                return replaceToken(template, "MEDICINES_TEXT", medicinesText == null ? "" : medicinesText);
        }

        public static String genericCompositionPrompt(String brandName) {
                String template = "What is the generic composition of the medicine '{{BRAND_NAME}}'? " +
                                "Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text.";
                return replaceToken(template, "BRAND_NAME", brandName == null ? "" : brandName);
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
                String customerToken = AIInputSafetyGuard.tokenizePersonName(customerName);
                String safeConditions = AIInputSafetyGuard.sanitizeClinicalConditions(diseases);
                StringBuilder context = new StringBuilder();
                context.append("Customer Token: ").append(customerToken).append("\n");
                if (safeConditions != null && !safeConditions.isEmpty()) {
                        context.append("Known Conditions: ").append(safeConditions).append("\n");
                }

                String template = "As a pharmacist's AI assistant, analyze this customer profile:\n\n{{CUSTOMER_CONTEXT}}"
                                +
                                "\nProvide:\n" +
                                "1. Health risk summary based on known conditions\n" +
                                "2. Medication recommendations and precautions for these conditions\n" +
                                "3. Drug interaction warnings to watch for\n" +
                                "4. Lifestyle and dietary suggestions\n\n" +
                                "Be concise and clinically relevant.";
                return replaceToken(template, "CUSTOMER_CONTEXT", context.toString());
        }

        public static String salesSummaryPrompt(Map<String, Double> salesData, double totalRevenue, Map<String, Integer> topItems) {
                String data = salesData == null ? ""
                                : salesData.entrySet().stream()
                                                .map(e -> e.getKey() + ": INR " + String.format("%.2f", e.getValue()))
                                                .collect(Collectors.joining("\n"));
                String revenue = String.format("%.2f", totalRevenue);
                String items = topItems == null ? "" : topItems.entrySet().stream()
                                .limit(5)
                                .map(e -> e.getKey() + " (" + e.getValue() + " units)")
                                .collect(Collectors.joining(", "));

                String template = "I am a Pharmacist. Create a detailed 'Patient Care Assistance Report' based on the following sales data:\n"
                                +
                                "Top Selling Medicines: {{TOP_ITEMS}}\n\n" +
                                "Provide a structured, multi-point guide with these EXACT section names as headers:\n" +
                                "Public Health Trend\n" +
                                "Care Assistance Advice\n" +
                                "Inventory Recommendations\n\n" +
                                "Format each section as: 'SectionName: content'. " +
                                "Under 'Care Assistance Advice', provide specific lifestyle, dietary, and non-medical advice that pharmacists should give to patients buying these top-selling medicines. " +
                                "Do NOT use markdown formatting like ** or #. \n\n" +
                                "CRITICAL: The response MUST be highly detailed and clinical. Write at least 3-4 comprehensive sentences for EACH of the three sections. Provide a thorough, professional paragraph for each section.";
                
                String res1 = replaceToken(template, "DAILY_SALES_DATA", data);
                String res2 = replaceToken(res1, "TOTAL_REVENUE", revenue);
                return replaceToken(res2, "TOP_ITEMS", items);
        }

        public static String restockSuggestionPrompt(String inventorySnapshot) {
                String template = "Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n" +
                                "{{INVENTORY_SNAPSHOT}}\n\n" +
                                "Prioritize by: critically low stock -> high demand -> seasonal needs.\n" +
                                "Format as a numbered list with quantities to order.";
                return replaceToken(template, "INVENTORY_SNAPSHOT", inventorySnapshot == null ? "" : inventorySnapshot);
        }

        public static String dbReportAnalysisPrompt(String reportType) {
                String safeType = reportType == null ? "" : reportType;
                if (safeType.contains("Inventory")) {
                        return "Analyze this pharmacy inventory data. Summarize the key findings: " +
                                        "total medicines shown, price range, stock levels. " +
                                        "Flag any concerns and give 2-3 actionable recommendations.";
                }
                if (safeType.contains("Low Stock")) {
                        return "Analyze these low stock items. Which medicines need urgent reordering? " +
                                        "Prioritize by criticality. Give specific reorder recommendations.";
                }
                if (safeType.contains("Expiring")) {
                        return "Analyze these expiring medicines. Which should be discounted for quick sale? " +
                                        "Which should be returned to supplier? Prioritize by urgency.";
                }
                if (safeType.contains("Sales")) {
                        return "Analyze this sales data. How is today's performance? " +
                                        "Compare with the 30-day trend. Any insights or suggestions?";
                }
                if (safeType.contains("Customer")) {
                        return "Analyze customer balances. Who are the highest debtors? " +
                                        "Suggest a follow-up strategy for debt recovery.";
                }
                return "Analyze this pharmacy data and provide a helpful summary with actionable insights.";
        }

        public static String combinedBusinessSummaryPrompt(String prompt) {
                String template = "Analyze this business data and produce a concise summary with key findings:\n{{PROMPT}}";
                return replaceToken(template, "PROMPT", prompt == null ? "" : prompt);
        }

        public static String combinedBusinessFallbackPrompt(String businessContext) {
                String template = "Business Data Summary:\n{{BUSINESS_CONTEXT}}";
                return replaceToken(template, "BUSINESS_CONTEXT", businessContext == null ? "" : businessContext);
        }

        public static String combinedMedicalPrecisionPrompt(String localResult, String prompt) {
                String template = "Based on this business analysis:\n\n{{LOCAL_RESULT}}\n\n" +
                                "Now answer the following with medical/pharmaceutical precision:\n{{PROMPT}}";
                return replaceToken(
                                replaceToken(template, "LOCAL_RESULT", localResult == null ? "" : localResult),
                                "PROMPT",
                                prompt == null ? "" : prompt);
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

        private static String replaceToken(String template, String tokenName, String value) {
                String safeTemplate = template == null ? "" : template;
                String safeTokenName = tokenName == null ? "" : tokenName.trim();
                if (safeTokenName.isEmpty()) {
                        return safeTemplate;
                }
                String safeValue = value == null ? "" : value;
                return safeTemplate.replace("{{" + safeTokenName + "}}", safeValue);
        }

        private static String medicineBulletList(List<BillItem> billItems) {
                return String.join("\n", AIInputSafetyGuard.approvedMedicinePromptLines(billItems));
        }
}
