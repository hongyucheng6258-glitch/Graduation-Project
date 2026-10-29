package com.campus.platform.module.drawgame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("draw_game_round")
public class DrawGuessRound {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Integer turnNumber;
    private Long drawerUserId;
    private String word;
    private Long snapshotResourceId;
    private String drawingData;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
