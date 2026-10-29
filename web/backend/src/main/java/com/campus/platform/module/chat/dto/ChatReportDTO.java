package com.campus.platform.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatReportDTO {
    @NotBlank
    @Size(max = 32)
    private String reasonType;
    @Size(max = 500)
    private String reason;
}
