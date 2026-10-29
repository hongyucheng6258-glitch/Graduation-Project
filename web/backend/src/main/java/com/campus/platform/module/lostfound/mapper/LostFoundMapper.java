package com.campus.platform.module.lostfound.mapper;

import com.campus.platform.module.lostfound.entity.LostFound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * LostFound Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 *
 * <p>P1 第6项修复：新增行锁查询，供认领流程在事务内对父行加排他锁，
 * 串行化同一招领信息上的并发认领申请（防止并发 double-claim）。
 */
@Mapper
public interface LostFoundMapper extends BaseMapper<LostFound> {

    /** 行级排他锁查询：SELECT ... FOR UPDATE（须在事务内调用） */
    @Select("SELECT * FROM lost_found WHERE id = #{id} FOR UPDATE")
    LostFound selectByIdForUpdate(java.lang.Long id);
}
