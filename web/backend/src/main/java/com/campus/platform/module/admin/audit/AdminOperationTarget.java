package com.campus.platform.module.admin.audit;

public interface AdminOperationTarget {
    String operationTargetId();

    default String operationDecision() {
        return null;
    }

    default String operationTargetType() {
        return null;
    }
}
