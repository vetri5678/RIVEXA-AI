package ai.riskvision.graveyard.model;

/**
 * Enterprise RBAC roles for RiskVision AI.
 *
 * Role hierarchy (highest to lowest privilege):
 *   ADMIN → MANAGER → ANALYST → AUDITOR → VIEWER
 *
 * Spring Security authorities are stored as "ROLE_<name>" (uppercase).
 * In the users table, role is stored in lowercase (e.g. "admin", "viewer").
 * CustomUserDetailsService normalises the role to uppercase when building the authority.
 */
public enum UserRole {
    /** Full system access — manage users, roles, configuration, all data. */
    ADMIN,
    /** Department/team manager — create/edit repositories and projects, view all reports. */
    MANAGER,
    /** Risk analyst — run predictions, view dashboards, read-write repository data. */
    ANALYST,
    /** Read-only auditor — view audit logs, read all data, cannot mutate anything. */
    AUDITOR,
    /** Default role — read-only access to their own projects and basic dashboard. */
    VIEWER
}
