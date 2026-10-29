package com.campus.platform.module.qa.mapper;

import com.campus.platform.module.qa.entity.CampusQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 问答-问题 Mapper */
@Mapper
public interface CampusQuestionMapper extends BaseMapper<CampusQuestion> {
}
