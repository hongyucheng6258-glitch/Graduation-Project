package com.campus.platform.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSendDTO {
    @NotBlank
    @Size(max = 64)
    private String clientMessageId;
    @NotBlank
    @Pattern(regexp = "text|image")
    private String messageType;
    @NotBlank
    @Size(max = 6_000_000)
    private String content;
    private Long resourceId;
}
