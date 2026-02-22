package org.example.MediManage.security;

import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacPolicyTest {

    @BeforeEach
    void clearSessionBefore() {
        UserSession.getInstance().logout();
    }

    @AfterEach
    void clearSessionAfter() {
        UserSession.getInstance().logout();
    }

    @Test
    void policyAllowsAdminToManageUsers() {
        assertTrue(RbacPolicy.canAccess(UserRole.ADMIN, Permission.MANAGE_USERS));
        assertFalse(RbacPolicy.canAccess(UserRole.MANAGER, Permission.MANAGE_USERS));
    }

    @Test
    void policyAllowsManagerToManageMedicines() {
        assertTrue(RbacPolicy.canAccess(UserRole.MANAGER, Permission.MANAGE_MEDICINES));
        assertFalse(RbacPolicy.canAccess(UserRole.CASHIER, Permission.MANAGE_MEDICINES));
    }

    @Test
    void policyAllowsCashierToManageSubscriptionEnrollments() {
        assertTrue(RbacPolicy.canAccess(UserRole.CASHIER, Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS));
        assertFalse(RbacPolicy.canAccess(UserRole.STAFF, Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS));
    }

    @Test
    void requireCurrentUserRejectsAnonymousRequests() {
        assertThrows(SecurityException.class, () -> RbacPolicy.requireCurrentUser(Permission.MANAGE_SYSTEM_SETTINGS));
    }

    @Test
    void requireCurrentUserAllowsManagerForMigration() {
        UserSession.getInstance().login(new User(42, "manager", "", UserRole.MANAGER));
        assertDoesNotThrow(() -> RbacPolicy.requireCurrentUser(Permission.EXECUTE_DATABASE_MIGRATION));
    }

    @Test
    void requireCurrentUserRejectsCashierForMigration() {
        UserSession.getInstance().login(new User(7, "cashier", "", UserRole.CASHIER));
        assertThrows(SecurityException.class,
                () -> RbacPolicy.requireCurrentUser(Permission.EXECUTE_DATABASE_MIGRATION));
    }
}
