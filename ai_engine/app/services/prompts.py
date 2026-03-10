"""
Central catalog for AI prompt templates.
Replaces Java's AIPromptCatalog.
"""

def checkout_care_protocol_prompt(medicines: list[str]) -> str:
    meds_str = "\n".join(medicines)
    return (
        f"I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n"
        f"{meds_str}\n"
        f"For EACH medicine, provide a 7-point guide:\n"
        f"1. Mechanism (Simplified)\n"
        f"2. Usage Guide (When/How)\n"
        f"3. Dietary Advice\n"
        f"4. Side Effects\n"
        f"5. Stop Protocol\n"
        f"Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n"
        f"Format as a clean, printable guide."
    )

def detailed_care_protocol_prompt(medicines: list[str]) -> str:
    meds_str = "\n".join(medicines)
    return (
        f"I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n"
        f"{meds_str}\n"
        f"For EACH medicine, provide these sections with EXACT section names as headers:\n"
        f"Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n"
        f"Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n"
        f"Format each section as: 'SectionName: content on same line'. "
        f"Start each medicine with its full name on its own line. "
        f"Do NOT use markdown formatting like ** or #."
    )

def prescription_validation_prompt(medicines_text: str) -> str:
    return (
        f"Validate the following prescription for drug-drug interactions, dosage safety, and contraindications. "
        f"List any concerns concisely:\n\n{medicines_text}"
    )

def generic_composition_prompt(brand_name: str) -> str:
    return (
        f"What is the generic composition of the medicine '{brand_name}'? "
        f"Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text."
    )

def inventory_trend_analysis_prompt() -> str:
    return (
        "Analyze the sales trends. "
        "1. Identify top-selling and slow-moving items.\n"
        "2. Suggest seasonal stock adjustments.\n"
        "3. Generate a 'To-Buy List' for the distributor with recommended quantities."
    )

def expiry_strategy_prompt() -> str:
    return (
        "For these expiring medicines:\n"
        "1. Suggest discount strategies to clear stock before expiry.\n"
        "2. Provide chemical-specific disposal instructions for safety.\n"
        "3. Flag any controlled or hazardous substances requiring special handling."
    )

def customer_history_analysis_prompt(customer_name: str, diseases: str) -> str:
    # Tokenize customer name to ensure privacy
    token = f"CUST_{abs(hash(customer_name)) % 10000:04d}" if customer_name else "Unknown"
    
    context = f"Customer Token: {token}\n"
    if diseases:
        context += f"Known Conditions: {diseases}\n"

    return (
        f"As a pharmacist's AI assistant, analyze this customer profile:\n\n{context}\n"
        f"Provide:\n"
        f"1. Health risk summary based on known conditions\n"
        f"2. Medication recommendations and precautions for these conditions\n"
        f"3. Drug interaction warnings to watch for\n"
        f"4. Lifestyle and dietary suggestions\n\n"
        f"Be concise and clinically relevant."
    )

def sales_summary_prompt(sales_data_str: str, total_revenue: float, top_items_str: str) -> str:
    return (
        f"I am a Pharmacist. Create a highly detailed 'Patient Care Assistance Report' based on the following sales data:\n"
        f"Top Selling Medicines: {top_items_str}\n\n"
        f"Provide a structured, extensive multi-paragraph guide with these EXACT section names as headers:\n"
        f"Public Health Trend\n"
        f"Care Assistance Advice\n"
        f"Inventory Recommendations\n\n"
        f"Format each section as: 'SectionName: content'. "
        f"Under 'Care Assistance Advice', provide highly detailed and specific lifestyle, dietary, and non-medical advice that pharmacists should give to patients buying these top-selling medicines. "
        f"Do NOT use markdown formatting like ** or #. \n\n"
        f"CRITICAL: The response MUST be highly detailed, clinical, and extensive. Write at least 4-6 comprehensive sentences (a full paragraph) for EACH of the three sections. Provide thorough, professional depth and completely fill the text area to ensure deep insight is delivered."
    )

def restock_suggestion_prompt(inventory_snapshot: str) -> str:
    return (
        f"Based on this pharmacy inventory snapshot, suggest items to restock urgently:\n\n"
        f"{inventory_snapshot}\n\n"
        f"Prioritize by: critically low stock -> high demand -> seasonal needs.\n"
        f"Format as a numbered list with quantities to order."
    )

def combined_business_summary_prompt(prompt: str) -> str:
    return f"Analyze this business data and produce a concise summary with key findings:\n{prompt}"

def combined_business_fallback_prompt(business_context: str) -> str:
    return f"Business Data Summary:\n{business_context}"

def combined_medical_precision_prompt(local_result: str, prompt: str) -> str:
    return (
        f"Based on this business analysis:\n\n{local_result}\n\n"
        f"Now answer the following with medical/pharmaceutical precision:\n{prompt}"
    )

def db_report_analysis_prompt(report_type: str) -> str:
    if "Inventory" in report_type:
        return (
            "Analyze this pharmacy inventory data. Summarize the key findings: "
            "total medicines shown, price range, stock levels. "
            "Flag any concerns and give 2-3 actionable recommendations."
        )
    if "Low Stock" in report_type:
        return (
            "Analyze these low stock items. Which medicines need urgent reordering? "
            "Prioritize by criticality. Give specific reorder recommendations."
        )
    if "Expiring" in report_type:
        return (
            "Analyze these expiring medicines. Which should be discounted for quick sale? "
            "Which should be returned to supplier? Prioritize by urgency."
        )
    if "Sales" in report_type:
        return (
            "Analyze this sales data. How is today's performance? "
            "Compare with the 30-day trend. Any insights or suggestions?"
        )
    if "Customer" in report_type:
        return (
            "Analyze customer balances. Who are the highest debtors? "
            "Suggest a follow-up strategy for debt recovery."
        )
    return "Analyze this pharmacy data and provide a helpful summary with actionable insights."

# Static strings for DB Query commands from Java
INVENTORY_SUMMARY_DB_QUERY = "Show inventory summary - list top medicines with stock quantities and prices"
LOW_STOCK_DB_QUERY = "Show low stock medicines that are running out"
EXPIRY_DB_QUERY = "Show medicines expiring soon within the next 90 days"
SALES_DB_QUERY = "Show today's sales summary and revenue"
CUSTOMER_BALANCES_DB_QUERY = "Show customer balances and outstanding debts"
TOP_SELLERS_DB_QUERY = "Show top 20 best-selling medicines by total quantity sold from bill items"
PROFIT_DB_QUERY = "Show profit analysis - total revenue, total bills, average bill value, and revenue by payment mode"
PRESCRIPTION_OVERVIEW_DB_QUERY = "Show recent prescriptions with patient name, doctor, status, and medicines prescribed"
DRUG_INTERACTION_DB_QUERY = "Show recent bills with multiple medicines to check for potential drug-drug interactions. List patient and all medicines per bill"
REORDER_SUGGESTIONS_DB_QUERY = "Show medicines with stock below 20 units that have been sold recently - suggest reorder quantities based on past sales velocity"
DAILY_SUMMARY_DB_QUERY = "Give a complete daily summary: total sales today, number of bills, new customers, pending prescriptions, low stock alerts, and expiring medicines count"
