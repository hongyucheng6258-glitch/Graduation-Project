package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.campus.platform.module.ai.mapper.AdminAssistantEvidenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AdminAssistantEvidence {
    private final AdminAssistantEvidenceMapper evidence;

    List<Ref> pendingIdle(String keyword) {
        return evidence.findPendingIdle(Constants.AUDIT_PENDING, keywordTerm(keyword)).stream()
                .map(id -> new Ref("idle", id)).toList();
    }

    List<Ref> pendingActivity(Long id, String keyword) {
        String term = id == null ? keywordTerm(keyword) : null;
        return evidence.findPendingActivity(Constants.AUDIT_PENDING, id, term).stream()
                .map(itemId -> new Ref("activity", itemId)).toList();
    }

    private String keywordTerm(String keyword) {
        if (keyword.length() > 80) throw new BizException(ResultCode.BAD_REQUEST, "审核关键词过长");
        return keyword.isEmpty() ? null
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
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
            for (var row : evidence.findIdle(ids(idle))) {
                var card = new Card("idle", Long.toString(row.id()), row.title(), row.status(), row.riskLevel(),
                        row.riskReason(), row.excerpt(), null, null, null);
                cards.put(card.ref(), card);
            }
        }
        var activity = byKind.get("activity");
        if (activity != null) {
            for (var row : evidence.findActivity(ids(activity))) {
                var fields = new ActivityFields(row.location(), row.startTime(), row.endTime(),
                        row.maxMembers(), row.signupDeadline(), row.version());
                var card = new Card("activity", Long.toString(row.id()), row.title(), row.status(), row.riskLevel(),
                        row.riskReason(), row.excerpt(), null, null, fields);
                cards.put(card.ref(), card);
            }
        }
        var report = byKind.get("report");
        if (report != null) {
            var reports = evidence.findReports(ids(report)).stream()
                    .map(row -> new Report(row.id(), row.status(), new Ref(row.targetType(), row.targetId()), row.reason()))
                    .toList();
            var targets = targets(reports);
            for (Report row : reports) {
                var target = targets.getOrDefault(row.target(), new Target(row.target().kind(),
                        Long.toString(row.target().id()), null, null));
                var card = new Card("report", Long.toString(row.id()), "举报 #" + row.id(), row.status(),
                        null, null, null, row.reason(), target, null);
                cards.put(card.ref(), card);
            }
        }
        return cards;
    }

    private Map<Ref, Target> targets(List<Report> reports) {
        var grouped = reports.stream().map(Report::target).distinct().collect(Collectors.groupingBy(Ref::kind));
        return grouped.entrySet().stream().flatMap(group -> {
            String kind = group.getKey();
            List<Long> ids = ids(group.getValue());
            var rows = switch (kind) {
                case "idle" -> evidence.findIdleTargets(ids);
                case "activity" -> evidence.findActivityTargets(ids);
                case "lostfound" -> evidence.findLostTargets(ids);
                case "post" -> evidence.findPostTargets(ids);
                case "comment" -> evidence.findCommentTargets(ids);
                case "user" -> evidence.findUserTargets(ids);
                default -> List.<AdminAssistantEvidenceMapper.TargetRow>of();
            };
            return rows.stream().map(row -> new Target(kind, Long.toString(row.id()), row.title(), row.excerpt()));
        }).collect(Collectors.toMap(target -> new Ref(target.kind(), Long.parseLong(target.id())), Function.identity()));
    }

    private List<Long> ids(List<Ref> refs) {
        return refs.stream().map(Ref::id).toList();
    }

    private record Report(long id, int status, Ref target, String reason) {}
    record Ref(String kind, long id) {}
    record Target(String kind, String id, String title, String excerpt) {}
    record ActivityFields(String location, LocalDateTime startTime, LocalDateTime endTime, Integer maxMembers,
                          LocalDateTime signupDeadline, String version) {}
    record Card(String kind, String id, String title, int status, Integer riskLevel, String riskReason,
                String excerpt, String reason, Target target,
                @JsonInclude(JsonInclude.Include.NON_NULL) ActivityFields activity) {
        Ref ref() { return new Ref(kind, Long.parseLong(id)); }
    }
}
