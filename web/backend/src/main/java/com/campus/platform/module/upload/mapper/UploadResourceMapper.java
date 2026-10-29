package com.campus.platform.module.upload.mapper;

import com.campus.platform.module.upload.entity.UploadResource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UploadResourceMapper extends BaseMapper<UploadResource> {
    @Select("SELECT * FROM upload_resource WHERE resource_url = #{url} LIMIT 1")
    UploadResource findByUrl(@Param("url") String url);

    @Update("UPDATE upload_resource SET biz_type = #{bizType}, biz_id = #{bizId} " +
            "WHERE id = #{id} AND owner_user_id = #{ownerUserId} AND biz_type IS NULL")
    int bindIfUnbound(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId,
                      @Param("bizType") String bizType, @Param("bizId") Long bizId);
}
