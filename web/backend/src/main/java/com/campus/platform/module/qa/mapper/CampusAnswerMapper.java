package com.campus.platform.module.qa.mapper;

import com.campus.platform.module.qa.entity.CampusAnswer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 问答-回答 Mapper */
@Mapper
public interface CampusAnswerMapper extends BaseMapper<CampusAnswer> {
    @org.apache.ibatis.annotations.Select("""
            <script>
            SELECT question_id, COUNT(*) AS answer_count FROM campus_answer
            WHERE question_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY question_id
            </script>
            """)
    java.util.List<java.util.Map<String, Object>> countByQuestionIds(
            @org.apache.ibatis.annotations.Param("ids") java.util.List<Long> ids);
}
