package com.campus.platform.module.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConversationCreateDTO {
    @NotNull
    private Long targetUserId;
    @Size(max = 32)
    private String contextType;
    private Long contextId;
    @Size(max = 128)
    private String contextTitle;
}
