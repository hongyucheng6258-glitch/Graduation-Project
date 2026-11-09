package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
class AdminAssistantDiagnostics {
    private final JdbcTemplate jdbc;

    Summary summarize(int days) {
        if (days != 1 && days != 7 && days != 30) throw new BizException(ResultCode.BAD_REQUEST, "仅支持今天、近7天或近30天");
        LocalDate end = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        var start = end.minusDays(days - 1L);
        var groups = jdbc.query("""
                SELECT tools,status,COUNT(*) FROM agent_run WHERE created_at>=? AND created_at<? GROUP BY tools,status
                """, (rs, row) -> new Group(rs.getString(1), "FAILED".equals(rs.getString(2)), rs.getLong(3)),
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        long runs = 0, failures = 0, untrackedFailures = 0;
        var tools = new HashMap<String, Tool>();
        for (Group group : groups) {
            long failed = group.failed() ? group.count() : 0;
            runs += group.count();
            failures += failed;
            var names = Arrays.stream(group.tools().split(",")).map(String::strip).filter(name -> !name.isEmpty()).distinct().toList();
            if (names.isEmpty()) untrackedFailures += failed;
            for (String name : names) {
                tools.merge(name, new Tool(name, group.count(), failed),
                        (old, next) -> new Tool(name, old.runs() + next.runs(), old.failed() + next.failed()));
            }
        }
        var sorted = tools.values().stream().sorted(Comparator.comparingLong(Tool::failed).reversed()
                .thenComparing(Comparator.comparingLong(Tool::runs).reversed()).thenComparing(Tool::name)).toList();
        return new Summary(start, end, runs, failures, untrackedFailures, sorted);
    }

    private record Group(String tools, boolean failed, long count) {}
    public record Tool(String name, long runs, long failed) {}
    public record Summary(LocalDate start, LocalDate end, long runs, long failed, long untrackedFailures, List<Tool> tools) {}
}
