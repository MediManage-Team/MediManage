package org.example.MediManage.security;

import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RbacPolicy {
    private static final Map<Permission, Set<UserRole>> ALLOWED_ROLES = buildPolicy();

    private RbacPolicy() {
    }

    public static boolean canAccess(UserRole role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }
        Set<UserRole> allowed = ALLOWED_ROLES.get(permission);
        return allowed != null && allowed.contains(role);
    }

    public static void requireCurrentUser(Permission permission) {
        User user = UserSession.getInstance().getUser();
        if (user == null) {
            throw new SecurityException("Access denied: login is required to " + permission.description() + ".");
        }
        requireRole(user.getRole(), permission);
    }

    public static void requireRole(UserRole role, Permission permission) {
        if (canAccess(role, permission)) {
            return;
        }
        Set<UserRole> allowedRoles = ALLOWED_ROLES.getOrDefault(permission, Collections.emptySet());
        String allowed = allowedRoles.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));
        throw new SecurityException("Access denied: requires one of roles [" + allowed + "] to "
                + permission.description() + ".");
    }

    public static Set<UserRole> allowedRoles(Permission permission) {
        return ALLOWED_ROLES.getOrDefault(permission, Collections.emptySet());
    }

    private static Map<Permission, Set<UserRole>> buildPolicy() {
        EnumMap<Permission, Set<UserRole>> map = new EnumMap<>(Permission.class);

        Set<UserRole> adminOnly = EnumSet.of(UserRole.ADMIN);
        Set<UserRole> adminManager = EnumSet.of(UserRole.ADMIN, UserRole.MANAGER);
        Set<UserRole> enrollmentOperators = EnumSet.of(
                UserRole.ADMIN,
                UserRole.MANAGER,
                UserRole.PHARMACIST,
                UserRole.CASHIER);

        map.put(Permission.MANAGE_USERS, adminOnly);
        map.put(Permission.MANAGE_MEDICINES, adminManager);
        map.put(Permission.MANAGE_SYSTEM_SETTINGS, adminManager);
        map.put(Permission.EXECUTE_DATABASE_MIGRATION, adminManager);
        map.put(Permission.MANAGE_SUBSCRIPTION_POLICY, adminManager);
        map.put(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS, enrollmentOperators);
        map.put(Permission.APPROVE_SUBSCRIPTION_OVERRIDES, adminManager);

        return Collections.unmodifiableMap(map);
    }
}
