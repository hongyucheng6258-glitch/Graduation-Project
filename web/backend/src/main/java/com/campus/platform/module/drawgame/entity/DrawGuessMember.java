package com.campus.platform.module.drawgame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("draw_game_member")
public class DrawGuessMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long userId;
    private Integer seatNo;
    private String nickname;
    private String avatar;
    private Integer score;
    private Boolean active;
    private LocalDateTime joinedAt;
    private LocalDateTime lastVisitedAt;
}
