package vn.edu.uit.edulens.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    ROLE_STUDENT,
    ROLE_LECTURER,
    ROLE_ADMIN;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("STUDENT".equals(normalized) || "ROLE_STUDENT".equals(normalized)) {
            return ROLE_STUDENT;
        }
        if ("LECTURER".equals(normalized) || "ROLE_LECTURER".equals(normalized) || "TEACHER".equals(normalized)) {
            return ROLE_LECTURER;
        }
        if ("ADMIN".equals(normalized) || "ROLE_ADMIN".equals(normalized)) {
            return ROLE_ADMIN;
        }
        for (Role r : Role.values()) {
            if (r.name().equalsIgnoreCase(normalized)) {
                return r;
            }
        }
        return ROLE_STUDENT;
    }
}

