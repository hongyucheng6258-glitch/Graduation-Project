package com.campus.platform.module.drawgame.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DrawGuessWsTicketDTO {
    @NotNull
    private Long roomId;
}
