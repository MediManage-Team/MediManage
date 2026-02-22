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
        this.aiOrchestrator = AIServiceProvider.get().getOrchestrator();
    }

    /**
     * A. Intelligent Substitute Finder
     * 1. Ask AI for generic composition of the brand.
     * 2. Search DB for that generic.
     */
    public CompletableFuture<String> findSubstitutes(String brandName) {
        String prompt = AIPromptCatalog.genericCompositionPrompt(brandName);

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
     * Uses LOCAL AI with RAG — injects real DB sales data as context.
     */
    public CompletableFuture<String> generateRestockReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);

        Map<String, Integer> sales = billDAO.getItemizedSales(start, end);

        if (sales.isEmpty()) {
            return CompletableFuture
                    .completedFuture("No sales data available for the last 30 days to generate a forecast.");
        }

        // Build business context from real DB data
        StringBuilder context = new StringBuilder();
        context.append("## Sales Data (Last 30 Days: ").append(start).append(" to ").append(end).append(")\n");
        sales.entrySet().stream().limit(50).forEach(entry -> context.append("- ").append(entry.getKey())
                .append(": ").append(entry.getValue()).append(" units\n"));

        String prompt = AIPromptCatalog.inventoryTrendAnalysisPrompt();

        // Explicit: Local AI with RAG context (falls back to Cloud if local
        // unavailable)
        return aiOrchestrator.localQueryWithContext(prompt, context.toString());
    }

    /**
     * C. Expiry & Disposal Management
     * Uses COMBINED query: Local AI analyzes stock → Cloud AI adds medical disposal
     * advice.
     */
    public CompletableFuture<String> generateExpiryReport() {
        List<Medicine> expiring = medicineDAO.getExpiringMedicines(60); // Check next 2 months

        if (expiring.isEmpty()) {
            return CompletableFuture.completedFuture("✅ No medicines are expiring in the next 60 days.");
        }

        // Build business context from real DB data
        StringBuilder context = new StringBuilder();
        context.append("## Expiring Medicines (Next 60 Days)\n");
        expiring.forEach(m -> context.append("- ").append(m.getName())
                .append(" (Generic: ").append(m.getGenericName()).append(")")
                .append(" | Expiry: ").append(m.getExpiry())
                .append(" | Stock: ").append(m.getStock())
                .append(" | Price: ₹").append(m.getPrice()).append("\n"));

        String prompt = AIPromptCatalog.expiryStrategyPrompt();

        // Combined: Local analyzes stock data → Cloud adds medical disposal precision
        return aiOrchestrator.combinedQuery(prompt, context.toString());
    }

    /**
     * D. Free-form Business Question with RAG
     * Ask any business question — local AI reasons over full inventory snapshot.
     */
    public CompletableFuture<String> askBusinessQuestion(String question) {
        // Build comprehensive business context
        StringBuilder context = new StringBuilder();

        // Current inventory snapshot
        List<Medicine> allMeds = medicineDAO.getAllMedicines();
        context.append("## Current Inventory (").append(allMeds.size()).append(" items)\n");
        int lowStock = 0, outOfStock = 0;
        double totalValue = 0;
        for (Medicine m : allMeds) {
            if (m.getStock() == 0)
                outOfStock++;
            else if (m.getStock() < 10)
                lowStock++;
            totalValue += m.getPrice() * m.getStock();
        }
        context.append("Total items: ").append(allMeds.size()).append("\n");
        context.append("Out of stock: ").append(outOfStock).append("\n");
        context.append("Low stock (<10): ").append(lowStock).append("\n");
        context.append("Total inventory value: ₹").append(String.format("%.2f", totalValue)).append("\n\n");

        // Recent sales
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        Map<String, Integer> sales = billDAO.getItemizedSales(start, end);
        context.append("## Recent Sales (Last 30 Days)\n");
        sales.entrySet().stream().limit(30).forEach(entry -> context.append("- ").append(entry.getKey()).append(": ")
                .append(entry.getValue()).append(" units\n"));

        // Expiring items
        List<Medicine> expiring = medicineDAO.getExpiringMedicines(60);
        if (!expiring.isEmpty()) {
            context.append("\n## Expiring Soon (60 Days)\n");
            expiring.stream().limit(20)
                    .forEach(m -> context.append("- ").append(m.getName()).append(" (Expiry: ").append(m.getExpiry())
                            .append(", Stock: ").append(m.getStock()).append(")\n"));
        }

        return aiOrchestrator.localQueryWithContext(question, context.toString());
    }
}
