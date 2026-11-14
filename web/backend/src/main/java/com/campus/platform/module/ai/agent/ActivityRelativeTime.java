package com.campus.platform.module.ai.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

final class ActivityRelativeTime {
    private static final String DATE = "今天|明天|后天|(?:这周|本周|下周|周|这星期|本星期|下星期|星期)([一二三四五六日天])";
    private static final String TIME = "(?:(凌晨|早上|上午|中午|下午|傍晚|晚上)\\s*)?"
            + "([0-9]{1,2})(?:[:：]([0-9]{2})|[点时](半|[0-9]{1,2}分?)?)(?![0-9分秒半])";
    private static final Pattern RANGE = Pattern.compile("(" + DATE + ")\\s*" + TIME
            + "(?:\\s*[到至—~～-]\\s*" + TIME + ")?");

    private ActivityRelativeTime() {}

    record Range(LocalDateTime start, LocalDateTime end) {}

    static Range resolve(String text, LocalDateTime now) {
        Range latest = null;
        var matches = RANGE.matcher(text);
        while (matches.find()) {
            LocalDate day = resolveDay(matches, now.toLocalDate());
            LocalTime start = clock(matches, 3, null);
            if (start == null) continue;
            LocalDateTime startAt = day.atTime(start);
            String date = matches.group(1);
            if ((date.startsWith("周") || date.startsWith("星期")) && !startAt.isAfter(now)) {
                startAt = startAt.plusWeeks(1);
            }
            if (!startAt.isAfter(now)) continue;
            LocalTime end = matches.group(8) == null ? null : clock(matches, 7, matches.group(3));
            LocalDateTime endAt = end == null ? null : startAt.toLocalDate().atTime(end);
            latest = new Range(startAt, endAt != null && endAt.isAfter(startAt) ? endAt : null);
        }
        return latest;
    }

    private static LocalDate resolveDay(MatchResult match, LocalDate today) {
        String date = match.group(1);
        if (date.equals("今天")) return today;
        if (date.equals("明天")) return today.plusDays(1);
        if (date.equals("后天")) return today.plusDays(2);
        int weekday = "一二三四五六日天".indexOf(match.group(2)) + 1;
        DayOfWeek target = DayOfWeek.of(Math.min(weekday, 7));
        if (date.startsWith("周") || date.startsWith("星期")) {
            return today.with(TemporalAdjusters.nextOrSame(target));
        }
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusDays(target.getValue() - 1L).plusWeeks(date.startsWith("下") ? 1 : 0);
    }

    private static LocalTime clock(MatchResult match, int group, String inheritedPeriod) {
        String period = match.group(group);
        if (period == null) period = inheritedPeriod;
        int hour = Integer.parseInt(match.group(group + 1));
        String minutes = match.group(group + 2);
        if (minutes == null) minutes = match.group(group + 3);
        int minute = minutes == null ? 0 : minutes.equals("半") ? 30 : Integer.parseInt(minutes.replace("分", ""));
        if (period != null && hour <= 12) {
            if (period.matches("下午|傍晚|晚上") && hour < 12) hour += 12;
            if (period.equals("中午") && hour < 11) hour += 12;
            if (period.equals("凌晨") && hour == 12) hour = 0;
        }
        return hour > 23 || minute > 59 ? null : LocalTime.of(hour, minute);
    }
}
