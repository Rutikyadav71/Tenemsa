package com.tenemsa.platform.audit.model;

/**
 * Everything the audit trail records. Stored as plain text in audit_logs.action
 * (not a database enum), so adding a value never needs a schema change.
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    PASSWORD_RESET,
    COMPANY_REGISTERED,
    COMPANY_RENAMED,
    COMPANY_AI_TOGGLED,
    AI_TASKS_CREATED,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_FROZEN,
    USER_UNFROZEN,
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    LEAVE_DELETED,
    INVITE_SENT,
    INVITE_ACCEPTED,
    INVITE_REVOKED,
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_DELETED,
    ISSUE_DELETED,
    SPRINT_STARTED,
    SPRINT_COMPLETED,
    AI_MEETING_SUMMARY_GENERATED,
    MEETING_SUMMARY_APPROVED,
    MEETING_ISSUES_CREATED,
    MEETING_SUMMARY_DELETED
}
