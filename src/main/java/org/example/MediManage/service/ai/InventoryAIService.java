package org.example.MediManage.service.ai;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
     * Uses AI when available, falls back to local DB search.
     */
    public CompletableFuture<String> findSubstitutes(String brandName) {
        org.json.JSONObject data = new org.json.JSONObject().put("brand_name", brandName);

        return aiOrchestrator.processOrchestration("generic_composition", data, "cloud_only", false)
                .thenApply(genericResponse -> {
                    String keyword = genericResponse.split("\n")[0].replace(".", "").trim();
                    List<Medicine> substitutes = medicineDAO.searchByGeneric(keyword);
                    if (substitutes.isEmpty()) {
                        substitutes = medicineDAO.searchByGeneric(brandName);
                    }
                    return formatSubstituteResult(brandName, keyword, substitutes);
                })
                .exceptionally(ex -> {
                    // ── OFFLINE FALLBACK: Smart multi-strategy search ──
                    java.util.Set<Integer> seen = new java.util.HashSet<>();
                    List<Medicine> combined = new java.util.ArrayList<>();

                    // Strategy 1: Search by full name (generic + name)
                    for (Medicine m : medicineDAO.searchByGeneric(brandName)) {
                        if (seen.add(m.getId())) combined.add(m);
                    }
                    // Strategy 2: Paginated search (also searches company)
                    for (Medicine m : medicineDAO.searchMedicines(brandName, 0, 50)) {
                        if (seen.add(m.getId())) combined.add(m);
                    }
                    // Strategy 3: Try each word individually for multi-word queries
                    if (combined.isEmpty() && brandName.contains(" ")) {
                        for (String word : brandName.split("\\s+")) {
                            if (word.length() >= 3) {
                                for (Medicine m : medicineDAO.searchByGeneric(word)) {
                                    if (seen.add(m.getId())) combined.add(m);
                                }
                            }
                        }
                    }
                    return formatSubstituteResult(brandName, brandName, combined);
                });
    }

    private String formatSubstituteResult(String brand, String keyword, List<Medicine> substitutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Search: \"").append(brand).append("\"");
        if (!brand.equalsIgnoreCase(keyword)) {
            sb.append(" → Generic: ").append(keyword);
        }
        sb.append("\n\n");

        if (substitutes.isEmpty()) {
            sb.append("❌ No matching medicines found.\n");
            sb.append("• Check the spelling and try again\n");
            sb.append("• Try searching by generic name (e.g. \"paracetamol\")\n");
        } else {
            sb.append("✅ ").append(substitutes.size()).append(" result(s):\n\n");
            for (Medicine m : substitutes) {
                sb.append("▸ ").append(m.getName());
                if (m.getCompany() != null && !m.getCompany().isBlank()) {
                    sb.append("  [").append(m.getCompany()).append("]");
                }
                sb.append("\n");
                sb.append("   Stock: ").append(m.getStock()).append("  |  ₹").append(String.format("%.2f", m.getPrice()));
                if (m.getGenericName() != null && !m.getGenericName().isBlank()) {
                    sb.append("  |  ").append(m.getGenericName());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * B. Strategic Forecasting & Seasonal Buying
     * Uses AI with RAG, falls back to local data analysis.
     */
    public CompletableFuture<String> generateRestockReport() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        Map<String, Integer> sales = billDAO.getItemizedSales(start, end);

        if (sales.isEmpty()) {
            return CompletableFuture.completedFuture("No sales data available for the last 30 days.");
        }

        StringBuilder context = new StringBuilder();
        context.append("## Sales Data (Last 30 Days: ").append(start).append(" to ").append(end).append(")\n");
        sales.entrySet().stream().limit(50).forEach(entry -> context.append("- ").append(entry.getKey())
                .append(": ").append(entry.getValue()).append(" units\n"));

        org.json.JSONObject data = new org.json.JSONObject().put("sales_data", context.toString());

        return aiOrchestrator.processOrchestration("inventory_trend", data, "local_fallback", false)
                .exceptionally(ex -> generateLocalRestockReport(sales));
    }

    private String generateLocalRestockReport(Map<String, Integer> sales) {
        StringBuilder sb = new StringBuilder();
        sb.append("Restock Analysis (30-Day Sales)\n\n");

        List<Map.Entry<String, Integer>> sorted = sales.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        sb.append("🔥 Top Sellers (High Priority Restock)\n");
        sorted.stream().limit(10).forEach(e ->
                sb.append("• ").append(e.getKey()).append(" — ").append(e.getValue()).append(" units sold\n"));

        sb.append("\n⚠️ Low Stock Warnings\n");
        List<MedicineDAO.ReorderNeededRow> lowStock = medicineDAO.getReorderNeeded();
        if (lowStock.isEmpty()) {
            sb.append("✅ All items above reorder threshold.\n");
        } else {
            lowStock.stream().limit(15).forEach(m ->
                    sb.append("• ").append(m.medicineName())
                            .append(" — Stock: ").append(m.currentStock())
                            .append(" (Reorder at: ").append(m.reorderThreshold()).append(")\n"));
        }

        sb.append("\n📊 Summary\n");
        sb.append("• Unique medicines sold: ").append(sales.size()).append("\n");
        int totalUnits = sales.values().stream().mapToInt(Integer::intValue).sum();
        sb.append("• Total units sold: ").append(totalUnits).append("\n");
        sb.append("• Items needing reorder: ").append(lowStock.size()).append("\n");

        return sb.toString();
    }

    /**
     * C. Expiry & Disposal Management
     * Uses AI for strategy, falls back to local categorized report.
     */
    public CompletableFuture<String> generateExpiryReport() {
        List<Medicine> expiring = medicineDAO.getExpiringMedicines(60);

        if (expiring.isEmpty()) {
            return CompletableFuture.completedFuture("✅ No medicines are expiring in the next 60 days.");
        }

        StringBuilder context = new StringBuilder();
        context.append("## Expiring Medicines (Next 60 Days)\n");
        expiring.forEach(m -> context.append("- ").append(m.getName())
                .append(" (Generic: ").append(m.getGenericName()).append(")")
                .append(" | Expiry: ").append(m.getExpiry())
                .append(" | Stock: ").append(m.getStock())
                .append(" | Price: ₹").append(m.getPrice()).append("\n"));

        org.json.JSONObject data = new org.json.JSONObject()
            .put("prompt", "Analyze expiring medicines and suggest medical disposal strategies")
            .put("business_context", context.toString());

        return aiOrchestrator.processOrchestration("combined_analysis", data, "cloud_only", false)
                .exceptionally(ex -> generateLocalExpiryReport(expiring));
    }

    private String generateLocalExpiryReport(List<Medicine> expiring) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expiry Risk Analysis\n\n");

        LocalDate today = LocalDate.now();
        List<Medicine> expired = new java.util.ArrayList<>();
        List<Medicine> within30 = new java.util.ArrayList<>();
        List<Medicine> within60 = new java.util.ArrayList<>();

        for (Medicine m : expiring) {
            try {
                String expStr = m.getExpiry();
                if (expStr == null || expStr.isBlank()) continue;
                if (expStr.length() > 10) expStr = expStr.substring(0, 10);
                LocalDate expDate = LocalDate.parse(expStr);
                long days = ChronoUnit.DAYS.between(today, expDate);
                if (days < 0) expired.add(m);
                else if (days <= 30) within30.add(m);
                else within60.add(m);
            } catch (Exception ignored) {}
        }

        double atRiskValue = expiring.stream().mapToDouble(m -> m.getPrice() * m.getStock()).sum();

        if (!expired.isEmpty()) {
            sb.append("🔴 EXPIRED (").append(expired.size()).append(" items)\n");
            expired.forEach(m -> sb.append("• ").append(m.getName())
                    .append(" | Expired: ").append(m.getExpiry())
                    .append(" | Stock: ").append(m.getStock()).append("\n"));
            sb.append("\n");
        }

        if (!within30.isEmpty()) {
            sb.append("🟠 Expiring in 0-30 Days (").append(within30.size()).append(" items)\n");
            within30.forEach(m -> sb.append("• ").append(m.getName())
                    .append(" | Expiry: ").append(m.getExpiry())
                    .append(" | Stock: ").append(m.getStock())
                    .append(" | Value: ₹").append(String.format("%.0f", m.getPrice() * m.getStock())).append("\n"));
            sb.append("\n");
        }

        if (!within60.isEmpty()) {
            sb.append("🟡 Expiring in 31-60 Days (").append(within60.size()).append(" items)\n");
            within60.forEach(m -> sb.append("• ").append(m.getName())
                    .append(" | Expiry: ").append(m.getExpiry())
                    .append(" | Stock: ").append(m.getStock()).append("\n"));
            sb.append("\n");
        }

        sb.append("💰 Total Inventory at Risk: ₹").append(String.format("%.2f", atRiskValue)).append("\n\n");
        sb.append("Recommendations:\n");
        sb.append("• Run clearance sales on items expiring within 30 days\n");
        sb.append("• Contact suppliers for return/exchange on high-value items\n");
        sb.append("• Dispose of expired items per regulatory guidelines\n");

        return sb.toString();
    }

    /**
     * D. Profit Margin Analyzer — FULLY OFFLINE, no AI needed.
     */
    public CompletableFuture<String> generateProfitAnalysis() {
        return CompletableFuture.supplyAsync(() -> {
            List<Medicine> all = medicineDAO.getAllMedicines();
            if (all.isEmpty()) return "No medicine data available for analysis.";

            List<Medicine> withMargin = all.stream()
                    .filter(m -> m.getPurchasePrice() > 0 && m.getPrice() > 0)
                    .collect(Collectors.toList());

            if (withMargin.isEmpty()) return "No purchase price data available. Add purchase prices to analyze margins.";

            StringBuilder sb = new StringBuilder();
            sb.append("💎 Profit Margin Analysis\n\n");

            withMargin.sort(Comparator.comparingDouble(Medicine::getProfitMarginPercent).reversed());

            sb.append("🟢 Top 5 High-Margin Medicines:\n");
            withMargin.stream().limit(5).forEach(m -> sb.append("• ").append(m.getName())
                    .append(" | Margin: ").append(String.format("%.1f%%", m.getProfitMarginPercent()))
                    .append(" | Sell: ₹").append(String.format("%.2f", m.getPrice()))
                    .append(" | Buy: ₹").append(String.format("%.2f", m.getPurchasePrice()))
                    .append("\n"));

            sb.append("\n🔴 Bottom 5 Low-Margin Medicines:\n");
            List<Medicine> bottom = withMargin.stream()
                    .sorted(Comparator.comparingDouble(Medicine::getProfitMarginPercent))
                    .limit(5)
                    .collect(Collectors.toList());
            bottom.forEach(m -> sb.append("• ").append(m.getName())
                    .append(" | Margin: ").append(String.format("%.1f%%", m.getProfitMarginPercent()))
                    .append(" | Sell: ₹").append(String.format("%.2f", m.getPrice()))
                    .append(" | Buy: ₹").append(String.format("%.2f", m.getPurchasePrice()))
                    .append("\n"));

            double avgMargin = withMargin.stream().mapToDouble(Medicine::getProfitMarginPercent).average().orElse(0);
            double totalStockValue = all.stream().mapToDouble(m -> m.getPrice() * m.getStock()).sum();
            long negativeMarginCount = withMargin.stream().filter(m -> m.getProfitMarginPercent() < 0).count();

            sb.append("\n📊 Summary\n");
            sb.append("• Medicines with pricing data: ").append(withMargin.size()).append("\n");
            sb.append("• Average profit margin: ").append(String.format("%.1f%%", avgMargin)).append("\n");
            sb.append("• Total stock retail value: ₹").append(String.format("%.2f", totalStockValue)).append("\n");
            if (negativeMarginCount > 0) {
                sb.append("• ⚠️ Medicines sold below cost: ").append(negativeMarginCount).append("\n");
            }

            return sb.toString();
        });
    }

    /**
     * E. Free-form Business Question with RAG
     */
    public CompletableFuture<String> askBusinessQuestion(String question) {
        StringBuilder context = new StringBuilder();
        List<Medicine> allMeds = medicineDAO.getAllMedicines();
        context.append("## Current Inventory (").append(allMeds.size()).append(" items)\n");
        int lowStock = 0, outOfStock = 0;
        double totalValue = 0;
        for (Medicine m : allMeds) {
            if (m.getStock() == 0) outOfStock++;
            else if (m.getStock() < 10) lowStock++;
            totalValue += m.getPrice() * m.getStock();
        }
        context.append("Total items: ").append(allMeds.size()).append("\n");
        context.append("Out of stock: ").append(outOfStock).append("\n");
        context.append("Low stock (<10): ").append(lowStock).append("\n");
        context.append("Total inventory value: ₹").append(String.format("%.2f", totalValue)).append("\n\n");

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        Map<String, Integer> sales = billDAO.getItemizedSales(start, end);
        context.append("## Recent Sales (Last 30 Days)\n");
        sales.entrySet().stream().limit(30).forEach(entry -> context.append("- ").append(entry.getKey()).append(": ")
                .append(entry.getValue()).append(" units\n"));

        List<Medicine> expiring = medicineDAO.getExpiringMedicines(60);
        if (!expiring.isEmpty()) {
            context.append("\n## Expiring Soon (60 Days)\n");
            expiring.stream().limit(20)
                    .forEach(m -> context.append("- ").append(m.getName()).append(" (Expiry: ").append(m.getExpiry())
                            .append(", Stock: ").append(m.getStock()).append(")\n"));
        }

        org.json.JSONObject data = new org.json.JSONObject()
            .put("prompt", question)
            .put("business_context", context.toString());

        return aiOrchestrator.processOrchestration("raw_chat", data, "local_fallback", false);
    }
}
