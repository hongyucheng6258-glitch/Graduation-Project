package com.campus.platform.module.activity.mapper;

import com.campus.platform.module.activity.service.ActivityWaitlist.Entry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityWaitlistMapper {
    String ENTRY_QUERY = """
            SELECT w.id, w.activity_id, a.title, w.state, w.offer_until, w.queued_at
            FROM activity_waitlist w JOIN activity a ON a.id = w.activity_id
            """;

    @Select(ENTRY_QUERY + " WHERE w.user_id=#{userId} ORDER BY w.id DESC LIMIT 100")
    List<Entry> mine(@Param("userId") long userId);

    @Delete("DELETE FROM activity_waitlist WHERE activity_id=#{activityId} AND user_id=#{userId}")
    int deleteEntry(@Param("activityId") long activityId,
                    @Param("userId") long userId);

    @Insert("INSERT INTO activity_waitlist(activity_id,user_id,state,queued_at) VALUES(#{activityId},#{userId},'WAITING',#{now})")
    int enqueue(@Param("activityId") long activityId,
                @Param("userId") long userId,
                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE activity_waitlist SET state='CANCELLED'
            WHERE id=#{entryId} AND activity_id=#{activityId} AND user_id=#{userId} AND state IN ('WAITING','OFFERED')
            """)
    int cancel(@Param("entryId") long entryId,
               @Param("activityId") long activityId,
               @Param("userId") long userId);

    @Update("""
            UPDATE activity_waitlist SET state='CLAIMED'
            WHERE id=#{entryId} AND activity_id=#{activityId} AND user_id=#{userId} AND state='OFFERED' AND offer_until>#{now}
            """)
    int claim(@Param("entryId") long entryId,
              @Param("activityId") long activityId,
              @Param("userId") long userId,
              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE activity_waitlist SET state=#{state} WHERE activity_id=#{activityId} AND user_id=#{userId}
            AND (state='CLAIMED' OR (state='APPROVED' AND #{state}='CANCELLED'))
            """)
    int release(@Param("state") String state,
                @Param("activityId") long activityId,
                @Param("userId") long userId);

    @Update("UPDATE activity_waitlist SET state='EXPIRED' WHERE activity_id=#{activityId} AND state IN ('WAITING','OFFERED')")
    int expireAll(@Param("activityId") long activityId);

    @Update("UPDATE activity_waitlist SET state='EXPIRED' WHERE activity_id=#{activityId} AND state='OFFERED' AND offer_until<=#{now}")
    int expireOffers(@Param("activityId") long activityId,
                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT w.id,w.user_id FROM activity_waitlist w
            WHERE w.activity_id=#{activityId} AND w.state='WAITING'
              AND NOT EXISTS (SELECT 1 FROM activity_member m WHERE m.activity_id=w.activity_id AND m.user_id=w.user_id)
            ORDER BY w.id LIMIT #{available}
            """)
    List<Candidate> findCandidates(@Param("activityId") long activityId,
                                   @Param("available") long available);

    @Update("UPDATE activity_waitlist SET state='OFFERED',offer_until=#{until} WHERE id=#{id}")
    int offer(@Param("until") LocalDateTime until,
              @Param("id") long id);

    @Select("""
            SELECT DISTINCT w.activity_id FROM activity_waitlist w JOIN activity a ON a.id=w.activity_id
            WHERE (w.state='OFFERED' AND w.offer_until<=#{expiry})
               OR (w.state='WAITING' AND (a.status IN (2,3) OR a.audit_status<>1 OR a.max_members=0
                  OR a.start_time<=#{waitingStart} OR a.end_time<=#{waitingEnd} OR a.signup_deadline<=#{waitingDeadline}
                  OR a.max_members > (SELECT COUNT(*) FROM activity_member m WHERE m.activity_id=a.id AND m.status IN (0,1))
                     + (SELECT COUNT(*) FROM activity_waitlist q WHERE q.activity_id=a.id AND q.state='OFFERED' AND q.offer_until>NOW())))
               OR (w.state='OFFERED' AND (a.status IN (2,3) OR a.audit_status<>1 OR a.max_members=0
                  OR a.start_time<=#{offeredStart} OR a.end_time<=#{offeredEnd} OR a.signup_deadline<=#{offeredDeadline}))
            ORDER BY w.activity_id LIMIT 100
            """)
    List<Long> findQueuesToAdvance(@Param("expiry") LocalDateTime expiry,
                                   @Param("waitingStart") LocalDateTime waitingStart,
                                   @Param("waitingEnd") LocalDateTime waitingEnd,
                                   @Param("waitingDeadline") LocalDateTime waitingDeadline,
                                   @Param("offeredStart") LocalDateTime offeredStart,
                                   @Param("offeredEnd") LocalDateTime offeredEnd,
                                   @Param("offeredDeadline") LocalDateTime offeredDeadline);

    @Select(ENTRY_QUERY + " WHERE w.activity_id=#{activityId} AND w.user_id=#{userId}")
    List<Entry> findEntry(@Param("activityId") long activityId,
                          @Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM activity_member WHERE activity_id=#{activityId} AND user_id=#{userId}")
    long countMembership(@Param("activityId") long activityId,
                         @Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM activity_member WHERE activity_id=#{activityId} AND status IN (#{pending},#{approved})")
    long countReserved(@Param("activityId") long activityId,
                       @Param("pending") int pending,
                       @Param("approved") int approved);

    @Select("""
            SELECT COUNT(*) FROM activity_waitlist WHERE activity_id=#{activityId}
            AND state='OFFERED' AND offer_until>#{now}
            AND (#{exceptUser,jdbcType=BIGINT} IS NULL OR user_id<>#{exceptUser,jdbcType=BIGINT})
            """)
    long countOffers(@Param("activityId") long activityId,
                     @Param("now") LocalDateTime now,
                     @Param("exceptUser") Long exceptUser);

    record Candidate(long id, long userId) {}
}
