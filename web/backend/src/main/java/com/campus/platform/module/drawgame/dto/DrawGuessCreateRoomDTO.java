package com.campus.platform.module.drawgame.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DrawGuessCreateRoomDTO {
    @Size(max = 40)
    private String title;

    private Boolean privateRoom = false;

    @Size(max = 32)
    private String password;

    @Min(2)
    @Max(6)
    private Integer maxPlayers = 6;

    @Min(1)
    @Max(8)
    private Integer roundsPerPlayer = 1;
}
