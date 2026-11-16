package ma.sanai3i.domain;

/** What a staff member (role ADMIN, not owner) may do in the administration. The owner can do everything. */
public enum Permission {
    /** artisan files: identity check, approve / reject, verified badge */
    FILES,
    /** users: list, details, block, password reset, export */
    USERS,
    /** requests and reviews: cancel, delete, export */
    REQUESTS,
    /** reports from users */
    REPORTS,
    /** trades and price bounds */
    CATEGORIES,
    /** subscriptions, payments, revenue settings */
    BILLING,
    /** announcements to users */
    BROADCAST,
    /** site settings and system info */
    SETTINGS,
    /** audit log */
    AUDIT
}
