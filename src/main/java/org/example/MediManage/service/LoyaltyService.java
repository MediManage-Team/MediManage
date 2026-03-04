package org.example.MediManage.service;

import org.example.MediManage.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Simple customer loyalty points service.
 * <ul>
 * <li>1 point earned per ₹100 spent</li>
 * <li>At 100+ points, customer can redeem for 5% discount on next bill</li>
 * <li>Redemption deducts 100 points</li>
 * </ul>
 */
public class LoyaltyService {

    private static final int POINTS_PER_100_RUPEES = 1;
    private static final int REDEMPTION_THRESHOLD = 100;
    private static final double REDEMPTION_DISCOUNT_PERCENT = 5.0;

    /**
     * Awards loyalty points for a bill total.
     * 
     * @return points awarded
     */
    public int awardPoints(int customerId, double billTotal) {
        if (customerId <= 0 || billTotal <= 0)
            return 0;
        int points = (int) (billTotal / 100.0) * POINTS_PER_100_RUPEES;
        if (points <= 0)
            return 0;

        String sql = "UPDATE customers SET loyalty_points = COALESCE(loyalty_points, 0) + ? WHERE customer_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, points);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return points;
    }

    /**
     * Returns the current loyalty points for a customer.
     */
    public int getPoints(int customerId) {
        String sql = "SELECT COALESCE(loyalty_points, 0) FROM customers WHERE customer_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Checks if the customer can redeem points for a discount.
     */
    public boolean canRedeem(int customerId) {
        return getPoints(customerId) >= REDEMPTION_THRESHOLD;
    }

    /**
     * Redeems points: deducts 100 points and returns the discount percent to apply.
     * Returns 0.0 if the customer doesn't have enough points.
     */
    public double redeemPoints(int customerId) {
        if (!canRedeem(customerId))
            return 0.0;

        String sql = "UPDATE customers SET loyalty_points = loyalty_points - ? WHERE customer_id = ? AND loyalty_points >= ?";
        try (Connection conn = DatabaseUtil.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, REDEMPTION_THRESHOLD);
            ps.setInt(2, customerId);
            ps.setInt(3, REDEMPTION_THRESHOLD);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                return REDEMPTION_DISCOUNT_PERCENT;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    /**
     * Returns the redemption threshold.
     */
    public int getRedemptionThreshold() {
        return REDEMPTION_THRESHOLD;
    }

    /**
     * Returns the discount percentage when points are redeemed.
     */
    public double getRedemptionDiscountPercent() {
        return REDEMPTION_DISCOUNT_PERCENT;
    }
}
