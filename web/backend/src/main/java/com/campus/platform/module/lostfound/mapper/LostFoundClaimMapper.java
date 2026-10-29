package com.campus.platform.module.lostfound.mapper;

import com.campus.platform.module.lostfound.entity.LostFoundClaim;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * LostFoundClaim Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 *
 * <p>P1 第6项修复：新增条件更新，用「WHERE status=0」把并发下「先读后写」的
 * 重复同意/拒绝收敛为单行原子更新（受影响行数 0 即已处理，幂等失败）。
 */
@Mapper
public interface LostFoundClaimMapper extends BaseMapper<LostFoundClaim> {

    /**
     * 仅当申请仍处于「待确认(0)」时更新为指定状态，返回受影响行数（0=已被并发处理）。
     */
    @Update("UPDATE lost_found_claim SET status = #{status} WHERE id = #{id} AND status = 0")
    int updateStatusIfPending(@Param("id") Long id, @Param("status") int status);

    /**
     * 同意某申请后，把同信息其余「待确认(0)」申请原子批量驳回（排除当前申请）。
     */
    @Update("UPDATE lost_found_claim SET status = 2 " +
            "WHERE lost_found_id = #{lostFoundId} AND status = 0 AND id <> #{excludeId}")
    int rejectOtherPending(@Param("lostFoundId") Long lostFoundId, @Param("excludeId") Long excludeId);

    /**
     * 仅当申请处于「已同意(1)」时标记为「已找回(3)」，返回受影响行数（0=状态不允许，防并发重复完成）。
     */
    @Update("UPDATE lost_found_claim SET status = 3 WHERE id = #{id} AND status = 1")
    int updateReturnedIfAgreed(@Param("id") Long id);

    /**
     * R3 复查修复：有效认领数的<b>当前读</b>（SELECT ... FOR UPDATE）。
     * 在 MySQL REPEATABLE READ 下，普通 SELECT 会沿用事务快照，看不到别的事务刚提交的认领；
     * 加 FOR UPDATE 后读当前已提交版本，配合父行 FOR UPDATE 锁保证「串行化 → 看到前一笔」。
     */
    @Select("SELECT COUNT(*) FROM lost_found_claim " +
            "WHERE lost_found_id = #{lostFoundId} AND status IN (0,1) FOR UPDATE")
    long countActiveForUpdate(@Param("lostFoundId") Long lostFoundId);
}
