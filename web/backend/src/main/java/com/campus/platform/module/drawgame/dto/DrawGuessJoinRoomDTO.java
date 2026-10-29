package com.campus.platform.module.drawgame.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DrawGuessJoinRoomDTO {
    @Size(max = 32)
    private String password;
}
