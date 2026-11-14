package com.campus.platform.module.partner.mapper;

import com.campus.platform.module.partner.entity.StudyPartner;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/** 学习搭子 Mapper */
@Mapper
public interface StudyPartnerMapper extends BaseMapper<StudyPartner> {
    @Select("SELECT * FROM study_partner WHERE id = #{id} FOR UPDATE")
    StudyPartner selectByIdForUpdate(@Param("id") Long id);
}
