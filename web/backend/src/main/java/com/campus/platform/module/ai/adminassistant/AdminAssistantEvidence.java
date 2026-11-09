package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AdminAssistantEvidence {
    private static final String IDLE_FIELDS = "SELECT id,title,LEFT(description,480),audit_status,ai_risk_level,ai_audit_reason FROM idle_item";
    private static final Map<String, String> TARGET_SQL = Map.of(
            "idle", "SELECT id,title,LEFT(description,480) FROM idle_item",
            "activity", "SELECT id,title,LEFT(description,480) FROM activity",
            "lostfound", "SELECT id,title,LEFT(description,480) FROM lost_found",
            "post", "SELECT id,'校园动态',LEFT(content,480) FROM post",
            "comment", "SELECT id,'动态评论',LEFT(content,480) FROM post_comment",
            "user", "SELECT id,'学生账号',NULL FROM user"
    );

    private final NamedParameterJdbcTemplate jdbc;

    List<Ref> pendingIdle(String keyword) {
        if (keyword.length() > 80) throw new BizException(ResultCode.BAD_REQUEST, "审核关键词过长");
        var params = new MapSqlParameterSource("pending", Constants.AUDIT_PENDING);
        String filter = "";
        if (!keyword.isEmpty()) {
            filter = " AND (title LIKE :keyword ESCAPE '!' OR description LIKE :keyword ESCAPE '!')";
            params.addValue("keyword", "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        return jdbc.query("SELECT id FROM idle_item WHERE audit_status=:pending" + filter
                + " ORDER BY COALESCE(ai_risk_level,0) DESC,id LIMIT 6", params,
                (rs, row) -> new Ref("idle", rs.getLong(1)));
    }

    List<Ref> report(long id) {
        if (id <= 0) throw new BizException(ResultCode.BAD_REQUEST, "举报编号无效");
        return List.of(new Ref("report", id));
    }

    Map<Ref, Card> cards(List<Ref> refs) {
        if (refs.isEmpty()) return Map.of();
        var byKind = refs.stream().distinct().collect(Collectors.groupingBy(Ref::kind));
        var cards = new HashMap<Ref, Card>();
        var idle = byKind.get("idle");
        if (idle != null) {
            jdbc.query(IDLE_FIELDS + " WHERE id IN (:ids)", ids(idle), (rs, row) ->
                    new Card("idle", rs.getString(1), rs.getString(2), rs.getInt(4), rs.getObject(5, Integer.class),
                            rs.getString(6), rs.getString(3), null, null)).forEach(card -> cards.put(card.ref(), card));
        }
        var report = byKind.get("report");
        if (report != null) {
            var reports = jdbc.query("""
                    SELECT id,status,target_type,target_id,reason FROM report WHERE id IN (:ids)
                    """, ids(report), (rs, row) -> new Report(rs.getLong(1), rs.getInt(2),
                    new Ref(rs.getString(3), rs.getLong(4)), rs.getString(5)));
            var targets = targets(reports);
            for (Report row : reports) {
                var target = targets.getOrDefault(row.target(), new Target(row.target().kind(),
                        Long.toString(row.target().id()), null, null));
                var card = new Card("report", Long.toString(row.id()), "举报 #" + row.id(), row.status(),
                        null, null, null, row.reason(), target);
                cards.put(card.ref(), card);
            }
        }
        return cards;
    }

    private Map<Ref, Target> targets(List<Report> reports) {
        var grouped = reports.stream().map(Report::target).distinct().collect(Collectors.groupingBy(Ref::kind));
        return grouped.entrySet().stream().filter(group -> TARGET_SQL.containsKey(group.getKey()))
                .flatMap(group -> jdbc.query(TARGET_SQL.get(group.getKey()) + " WHERE id IN (:ids)", ids(group.getValue()),
                        (rs, row) -> new Target(group.getKey(), rs.getString(1), rs.getString(2), rs.getString(3))).stream())
                .collect(Collectors.toMap(target -> new Ref(target.kind(), Long.parseLong(target.id())), Function.identity()));
    }

    private MapSqlParameterSource ids(List<Ref> refs) {
        return new MapSqlParameterSource("ids", refs.stream().map(Ref::id).toList());
    }

    private record Report(long id, int status, Ref target, String reason) {}
    record Ref(String kind, long id) {}
    record Target(String kind, String id, String title, String excerpt) {}
    record Card(String kind, String id, String title, int status, Integer riskLevel, String riskReason,
                String excerpt, String reason, Target target) {
        Ref ref() { return new Ref(kind, Long.parseLong(id)); }
    }
}
