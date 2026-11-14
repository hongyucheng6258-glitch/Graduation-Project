package com.campus.platform.module.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivityRelativeTimeTest {
    private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 11, 13, 12, 0);

    @Test
    void fillsOnlyExplicitClockAndHeadcountFromTheStudentsMessage() {
        var args = new ObjectMapper().createObjectNode()
                .put("startTime", "2026-11-22 19:00:00").put("endTime", "2026-11-22 21:00:00");
        ActivityPublishDrafts.retainStudentFacts(args, List.of("帮我发布读书会", "这周日晚上7点，4个人，在407宿舍"), FRIDAY);
        assertEquals("2026-11-15T19:00", args.path("startTime").asText());
        assertEquals(4, args.path("maxMembers").asInt());
        assertFalse(args.has("endTime"));
        assertFalse(args.has("signupDeadline"));
    }

    @Test
    void handlesWeekBoundariesAndLeavesAmbiguousTimesBlank() {
        assertEquals(LocalDateTime.of(2026, 11, 22, 20, 30),
                ActivityRelativeTime.resolve("下周日晚上7点到8点半", FRIDAY).end());
        assertEquals(LocalDateTime.of(2026, 11, 15, 15, 0),
                ActivityRelativeTime.resolve("后天15:00", FRIDAY).start());
        assertNull(ActivityRelativeTime.resolve("周日，4个人", FRIDAY));
        assertNull(ActivityRelativeTime.resolve("这周五上午9点", FRIDAY));
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0),
                ActivityRelativeTime.resolve("周五上午9点", FRIDAY).start());
    }

    @Test
    void resolvesAnExplicitRelativeDeadlineSeparately() {
        var args = new ObjectMapper().createObjectNode();
        ActivityPublishDrafts.retainStudentFacts(args, List.of("这周日晚上7点到9点，报名截止明天18:00"), FRIDAY);
        assertEquals("2026-11-15T21:00", args.path("endTime").asText());
        assertEquals("2026-11-14T18:00", args.path("signupDeadline").asText());
    }
}
