package org.example.MediManage.service.ai;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InventoryAIService {

    private final MedicineDAO medicineDAO;
    private final BillDAO billDAO;
    private final AIOrchestrator aiOrchestrator;

    public InventoryAIService() {
        this.medicineDAO = new MedicineDAO();
        this.billDAO = new BillDAO();
        this.aiOrchestrator = new AIOrchestrator();
    }

    /**
     * A. Intelligent Substitute Finder
     * 1. Ask AI for generic composition of the brand.
     * 2. Search DB for that generic.
     */
    public CompletableFuture<String> findSubstitutes(String brandName) {
        String prompt = "What is the generic composition of the medicine '" + brandName + "'? " +
                "Provide ONLY the generic name(s) in a comma-separated list. Do not accept any other text.";

        // Use AI to get generic name
        return aiOrchestrator.processQuery(prompt, true, false) // Precision required for drug info
                .thenApply(genericResponse -> {
                    // AI might return extra text, try to clean it or just use it as search keyword
                    // Simple heuristic: take the first line or comma separated values
                    String keyword = genericResponse.split("\n")[0].replace(".", "").trim();

                    List<Medicine> substitutes = medicineDAO.searchByGeneric(keyword);

                    if (substitutes.isEmpty()) {
                        // Try searching by the brand name itself in case it's a mix-up or we have it
                        // under a different name
                        substitutes = medicineDAO.searchByGeneric(brandName);
                    }

                    StringBuilder result = new StringBuilder();
                    result.append("### Substitute Analysis for: ").append(brandName).append("\n");
                    result.append("**Identified Generic:** ").append(keyword).append("\n\n");

                    if (substitutes.isEmpty()) {
                        result.append("❌ No exact substitutes found in stock.");
                    } else {
                        result.append("✅ **Available Substitutes:**\n");
                        for (Medicine m : substitutes) {
                            result.append("- **").append(m.getName()).append("**")
                                    .append(" (").append(m.getCompany()).append(")")
                                    .append(" | Stock: ").append(m.getStock())
                                    .append(" | Price: ₹").append(m.getPrice())
                                    .append("\n");
                        }
                    }
                    return result.toString();
                });
    }

    /**
     * B. Strategic Forecasting & Seasonal Buying
     * 1. Get last 30 days sales.
     * 2. Feed to AI for analysis.
     */
    public CompletableFuture<String> generateRestockReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);

        Map<String, Integer> sales = billDAO.getItemizedSales(start, end);

        if (sales.isEmpty()) {
            return CompletableFuture
                    .completedFuture("No sales data available for the last 30 days to generate a forecast.");
        }

        // Format data for AI
        StringBuilder dataSummary = new StringBuilder();
        sales.entrySet().stream().limit(50).forEach(entry -> dataSummary.append("- ").append(entry.getKey())
                .append(": ").append(entry.getValue()).append(" units\n"));

        String prompt = "Analyze the following sales data (Top 50 items sold in last 30 days) and current date (" + end
                + "). " +
                "1. Identify sales trends.\n" +
                "2. Suggest seasonal stock adjustments (e.g. for upcoming winter/summer).\n" +
                "3. Generate a 'To-Buy List' for the distributor.\n\n" +
                "Sales Data:\n" + dataSummary.toString();

        return aiOrchestrator.processQuery(prompt, false, false); // Local AI is fine for logic/trends
    }

    /**
     * C. Expiry & Disposal Management
     * 1. Get expiring items.
     * 2. Feed to AI for advice.
     */
    public CompletableFuture<String> generateExpiryReport() {
        List<Medicine> expiring = medicineDAO.getExpiringMedicines(60); // Check next 2 months

        if (expiring.isEmpty()) {
            return CompletableFuture.completedFuture("✅ No medicines are expiring in the next 60 days.");
        }

        StringBuilder dataSummary = new StringBuilder();
        expiring.forEach(m -> dataSummary.append("- ").append(m.getName())
                .append(" (Generic: ").append(m.getGenericName()).append(")")
                .append(" Expiry: ").append(m.getExpiry()).append("\n"));

        String prompt = "The following medicines are expiring within 60 days. \n" +
                "1. Suggest a strategy to clear stock (e.g. discount percentages).\n" +
                "2. Provide chemical-specific disposal instructions for safety if they expire.\n\n" +
                "Expiring List:\n" + dataSummary.toString();

        return aiOrchestrator.processQuery(prompt, true, true); // Precision for disposal (chemical safety), enabled
                                                                // search for disposal methods
    }
}
