package com.campus.platform.module.admin.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdminPermission {
    CONTENT_REVIEW("audit:content:decide", false),
    REPORT_HANDLE("report:handle", false),
    USER_STATUS("user:status:edit", false),
    USER_PASSWORD_RESET("user:password:reset", true),
    NOTICE_WRITE("notice:write", false),
    AI_CONFIG_EDIT("ai:config:edit", true),
    AI_PROMPT_EDIT("ai:prompt:edit", true),
    ACTIVITY_EXPORT("activity:export", true),
    SYSTEM_CONFIG_EDIT("system:config:edit", true),
    OPERATION_LOG_READ("system:operation-log:list", true);

    private final String code;
    private final boolean superOnly;

    public boolean allows(String role) {
        return "super".equals(role) || (!superOnly && "audit".equals(role));
    }
}
