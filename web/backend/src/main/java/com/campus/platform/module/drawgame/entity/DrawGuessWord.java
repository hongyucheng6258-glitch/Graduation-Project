package com.campus.platform.module.drawgame.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("draw_game_word")
public class DrawGuessWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String category;
    private Boolean active;
}
