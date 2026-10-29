package com.campus.platform.module.drawgame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.platform.module.drawgame.entity.DrawGuessRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DrawGuessRoundMapper extends BaseMapper<DrawGuessRound> {
    @Select("""
            SELECT r.id, r.room_id, r.turn_number, r.drawer_user_id, r.word,
                   r.snapshot_resource_id, r.ended_at
            FROM draw_game_round r
            INNER JOIN draw_game_room room ON room.id = r.room_id
            WHERE r.status = 'FINISHED'
              AND r.snapshot_resource_id IS NOT NULL
              AND room.private_room = 0
            ORDER BY r.ended_at DESC
            LIMIT #{limit}
            """)
    List<DrawGuessRound> selectPublicGalleryRounds(@Param("limit") int limit);
}
