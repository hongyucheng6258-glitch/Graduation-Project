package com.campus.platform.module.idle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record IdleHandoffDTO(@NotNull @Min(0) Integer version, @NotNull LocalDateTime meetingAt,
                             @NotBlank @Size(max = 128) String location) {
    public record Confirmation(@NotNull @Min(1) Integer version) {}
}
