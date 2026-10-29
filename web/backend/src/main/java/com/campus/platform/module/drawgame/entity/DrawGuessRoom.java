package com.campus.platform.module.drawgame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("draw_game_room")
public class DrawGuessRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomCode;
    private Long ownerUserId;
    private String title;
    private Boolean privateRoom;
    private String passwordHash;
    private Integer maxPlayers;
    private Integer roundsPerPlayer;
    private String status;
    private Integer currentTurn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
