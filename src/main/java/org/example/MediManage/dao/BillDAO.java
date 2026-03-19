package org.example.MediManage.dao;

import org.example.MediManage.util.DatabaseUtil;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.BillHistoryRecord;
import org.example.MediManage.model.PaymentSplit;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BillDAO {
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 500;
    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();

    public int generateInvoice(double totalAmount, List<BillItem> items, Integer customerId, Integer userId,
            List<PaymentSplit> paymentSplits, String paymentMode, int loyaltyPointsToRedeem, int loyaltyPointsToAward)
            throws SQLException {
        Connection conn = null;
        int billId = -1;
        try {
            conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            String checkUser = "SELECT 1 FROM users WHERE user_id = ?";
            try (PreparedStatement psCheck = conn.prepareStatement(checkUser)) {
                psCheck.setInt(1, userId);
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (!rsCheck.next()) {
                        System.err.println("⚠️ Warning: User ID " + userId + " not found. Fallback to Admin (1).");
                        userId = 1;
                    }
                }
            }

            String billSql = "INSERT INTO bills (" +
                    "total_amount, bill_date, customer_id, user_id, payment_mode" +
                    ") VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement psBill = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                psBill.setDouble(1, totalAmount);
                String now = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                psBill.setString(2, now);
                psBill.setObject(3, customerId);
                psBill.setObject(4, userId);
                psBill.setString(5, paymentMode != null ? paymentMode : "CASH");
                psBill.executeUpdate();
                ResultSet rs = psBill.getGeneratedKeys();
                if (rs.next()) {
                    billId = rs.getInt(1);
                }
            }

            String itemSql = "INSERT INTO bill_items (" +
                    "bill_id, medicine_id, quantity, price, total, cost_of_goods" +
                    ") VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement psItem = conn.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS)) {

                for (BillItem item : items) {
                    List<InventoryBatchDAO.BatchAllocation> allocations = inventoryBatchDAO
                            .allocateFefo(conn, item.getMedicineId(), item.getQty());
                    double costOfGoods = allocations.stream()
                            .mapToDouble(allocation -> allocation.quantity() * allocation.unitCost())
                            .sum();

                    psItem.setInt(1, billId);
                    psItem.setInt(2, item.getMedicineId());
                    psItem.setInt(3, item.getQty());
                    psItem.setDouble(4, item.getPrice());
                    psItem.setDouble(5, item.getTotal());
                    psItem.setDouble(6, round2(costOfGoods));
                    psItem.executeUpdate();

                    int billItemId;
                    try (ResultSet itemKeys = psItem.getGeneratedKeys()) {
                        if (!itemKeys.next()) {
                            throw new SQLException("Failed to retrieve bill item ID for medicine_id=" + item.getMedicineId());
                        }
                        billItemId = itemKeys.getInt(1);
                    }
                    saveBatchAllocations(conn, billId, billItemId, item.getMedicineId(), allocations);
                }
            }

            savePaymentSplits(conn, billId, paymentSplits);

            if (customerId != null) {
                double creditAmount = resolveCreditAmount(totalAmount, paymentMode, paymentSplits);
                if (creditAmount > 0.0) {
                    String updateBalanceSql = "UPDATE customers SET current_balance = current_balance + ? WHERE customer_id = ?";
                    try (PreparedStatement psUpdate = conn.prepareStatement(updateBalanceSql)) {
                        psUpdate.setDouble(1, creditAmount);
                        psUpdate.setInt(2, customerId);
                        psUpdate.executeUpdate();
                    }
                }

                if (loyaltyPointsToRedeem > 0) {
                    String redeemSql = "UPDATE customers " +
                            "SET loyalty_points = COALESCE(loyalty_points, 0) - ? " +
                            "WHERE customer_id = ? AND COALESCE(loyalty_points, 0) >= ?";
                    try (PreparedStatement psRedeem = conn.prepareStatement(redeemSql)) {
                        psRedeem.setInt(1, loyaltyPointsToRedeem);
                        psRedeem.setInt(2, customerId);
                        psRedeem.setInt(3, loyaltyPointsToRedeem);
                        if (psRedeem.executeUpdate() != 1) {
                            throw new SQLException("Failed to redeem loyalty points for customer_id=" + customerId);
                        }
                    }
                }

                if (loyaltyPointsToAward > 0) {
                    String awardSql = "UPDATE customers SET loyalty_points = COALESCE(loyalty_points, 0) + ? WHERE customer_id = ?";
                    try (PreparedStatement psAward = conn.prepareStatement(awardSql)) {
                        psAward.setInt(1, loyaltyPointsToAward);
                        psAward.setInt(2, customerId);
                        if (psAward.executeUpdate() != 1) {
                            throw new SQLException("Failed to award loyalty points for customer_id=" + customerId);
                        }
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null)
                conn.rollback();
            String debugMsg = "Invoice Generation Failed.\n" +
                    "Debug Info:\n" +
                    "Customer ID: " + customerId + "\n" +
                    "User ID: " + userId + "\n" +
                    "Items: " + items.size() + "\n" +
                    "First Item Medicine ID: " + (items.isEmpty() ? "N/A" : items.get(0).getMedicineId());
            System.err.println(debugMsg);
            throw new SQLException(debugMsg + "\nOriginal Error: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
        return billId;
    }

    private void saveBatchAllocations(
            Connection conn,
            int billId,
            int billItemId,
            int medicineId,
            List<InventoryBatchDAO.BatchAllocation> allocations) throws SQLException {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO bill_item_batch_allocations (
                    bill_id,
                    bill_item_id,
                    medicine_id,
                    batch_id,
                    batch_number,
                    expiry_date,
                    quantity,
                    unit_cost,
                    selling_price
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (InventoryBatchDAO.BatchAllocation allocation : allocations) {
                ps.setInt(1, billId);
                ps.setInt(2, billItemId);
                ps.setInt(3, medicineId);
                if (allocation.batchId() != null && allocation.batchId() > 0) {
                    ps.setInt(4, allocation.batchId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                ps.setString(5, allocation.batchNumber());
                ps.setString(6, allocation.expiryDate());
                ps.setInt(7, allocation.quantity());
                ps.setDouble(8, allocation.unitCost());
                ps.setDouble(9, allocation.sellingPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void savePaymentSplits(Connection conn, int billId, List<PaymentSplit> paymentSplits) throws SQLException {
        if (paymentSplits == null || paymentSplits.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PaymentSplit split : paymentSplits) {
                if (split == null) {
                    continue;
                }
                ps.setInt(1, billId);
                ps.setString(2, normalizePaymentMethod(split.getPaymentMethod()));
                ps.setDouble(3, split.getAmount());
                ps.setString(4, split.getReferenceNumber());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private double resolveCreditAmount(double totalAmount, String paymentMode, List<PaymentSplit> paymentSplits) {
        if (paymentSplits != null && !paymentSplits.isEmpty()) {
            return paymentSplits.stream()
                    .filter(split -> split != null && "Credit".equalsIgnoreCase(split.getPaymentMethod()))
                    .mapToDouble(PaymentSplit::getAmount)
                    .sum();
        }

        return "Credit".equalsIgnoreCase(paymentMode) ? totalAmount : 0.0;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        return paymentMethod == null ? "OTHER" : paymentMethod.trim().toUpperCase();
    }

    public double getDailySales() {
        LocalDate today = LocalDate.now();
        String start = today.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = today.plusDays(1).atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM bills " +
                "WHERE bill_date >= ? AND bill_date < ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start);
            stmt.setString(2, end);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public int countTodaysBills() {
        LocalDate today = LocalDate.now();
        String start = today.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = today.plusDays(1).atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "SELECT COUNT(*) FROM bills WHERE bill_date >= ? AND bill_date < ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start);
            stmt.setString(2, end);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int countTodaysUniqueCustomers() {
        LocalDate today = LocalDate.now();
        String start = today.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = today.plusDays(1).atStartOfDay()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "SELECT COUNT(DISTINCT customer_id) FROM bills WHERE bill_date >= ? AND bill_date < ? AND customer_id IS NOT NULL";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, start);
            stmt.setString(2, end);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<BillHistoryRecord> getBillHistory() {
        return getBillHistoryPage(0, DEFAULT_HISTORY_LIMIT);
    }

    public List<BillHistoryRecord> getBillHistoryPage(int offset, int limit) {
        List<BillHistoryRecord> history = new ArrayList<>();
        int safeOffset = Math.max(0, offset);
        int safeLimit = normalizeHistoryLimit(limit);
        String sql = "SELECT b.bill_id, b.bill_date, b.total_amount, c.name, c.phone, u.username " +
                "FROM bills b " +
                "LEFT JOIN customers c ON b.customer_id = c.customer_id " +
                "LEFT JOIN users u ON b.user_id = u.user_id " +
                "ORDER BY b.bill_date DESC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, safeLimit);
            stmt.setInt(2, safeOffset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new BillHistoryRecord(
                            rs.getInt("bill_id"),
                            rs.getString("bill_date"),
                            rs.getDouble("total_amount"),
                            rs.getString("name"),
                            rs.getString("phone"),
                            rs.getString("username")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    public int countBillHistory() {
        String sql = "SELECT COUNT(*) FROM bills";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // New Method for Reports
    public Map<String, Double> getSalesBetweenDates(LocalDate start, LocalDate end) {
        Map<String, Double> sales = new LinkedHashMap<>();
        // Filter via indexed bill_date range, then aggregate by day.
        String sql = "SELECT DATE(bill_date) as day, SUM(total_amount) as total " +
                "FROM bills WHERE bill_date >= ? AND bill_date < ? " +
                "GROUP BY day ORDER BY day ASC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.put(rs.getString("day"), rs.getDouble("total"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sales;
    }

    public List<BillItem> getBillItemsExtended(int billId) {
        List<BillItem> items = new java.util.ArrayList<>();
        String sql = "SELECT bi.medicine_id, m.name, m.expiry_date, bi.quantity, bi.price, bi.total FROM bill_items bi LEFT JOIN medicines m ON bi.medicine_id = m.medicine_id WHERE bi.bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int mid = rs.getInt("medicine_id");
                    String name = rs.getString("name");
                    if (name == null)
                        name = "Deleted Item (" + mid + ")";
                    String expiry = rs.getString("expiry_date");
                    int qty = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    double total = rs.getDouble("total");
                    double gst = total - (price * qty);
                    items.add(new BillItem(mid, name, expiry, qty, price, gst));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    // For Business Intelligence: Sales by Item (Medicine)
    public Map<String, Integer> getItemizedSales(LocalDate start, LocalDate end) {
        Map<String, Integer> sales = new LinkedHashMap<>();
        String sql = "SELECT m.name, SUM(bi.quantity) as total_qty " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ? " +
                "GROUP BY m.name " +
                "ORDER BY total_qty DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.put(rs.getString("name"), rs.getInt("total_qty"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sales;
    }

    // For Business Intelligence: Sales by Revenue (Medicine)
    public Map<String, Double> getItemizedRevenue(LocalDate start, LocalDate end) {
        Map<String, Double> revenue = new LinkedHashMap<>();
        String sql = "SELECT m.name, SUM(bi.total) as total_rev " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ? " +
                "GROUP BY m.name " +
                "ORDER BY total_rev DESC";

        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    revenue.put(rs.getString("name"), rs.getDouble("total_rev"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return revenue;
    }

    // For Business Intelligence: Line Chart (Gross Profit by Day)
    public Map<String, Double> getProfitBetweenDates(LocalDate start, LocalDate end) {
        Map<String, Double> profit = new LinkedHashMap<>();
        String sqlWithCogs = "SELECT DATE(b.bill_date) as day, " +
                "SUM(bi.total - COALESCE(bi.cost_of_goods, (m.purchase_price * bi.quantity))) as daily_profit " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ? " +
                "GROUP BY day ORDER BY day ASC";
        String sqlLegacy = "SELECT DATE(b.bill_date) as day, " +
                "SUM(bi.total - (m.purchase_price * bi.quantity)) as daily_profit " +
                "FROM bills b " +
                "JOIN bill_items bi ON bi.bill_id = b.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ? " +
                "GROUP BY day ORDER BY day ASC";

        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = sqlWithCogs;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, start + " 00:00:00");
                ps.setString(2, end.plusDays(1) + " 00:00:00");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        profit.put(rs.getString("day"), rs.getDouble("daily_profit"));
                    }
                }
            } catch (SQLException e) {
                if (!isMissingCostOfGoodsColumn(e)) {
                    throw e;
                }
                try (PreparedStatement legacyPs = conn.prepareStatement(sqlLegacy)) {
                    legacyPs.setString(1, start + " 00:00:00");
                    legacyPs.setString(2, end.plusDays(1) + " 00:00:00");
                    try (ResultSet rs = legacyPs.executeQuery()) {
                        while (rs.next()) {
                            profit.put(rs.getString("day"), rs.getDouble("daily_profit"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return profit;
    }

    // For Business Intelligence: Payment Methods Donut Chart
    public Map<String, Integer> getPaymentMethodDistribution(LocalDate start, LocalDate end) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        String sql = "SELECT payment_mode, COUNT(*) as txn_count " +
                "FROM bills " +
                "WHERE bill_date >= ? AND bill_date < ? " +
                "GROUP BY payment_mode";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, start + " 00:00:00");
            ps.setString(2, end.plusDays(1) + " 00:00:00");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String mode = rs.getString("payment_mode");
                    if (mode == null || mode.isBlank()) mode = "CASH";
                    distribution.put(mode, rs.getInt("txn_count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return distribution;
    }

    public WeeklySalesMarginSummary getWeeklySalesMarginSummary(LocalDate weekStart, LocalDate weekEnd) {
        LocalDate safeWeekStart = weekStart == null ? LocalDate.now() : weekStart;
        LocalDate safeWeekEnd = weekEnd == null ? safeWeekStart.plusDays(6) : weekEnd;
        if (safeWeekEnd.isBefore(safeWeekStart)) {
            safeWeekEnd = safeWeekStart;
        }

        String rangeStart = safeWeekStart + " 00:00:00";
        String rangeEndExclusive = safeWeekEnd.plusDays(1) + " 00:00:00";
        String expenseRangeStart = safeWeekStart.toString();
        String expenseRangeEndExclusive = safeWeekEnd.plusDays(1).toString();

        String billSql = "SELECT COUNT(DISTINCT b.bill_id) AS bill_count, " +
                "COALESCE(SUM(bi.total), 0) AS net_sales, " +
                "COALESCE(SUM(COALESCE(bi.cost_of_goods, bi.quantity * COALESCE(m.purchase_price, 0))), 0) AS cogs " +
                "FROM bills b " +
                "JOIN bill_items bi ON b.bill_id = bi.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ?";
        String billSqlLegacy = "SELECT COUNT(DISTINCT b.bill_id) AS bill_count, " +
                "COALESCE(SUM(bi.total), 0) AS net_sales, " +
                "COALESCE(SUM(bi.quantity * COALESCE(m.purchase_price, 0)), 0) AS cogs " +
                "FROM bills b " +
                "JOIN bill_items bi ON b.bill_id = bi.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ?";
        
        String expenseSql = "SELECT COALESCE(SUM(amount), 0) AS total_expenses " +
                "FROM expenses WHERE date >= ? AND date < ?";

        long billCount = 0L;
        double netSales = 0.0;
        double cogs = 0.0;
        double totalExpenses = 0.0;

        try (Connection conn = DatabaseUtil.getConnection()) {
            try (PreparedStatement billPs = conn.prepareStatement(billSql)) {
                billPs.setString(1, rangeStart);
                billPs.setString(2, rangeEndExclusive);
                try (ResultSet rs = billPs.executeQuery()) {
                    if (rs.next()) {
                        billCount = rs.getLong("bill_count");
                        netSales = rs.getDouble("net_sales");
                        cogs = rs.getDouble("cogs");
                    }
                }
            } catch (SQLException e) {
                if (!isMissingCostOfGoodsColumn(e)) {
                    throw e;
                }
                try (PreparedStatement billPs = conn.prepareStatement(billSqlLegacy)) {
                    billPs.setString(1, rangeStart);
                    billPs.setString(2, rangeEndExclusive);
                    try (ResultSet rs = billPs.executeQuery()) {
                        if (rs.next()) {
                            billCount = rs.getLong("bill_count");
                            netSales = rs.getDouble("net_sales");
                            cogs = rs.getDouble("cogs");
                        }
                    }
                }
            }

            try (PreparedStatement expensePs = conn.prepareStatement(expenseSql)) {
                expensePs.setString(1, expenseRangeStart);
                expensePs.setString(2, expenseRangeEndExclusive);
                try (ResultSet rs = expensePs.executeQuery()) {
                    if (rs.next()) {
                        totalExpenses = rs.getDouble("total_expenses");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        netSales = round2(netSales);
        totalExpenses = round2(totalExpenses);
        double grossMargin = round2(netSales - cogs);
        double grossMarginPercent = netSales <= 0.0 ? 0.0 : round2((grossMargin / netSales) * 100.0);

        return new WeeklySalesMarginSummary(
                safeWeekStart.toString(),
                safeWeekEnd.toString(),
                billCount,
                netSales,
                grossMargin,
                grossMarginPercent,
                totalExpenses);
    }

    public double getMonthlyGrossProfit() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        
        String startStr = start.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = end.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        String sql = "SELECT COALESCE(SUM(bi.total), 0) AS revenue, " +
                     "COALESCE(SUM(COALESCE(bi.cost_of_goods, bi.quantity * COALESCE(m.purchase_price, 0))), 0) AS cogs " +
                     "FROM bills b " +
                     "JOIN bill_items bi ON b.bill_id = bi.bill_id " +
                     "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                     "WHERE b.bill_date >= ? AND b.bill_date < ?";
        String sqlLegacy = "SELECT COALESCE(SUM(bi.total), 0) AS revenue, " +
                "COALESCE(SUM(bi.quantity * COALESCE(m.purchase_price, 0)), 0) AS cogs " +
                "FROM bills b " +
                "JOIN bill_items bi ON b.bill_id = bi.bill_id " +
                "JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "WHERE b.bill_date >= ? AND b.bill_date < ?";
                     
        try (Connection conn = DatabaseUtil.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, startStr);
                stmt.setString(2, endStr);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        double revenue = rs.getDouble("revenue");
                        double cogs = rs.getDouble("cogs");
                        return round2(revenue - cogs);
                    }
                }
            } catch (SQLException e) {
                if (!isMissingCostOfGoodsColumn(e)) {
                    throw e;
                }
                try (PreparedStatement stmt = conn.prepareStatement(sqlLegacy)) {
                    stmt.setString(1, startStr);
                    stmt.setString(2, endStr);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            double revenue = rs.getDouble("revenue");
                            double cogs = rs.getDouble("cogs");
                            return round2(revenue - cogs);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private boolean isMissingCostOfGoodsColumn(SQLException e) {
        return e != null
                && e.getMessage() != null
                && e.getMessage().toLowerCase().contains("cost_of_goods");
    }

    // ======================== AI CARE PROTOCOL ========================

    /**
     * Save AI-generated care protocol for a bill.
     */
    public void saveAICareProtocol(int billId, String protocol) {
        String sql = "UPDATE bills SET ai_care_protocol = ? WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, protocol);
            ps.setInt(2, billId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save AI care protocol for bill " + billId + ": " + e.getMessage());
        }
    }

    /**
     * Retrieve AI care protocol for a bill.
     */
    public String getAICareProtocol(int billId) {
        String sql = "SELECT ai_care_protocol FROM bills WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ai_care_protocol");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load AI care protocol for bill " + billId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Get payment mode for a bill.
     */
    public String getPaymentMode(int billId) {
        String sql = "SELECT payment_mode FROM bills WHERE bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("payment_mode");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get payment mode for bill " + billId + ": " + e.getMessage());
        }
        return "CASH";
    }

    /**
     * Get customer email for a bill.
     */
    public String getCustomerEmailByBillId(int billId) {
        String sql = "SELECT c.email FROM bills b JOIN customers c ON b.customer_id = c.customer_id WHERE b.bill_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("email");
                    return email != null ? email : "";
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get customer email for bill " + billId + ": " + e.getMessage());
        }
        return "";
    }

    private int normalizeHistoryLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record WeeklySalesMarginSummary(
            String weekStartDate,
            String weekEndDate,
            long billCount,
            double netSales,
            double grossMargin,
            double grossMarginPercent,
            double totalExpenses) {
    }

    // ══════════════════════════════════════════════════════════════
    // BUSINESS INTELLIGENCE QUERIES
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns daily revenue totals for the last N days.
     * Result: list of (date_string, total_revenue) pairs ordered chronologically.
     */
    public List<Map.Entry<String, Double>> getDailySalesTrend(int days) {
        List<Map.Entry<String, Double>> trend = new ArrayList<>();
        String sql = "SELECT DATE(bill_date) as sale_date, COALESCE(SUM(total_amount), 0) as revenue " +
                "FROM bills WHERE bill_date >= date('now', '-' || ? || ' days') " +
                "GROUP BY DATE(bill_date) ORDER BY sale_date ASC";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, days);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trend.add(Map.entry(rs.getString("sale_date"), rs.getDouble("revenue")));
                }
            }
        } catch (SQLException e) {
            System.err.println("getDailySalesTrend error: " + e.getMessage());
        }
        return trend;
    }

    /**
     * Returns total sales grouped by payment mode.
     * Result: map of (payment_mode → total_amount).
     */
    public Map<String, Double> getSalesByPaymentMode() {
        Map<String, Double> result = new LinkedHashMap<>();
        String sql = "SELECT COALESCE(payment_mode, 'CASH') as mode, SUM(total_amount) as total " +
                "FROM bills GROUP BY mode ORDER BY total DESC";
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("mode"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            System.err.println("getSalesByPaymentMode error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns the top N medicines by units sold.
     * Result: list of (medicine_name, units_sold) pairs ordered by units descending.
     */
    public List<Map.Entry<String, Integer>> getTopSellingMedicines(int limit) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        String sql = "SELECT m.name, SUM(bi.quantity) as units " +
                "FROM bill_items bi JOIN medicines m ON bi.medicine_id = m.medicine_id " +
                "GROUP BY bi.medicine_id ORDER BY units DESC LIMIT ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(Map.entry(rs.getString("name"), rs.getInt("units")));
                }
            }
        } catch (SQLException e) {
            System.err.println("getTopSellingMedicines error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns revenue grouped by hour of day (0-23).
     * Result: map of (hour → total_revenue).
     */
    public Map<Integer, Double> getHourlySalesDistribution() {
        Map<Integer, Double> result = new LinkedHashMap<>();
        // Initialize all hours
        for (int h = 0; h < 24; h++) result.put(h, 0.0);
        String sql = "SELECT CAST(strftime('%H', bill_date) AS INTEGER) as hour, " +
                "SUM(total_amount) as revenue FROM bills " +
                "WHERE bill_date >= date('now', '-30 days') " +
                "GROUP BY hour ORDER BY hour";
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getInt("hour"), rs.getDouble("revenue"));
            }
        } catch (SQLException e) {
            System.err.println("getHourlySalesDistribution error: " + e.getMessage());
        }
        return result;
    }
}
