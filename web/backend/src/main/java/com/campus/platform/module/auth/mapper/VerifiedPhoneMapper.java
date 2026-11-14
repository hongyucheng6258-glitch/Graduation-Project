package com.campus.platform.module.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface VerifiedPhoneMapper {
    @Select("SELECT phone FROM verified_student_phone WHERE user_id = #{userId}")
    String findPhone(@Param("userId") long userId);

    @Select("SELECT user_id FROM verified_student_phone WHERE phone = #{phone}")
    Long findUserId(@Param("phone") String phone);

    @Update("UPDATE verified_student_phone SET phone = #{phone}, verified_at = NOW() WHERE user_id = #{userId}")
    int updatePhone(@Param("userId") long userId, @Param("phone") String phone);

    @Insert("INSERT INTO verified_student_phone(user_id, phone) VALUES (#{userId}, #{phone})")
    int insertPhone(@Param("userId") long userId, @Param("phone") String phone);
}
