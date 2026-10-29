package com.campus.platform.module.ai.entity;

import java.util.List;

/** AI内容审核的结构化结果。 */
public record AiAuditResult(
        String level,
        Integer score,
        String reason,
        List<String> categories) {
}
