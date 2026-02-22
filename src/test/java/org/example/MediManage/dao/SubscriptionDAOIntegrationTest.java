package org.example.MediManage.dao;

import org.example.MediManage.DatabaseUtil;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionDAOIntegrationTest {
    private static final String DB_PATH_PROPERTY = "medimanage.db.path";
    private static Path testDbPath;

    @BeforeAll
    static void setupDb() throws Exception {
        Path tempDir = Files.createTempDirectory("medimanage-subscription-dao-tests-");
        testDbPath = tempDir.resolve("medimanage-subscription-dao.db");
        System.setProperty(DB_PATH_PROPERTY, testDbPath.toString());
        DatabaseUtil.initDB();
    }

    @AfterAll
    static void cleanupDb() {
        System.clearProperty(DB_PATH_PROPERTY);
        if (testDbPath == null) {
            return;
        }
        String baseName = testDbPath.getFileName().toString();
        tryDelete(testDbPath.resolveSibling(baseName + "-shm"));
        tryDelete(testDbPath.resolveSibling(baseName + "-wal"));
        tryDelete(testDbPath);
        Path parent = testDbPath.getParent();
        if (parent != null) {
            tryDelete(parent);
        }
    }

    @Test
    void createAndLifecycleEnrollmentFlow() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_admin_" + System.nanoTime(), "ADMIN");
        int customerId = insertCustomer("cust_" + System.nanoTime(), "9999999999");

        SubscriptionPlan plan = new SubscriptionPlan(
                0,
                "PLAN-" + System.nanoTime(),
                "Gold Care",
                "Gold plan for discounts",
                999.0,
                30,
                7,
                10.0,
                20.0,
                5.0,
                SubscriptionPlanStatus.DRAFT,
                false,
                true,
                null,
                null,
                null,
                null);

        int planId = dao.createPlan(plan, actorId);
        assertTrue(planId > 0);

        dao.updatePlanStatus(planId, SubscriptionPlanStatus.ACTIVE, actorId);
        Optional<SubscriptionPlan> storedPlan = dao.findPlanById(planId);
        assertTrue(storedPlan.isPresent());
        assertEquals(SubscriptionPlanStatus.ACTIVE, storedPlan.get().status());

        CustomerSubscription enrollment = new CustomerSubscription(
                0,
                customerId,
                planId,
                SubscriptionEnrollmentStatus.ACTIVE,
                "2026-02-01 00:00:00",
                "2026-03-03 00:00:00",
                "2026-03-10 00:00:00",
                "POS",
                actorId,
                actorId,
                "APR-101",
                null,
                null,
                null,
                null);

        int enrollmentId = dao.createEnrollment(enrollment, actorId);
        assertTrue(enrollmentId > 0);

        Optional<CustomerSubscription> storedEnrollment = dao.findEnrollmentById(enrollmentId);
        assertTrue(storedEnrollment.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.ACTIVE, storedEnrollment.get().status());

        dao.updateEnrollmentStatus(
                enrollmentId,
                SubscriptionEnrollmentStatus.FROZEN,
                "FREEZE",
                "Customer requested hold",
                actorId,
                actorId,
                "APR-102");

        Optional<CustomerSubscription> frozenEnrollment = dao.findEnrollmentById(enrollmentId);
        assertTrue(frozenEnrollment.isPresent());
        assertEquals(SubscriptionEnrollmentStatus.FROZEN, frozenEnrollment.get().status());
        assertEquals("Customer requested hold", frozenEnrollment.get().frozenReason());

        assertFalse(dao.getCustomerEnrollments(customerId).isEmpty());
        assertTrue(dao.findApplicableSubscription(customerId).isEmpty());
    }

    @Test
    void upsertAndDeletePlanMedicineRuleFlow() throws Exception {
        SubscriptionDAO dao = new SubscriptionDAO();
        int actorId = insertUser("sub_rule_admin_" + System.nanoTime(), "ADMIN");
        int planId = dao.createPlan(new SubscriptionPlan(
                0,
                "RULE-" + System.nanoTime(),
                "Rule Plan",
                "Plan with medicine rules",
                899.0,
                30,
                5,
                8.0,
                15.0,
                5.0,
                SubscriptionPlanStatus.ACTIVE,
                false,
                true,
                null,
                null,
                null,
                null), actorId);

        int medicineId = insertMedicine("RuleMed-" + System.nanoTime(), 100.0);

        dao.upsertPlanMedicineRule(planId, medicineId, true, 11.0, 25.0, 4.0, true);
        dao.upsertPlanMedicineRule(planId, medicineId, true, 13.5, 30.0, 5.0, true);

        java.util.List<SubscriptionPlanMedicineRule> rules = dao.getPlanMedicineRules(planId);
        assertEquals(1, rules.size());
        SubscriptionPlanMedicineRule stored = rules.get(0);
        assertEquals(13.5, stored.discountPercent(), 0.0001);
        assertEquals(30.0, stored.maxDiscountAmount(), 0.0001);

        dao.deletePlanMedicineRule(stored.ruleId());
        assertTrue(dao.getPlanMedicineRules(planId).isEmpty());
    }

    private static int insertUser(String username, String role) throws Exception {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, "password");
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test user");
    }

    private static int insertCustomer(String name, String phone) throws Exception {
        String sql = "INSERT INTO customers (name, phone) VALUES (?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Failed to insert test customer");
    }

    private static int insertMedicine(String name, double price) throws Exception {
        String insertMedicineSql = "INSERT INTO medicines (name, company, price) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(insertMedicineSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, "Test Co");
            ps.setDouble(3, price);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int medicineId = rs.getInt(1);
                    try (PreparedStatement stockPs = conn.prepareStatement(
                            "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)")) {
                        stockPs.setInt(1, medicineId);
                        stockPs.setInt(2, 100);
                        stockPs.executeUpdate();
                    }
                    return medicineId;
                }
            }
        }
        throw new IllegalStateException("Failed to insert test medicine");
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }
}
