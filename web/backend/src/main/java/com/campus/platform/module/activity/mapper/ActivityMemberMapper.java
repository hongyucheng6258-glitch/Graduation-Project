package com.campus.platform.module.activity.mapper;

import com.campus.platform.common.Constants;
import com.campus.platform.module.activity.entity.ActivityMember;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ActivityMember Mapper（MyBatis-Plus BaseMapper，CRUD 零 XML）。
 */
@Mapper
public interface ActivityMemberMapper extends BaseMapper<ActivityMember> {
    default Map<Long, Long> approvedCounts(List<Long> activityIds) {
        if (activityIds.isEmpty()) return Map.of();
        return countByActivities(activityIds, Constants.MEMBER_APPROVED).stream()
                .collect(Collectors.toMap(ActivityCount::activityId, ActivityCount::total));
    }

    @Select("""
            <script>
            SELECT activity_id, COUNT(*) AS total
            FROM activity_member
            WHERE status = #{status} AND activity_id IN
            <foreach collection="activityIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY activity_id
            </script>
            """)
    List<ActivityCount> countByActivities(@Param("activityIds") List<Long> activityIds, @Param("status") int status);

    @Select("""
            SELECT COUNT(CASE WHEN status=1 THEN 1 END) AS approved,
                   COUNT(CASE WHEN status=0 THEN 1 END) AS pending
            FROM activity_member WHERE activity_id=#{id} AND status IN (0,1)
            """)
    CapacityCount capacityCounts(@Param("id") Long id);

    default Map<Long, ActivityCapacity> capacityByActivities(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return capacityRows(ids).stream().collect(Collectors.toMap(ActivityCapacity::activityId, row -> row));
    }

    @Select("""
            <script>
            SELECT activity_id, COUNT(CASE WHEN status=1 THEN 1 END) AS approved,
                   COUNT(CASE WHEN status=0 THEN 1 END) AS pending
            FROM activity_member WHERE status IN (0,1) AND activity_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY activity_id
            </script>
            """)
    List<ActivityCapacity> capacityRows(@Param("ids") List<Long> ids);

    record ActivityCapacity(Long activityId, long approved, long pending) {}
    record CapacityCount(long approved, long pending) {}
    record ActivityCount(Long activityId, Long total) {}
}
