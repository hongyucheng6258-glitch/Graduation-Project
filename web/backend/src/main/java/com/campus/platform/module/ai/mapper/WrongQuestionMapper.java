package com.campus.platform.module.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.module.ai.entity.WrongQuestion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * WrongQuestion Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
    default void applyReviewSort(LambdaQueryWrapper<WrongQuestion> wrapper, String sort) {
        if ("last_review_asc".equals(sort)) {
            // 从未复习（null）最优先
            wrapper.last("ORDER BY last_review_time IS NULL DESC, last_review_time ASC, id DESC");
        } else if ("wrong_desc".equals(sort)) {
            wrapper.orderByDesc(WrongQuestion::getWrongCount).orderByDesc(WrongQuestion::getId);
        } else if ("difficulty_desc".equals(sort)) {
            wrapper.last("ORDER BY FIELD(difficulty, '难', '中', '易') DESC, id DESC");
        } else {
            wrapper.orderByDesc(WrongQuestion::getId);
        }
    }

}
