package com.tpm.project;

/**
 * Per-project roles, ordered from least to most privileged so permission checks are a simple
 * "at least" comparison.
 */
public enum ProjectRole {
    /** Read-only access to the board, tasks and comments. */
    VIEWER,
    /** Can create / edit / move tasks and comment. */
    MEMBER,
    /** Can additionally manage columns, members and project settings. */
    ADMIN,
    /** Full control including deleting the project and transferring ownership. */
    OWNER;

    public boolean atLeast(ProjectRole required) {
        return compareTo(required) >= 0;
    }
}
