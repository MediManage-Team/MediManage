package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central prompt templates used across AI features.
 */
public final class AIPromptCatalog {
    private static final AIPromptRegistryService PROMPT_REGISTRY = AIPromptRegistryService.getInstance();

    private static final String KEY_CHECKOUT_CARE_PROTOCOL = "checkout_care_protocol";
    private static final String KEY_DETAILED_CARE_PROTOCOL = "detailed_care_protocol";
    private static final String KEY_PRESCRIPTION_VALIDATION = "prescription_validation";
    private static final String KEY_GENERIC_COMPOSITION = "generic_composition";
    private static final String KEY_INVENTORY_TREND_ANALYSIS = "inventory_trend_analysis";
    private static final String KEY_EXPIRY_STRATEGY = "expiry_strategy";
    private static final String KEY_CUSTOMER_HISTORY_ANALYSIS = "customer_history_analysis";
    private static final String KEY_SALES_SUMMARY = "sales_summary";
    private static final String KEY_RESTOCK_SUGGESTION = "restock_suggestion";
    private static final String KEY_DB_REPORT_INVENTORY = "db_report_analysis_inventory";
    private static final String KEY_DB_REPORT_LOW_STOCK = "db_report_analysis_low_stock";
    private static final String KEY_DB_REPORT_EXPIRING = "db_report_analysis_expiring";
    private static final String KEY_DB_REPORT_SALES = "db_report_analysis_sales";
    private static final String KEY_DB_REPORT_CUSTOMER = "db_report_analysis_customer";
    private static final String KEY_DB_REPORT_DEFAULT = "db_report_analysis_default";
    private static final String KEY_COMBINED_BUSINESS_SUMMARY = "combined_business_summary";
    private static final String KEY_COMBINED_BUSINESS_FALLBACK = "combined_business_fallback";
    private static final String KEY_COMBINED_MEDICAL_PRECISION = "combined_medical_precision";
    private static final String KEY_DB_QUERY_INVENTORY_SUMMARY = "db_query_inventory_summary";
    private static final String KEY_DB_QUERY_LOW_STOCK = "db_query_low_stock";
    private static final String KEY_DB_QUERY_EXPIRY = "db_query_expiry";
    private static final String KEY_DB_QUERY_SALES = "db_query_sales";
    private static final String KEY_DB_QUERY_CUSTOMER_BALANCES = "db_query_customer_balances";
    private static final String KEY_DB_QUERY_TOP_SELLERS = "db_query_top_sellers";
    private static final String KEY_DB_QUERY_PROFIT = "db_query_profit";
    private static final String KEY_DB_QUERY_PRESCRIPTION_OVERVIEW = "db_query_prescription_overview";
    private static final String KEY_DB_QUERY_DRUG_INTERACTION = "db_query_drug_interaction";
    private static final String KEY_DB_QUERY_REORDER_SUGGESTIONS = "db_query_reorder_suggestions";
    private static final String KEY_DB_QUERY_DAILY_SUMMARY = "db_query_daily_summary";
    private static final String KEY_SUBSCRIPTION_TRANSLATION = "subscription_multilingual_translation";

    private AIPromptCatalog() {
    }

    public static String checkoutCareProtocolPrompt(List<BillItem> billItems) {
        String template = resolveTemplate(
                KEY_CHECKOUT_CARE_PROTOCOL,
                "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                        "{{MEDICINES}}\n" +
                        "For EACH medicine, provide a 7-point guide:\n" +
                        "1. Mechanism (Simplified)\n" +
                        "2. Usage Guide (When/How)\n" +
                        "3. Dietary Advice\n" +
                        "4. Side Effects\n" +
                        "5. Stop Protocol\n" +
                        "Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n" +
                        "Format as a clean, printable guide.");
        return replaceToken(template, "MEDICINES", medicineBulletList(billItems));
    }

    public static String detailedCareProtocolPrompt(List<BillItem> billItems) {
        String template = resolveTemplate(
                KEY_DETAILED_CARE_PROTOCOL,
                "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                        "{{MEDICINES}}\n" +
                        "For EACH medicine, provide these sections with EXACT section names as headers:\n" +
                        "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n" +
                        "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n" +
                        "Format each section as: 'SectionName: content on same line'. " +
                        "Start each medicine with its full name on its own line. " +
                        "Do NOT use markdown formatting like ** or #.");
        return replaceToken(template, "MEDICINES", medicineBulletList(billItems));
    }

    public static String prescriptionValidationPrompt(String medicinesText) {
        String template = resolveTemplate(
                KEY_PRESCRIPTION_VALIDATION,
                "Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. " +
                        "List any concerns concisely:\n\n{{MEDICINES_TEXT}}");
        return replaceToken(template, "MEDICINES_TEXT", medicinesText == null ? "" : medicinesText);
    }

    public static String genericCompositionPrompt(String brandName) {
        String template = resolveTemplate(
                KEY_GENERIC_COMPOSITION,
                "What is the generic composition of the medicine '{{BRAND_NAME}}'? " +
                        "Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text.");
        return replaceToken(template, "BRAND_NAME", brandName == null ? "" : brandName);
    }

    public static String inventoryTrendAnalysisPrompt() {
        return resolveTemplate(
                KEY_INVENTORY_TREND_ANALYSIS,
                "Analyze the sales trends. " +
                        "1. Identify top-selling and slow-moving items.\n" +
                        "2. Suggest seasonal stock adjustments.\n" +
                        "3. Generate a 'To-Buy List' for the distributor with recommended quantities.");
    }

    public static String expiryStrategyPrompt() {
        return resolveTemplate(
                KEY_EXPIRY_STRATEGY,
                "For these expiring medicines:\n" +
                        "1. Suggest discount strategies to clear stock before expiry.\n" +
                        "2. Provide chemical-specific disposal instructions for safety.\n" +
                        "3. Flag any controlled or hazardous substances requiring special handling.");
    }

    public static String customerHistoryAnalysisPrompt(String customerName, String diseases) {
        String customerToken = AIInputSafetyGuard.tokenizePersonName(customerName);
        String safeConditions = AIInputSafetyGuard.sanitizeClinicalConditions(diseases);
        StringBuilder context = new StringBuilder();
        context.append("Customer Token: ").append(customerToken).append("\n");
        if (safeConditions != null && !safeConditions.isEmpty()) {
            context.append("Known Conditions: ").append(safeConditions).append("\n");
        }

        String template = resolveTemplate(
                KEY_CUSTOMER_HISTORY_ANALYSIS,
                "As a pharmacist's AI assistant, analyze this customer profile:\n\n{{CUSTOMER_CONTEXT}}" +
                        "\nProvide:\n" +
                        "1. Health risk summary based on known conditions\n" +
                        "2. Medication recommendations and precautions for these conditions\n" +
                        "3. Drug interaction warnings to watch for\n" +
                        "4. Lifestyle and dietary suggestions\n\n" +
                        "Be concise and clinically relevant.");
        return replaceToken(template, "CUSTOMER_CONTEXT", context.toString());
    }

    public static String salesSummaryPrompt(Map<String, Double> salesData, double totalRevenue) {
        String data = salesData == null ? "" : salesData.entrySet().stream()
                .map(e -> e.getKey() + ": INR " + String.format("%.2f", e.getValue()))
                .collect(Collectors.joining("\n"));
        String revenue = String.format("%.2f", totalRevenue);

        String template = resolveTemplate(
                KEY_SALES_SUMMARY,
                "Analyze the following pharmacy sales data and provide a concise business summary:\n\n" +
                        "Daily Sales:\n{{DAILY_SALES_DATA}}\n" +
                        "Total Revenue: INR {{TOTAL_REVENUE}}\n\n" +
                        "Provide:\n" +
                        "1. Sales trend observation (up/down/stable)\n" +
                        "2. Peak and low days\n" +
                        "3. Revenue optimization suggestions\n" +
                        "4. One actionable recommendation\n\n" +
                        "Keep it brief - max 5 lines.");
        return replaceToken(replaceToken(template, "DAILY_SALES_DATA", data), "TOTAL_REVENUE", revenue);
    }

    public static String restockSuggestionPrompt(String inventorySnapshot) {
        String template = resolveTemplate(
                KEY_RESTOCK_SUGGESTION,
                "Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n" +
                        "{{INVENTORY_SNAPSHOT}}\n\n" +
                        "Prioritize by: critically low stock -> high demand -> seasonal needs.\n" +
                        "Format as a numbered list with quantities to order.");
        return replaceToken(template, "INVENTORY_SNAPSHOT", inventorySnapshot == null ? "" : inventorySnapshot);
    }

    public static String dbReportAnalysisPrompt(String reportType) {
        String safeType = reportType == null ? "" : reportType;
        if (safeType.contains("Inventory")) {
            return resolveTemplate(
                    KEY_DB_REPORT_INVENTORY,
                    "Analyze this pharmacy inventory data. Summarize the key findings: " +
                            "total medicines shown, price range, stock levels. " +
                            "Flag any concerns and give 2-3 actionable recommendations.");
        }
        if (safeType.contains("Low Stock")) {
            return resolveTemplate(
                    KEY_DB_REPORT_LOW_STOCK,
                    "Analyze these low stock items. Which medicines need urgent reordering? " +
                            "Prioritize by criticality. Give specific reorder recommendations.");
        }
        if (safeType.contains("Expiring")) {
            return resolveTemplate(
                    KEY_DB_REPORT_EXPIRING,
                    "Analyze these expiring medicines. Which should be discounted for quick sale? " +
                            "Which should be returned to supplier? Prioritize by urgency.");
        }
        if (safeType.contains("Sales")) {
            return resolveTemplate(
                    KEY_DB_REPORT_SALES,
                    "Analyze this sales data. How is today's performance? " +
                            "Compare with the 30-day trend. Any insights or suggestions?");
        }
        if (safeType.contains("Customer")) {
            return resolveTemplate(
                    KEY_DB_REPORT_CUSTOMER,
                    "Analyze customer balances. Who are the highest debtors? " +
                            "Suggest a follow-up strategy for debt recovery.");
        }
        return resolveTemplate(
                KEY_DB_REPORT_DEFAULT,
                "Analyze this pharmacy data and provide a helpful summary with actionable insights.");
    }

    public static String combinedBusinessSummaryPrompt(String prompt) {
        String template = resolveTemplate(
                KEY_COMBINED_BUSINESS_SUMMARY,
                "Analyze this business data and produce a concise summary with key findings:\n{{PROMPT}}");
        return replaceToken(template, "PROMPT", prompt == null ? "" : prompt);
    }

    public static String combinedBusinessFallbackPrompt(String businessContext) {
        String template = resolveTemplate(
                KEY_COMBINED_BUSINESS_FALLBACK,
                "Business Data Summary:\n{{BUSINESS_CONTEXT}}");
        return replaceToken(template, "BUSINESS_CONTEXT", businessContext == null ? "" : businessContext);
    }

    public static String combinedMedicalPrecisionPrompt(String localResult, String prompt) {
        String template = resolveTemplate(
                KEY_COMBINED_MEDICAL_PRECISION,
                "Based on this business analysis:\n\n{{LOCAL_RESULT}}\n\n" +
                        "Now answer the following with medical/pharmaceutical precision:\n{{PROMPT}}");
        return replaceToken(
                replaceToken(template, "LOCAL_RESULT", localResult == null ? "" : localResult),
                "PROMPT",
                prompt == null ? "" : prompt);
    }

    public static String inventorySummaryDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_INVENTORY_SUMMARY,
                "Show inventory summary - list top medicines with stock quantities and prices");
    }

    public static String lowStockDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_LOW_STOCK,
                "Show low stock medicines that are running out");
    }

    public static String expiryDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_EXPIRY,
                "Show medicines expiring soon within the next 90 days");
    }

    public static String salesDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_SALES,
                "Show today's sales summary and revenue");
    }

    public static String customerBalancesDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_CUSTOMER_BALANCES,
                "Show customer balances and outstanding debts");
    }

    public static String topSellersDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_TOP_SELLERS,
                "Show top 20 best-selling medicines by total quantity sold from bill items");
    }

    public static String profitDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_PROFIT,
                "Show profit analysis - total revenue, total bills, average bill value, and revenue by payment mode");
    }

    public static String prescriptionOverviewDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_PRESCRIPTION_OVERVIEW,
                "Show recent prescriptions with patient name, doctor, status, and medicines prescribed");
    }

    public static String drugInteractionCheckDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_DRUG_INTERACTION,
                "Show recent bills with multiple medicines to check for potential drug-drug interactions. " +
                        "List patient and all medicines per bill");
    }

    public static String reorderSuggestionsDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_REORDER_SUGGESTIONS,
                "Show medicines with stock below 20 units that have been sold recently - " +
                        "suggest reorder quantities based on past sales velocity");
    }

    public static String dailySummaryDbQueryPrompt() {
        return resolveTemplate(
                KEY_DB_QUERY_DAILY_SUMMARY,
                "Give a complete daily summary: total sales today, number of bills, new customers, " +
                        "pending prescriptions, low stock alerts, and expiring medicines count");
    }

    public static String subscriptionMultilingualTranslationPrompt(String englishSnippet, String languageName) {
        String template = resolveTemplate(
                KEY_SUBSCRIPTION_TRANSLATION,
                "Translate this pharmacy billing subscription explanation to {{LANGUAGE_NAME}}.\n" +
                        "Keep numbers, percentages, and currency unchanged.\n" +
                        "Output only the translated text, no bullets, no quotes, no extra notes.\n\n" +
                        "{{ENGLISH_SNIPPET}}");
        String withLanguage = replaceToken(template, "LANGUAGE_NAME", languageName == null ? "English" : languageName);
        return replaceToken(withLanguage, "ENGLISH_SNIPPET", englishSnippet == null ? "" : englishSnippet);
    }

    private static String resolveTemplate(String promptKey, String defaultTemplate) {
        return PROMPT_REGISTRY.resolvePromptTemplate(promptKey, defaultTemplate);
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
