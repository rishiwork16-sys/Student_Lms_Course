package com.finallms.backend.enums;

public enum AssignmentType {
    TEXT("Text Only"),
    FILE("File Upload");

    private final String displayName;

    AssignmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
