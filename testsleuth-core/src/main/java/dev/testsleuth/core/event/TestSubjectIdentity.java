package dev.testsleuth.core.event;

import java.util.Objects;

public final class TestSubjectIdentity {
    private TestSubjectIdentity() {
    }

    public static String testMethod(String className, String methodName) {
        String normalizedClassName = requireText(className, "className");
        String normalizedMethodName = normalizeMethodName(requireText(methodName, "methodName"));
        return normalizedClassName + "." + normalizedMethodName;
    }

    public static String normalizeMethodName(String methodName) {
        String normalized = requireText(methodName, "methodName").trim();
        int parameterStart = normalized.indexOf('(');
        if (parameterStart > 0) {
            normalized = normalized.substring(0, parameterStart);
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
