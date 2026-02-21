package com.finallms.backend.enums;

public enum Role {
    ADMIN("Administrator"),
    STUDENT("Student"),
    INSTRUCTOR("Instructor");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
