package com.tpm.seed;

import java.util.List;

import com.tpm.user.User;

/** Pre-seeded accounts so visitors (and interviewers) can log in with one click. */
public record DemoAccount(String email, String fullName, User.Role role, String description, String color) {

    public static final String PASSWORD = "Demo@1234";
    public static final String DOMAIN = "@demo.tpm";

    public static final List<DemoAccount> ALL = List.of(
            new DemoAccount("alice@demo.tpm", "Alice Johnson", User.Role.USER, "Project owner", "#6366f1"),
            new DemoAccount("bob@demo.tpm", "Bob Smith", User.Role.USER, "Developer (member)", "#14b8a6"),
            new DemoAccount("carol@demo.tpm", "Carol Diaz", User.Role.USER, "Designer (project admin)", "#ec4899"),
            new DemoAccount("victor@demo.tpm", "Victor Viewer", User.Role.USER, "Stakeholder (read-only)", "#f59e0b"),
            new DemoAccount("admin@demo.tpm", "Ada Admin", User.Role.ADMIN, "System administrator", "#ef4444"));

    public static boolean isDemo(String email) {
        return email != null && email.endsWith(DOMAIN);
    }
}
