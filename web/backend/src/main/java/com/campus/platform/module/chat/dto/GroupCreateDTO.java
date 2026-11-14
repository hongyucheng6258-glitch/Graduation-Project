package com.campus.platform.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GroupCreateDTO {
    @NotBlank
    @Size(max = 32)
    private String name;

    @NotNull
    @Size(min = 2, max = 49)
    private List<@NotNull @Positive Long> memberIds;
}
