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

    record ActivityCount(Long activityId, Long total) {}
}
