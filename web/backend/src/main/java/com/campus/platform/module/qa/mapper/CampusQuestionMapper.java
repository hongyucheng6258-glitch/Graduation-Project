package com.campus.platform.module.qa.mapper;

import com.campus.platform.module.qa.entity.CampusQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

/** 问答-问题 Mapper */
@Mapper
public interface CampusQuestionMapper extends BaseMapper<CampusQuestion> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM campus_question WHERE id=#{id} FOR UPDATE")
    CampusQuestion lockById(@org.apache.ibatis.annotations.Param("id") Long id);
    @Update("UPDATE campus_question SET view_count = COALESCE(view_count, 0) + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
}
