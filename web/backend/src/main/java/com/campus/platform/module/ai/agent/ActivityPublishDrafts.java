package com.campus.platform.module.ai.agent;

import cn.hutool.core.convert.NumberChineseFormatter;
import cn.hutool.crypto.SecureUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.dto.ActivityPublishDTO;
import com.campus.platform.module.activity.service.ActivityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ActivityPublishDrafts implements AgentToolSource {
    static final String PREPARE = "prepare_activity_publish";
    private static final int MAX_MEMBERS = 500;
    private static final int MAX_IMAGES = 3;
    private static final long MAX_ENCODED_IMAGE_LENGTH = 15L * 1024 * 1024;
    private static final Pattern MEMBER_LIMIT = Pattern.compile(
            "(?:人数上限|人数限制|最多|限额|限招|(?<!不)限|容纳)\\s*[:：为]?\\s*([0-9]{1,9}|[一二两三四五六七八九十百零]{1,8})(?![0-9])");
    private static final Pattern STANDALONE_COUNT = Pattern.compile(
            "^(?:共|一共)?\\s*([0-9]{1,3}|[一二两三四五六七八九十百零]{1,8})\\s*(?:个)?人$");
    private static final Pattern UNLIMITED = Pattern.compile(
            "不限(?:制)?人数|人数(?:不限|不设上限|无限制)|无人数限制|不设人数上限|人数上限\\s*[:：为]?\\s*0(?![0-9])");
    private static final Pattern SIGNUP_CUTOFF = Pattern.compile("截止|截至|报名[^。！？；]{0,8}关闭");
    private static final String CLOCK = "(?:[01]?[0-9]|2[0-3])(?:[:：][0-5]?[0-9](?:[:：][0-5]?[0-9])?|[点时](?:[0-5]?[0-9]分?)?)";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ActivityService activities;

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool(PREPARE,
                "学生明确要求发布活动并已给出标题时，生成可编辑的活动草稿。title 必填，其余字段只填学生明确提供的事实，"
                        + "缺失的字段必须省略。时间用 yyyy-MM-dd HH:mm:ss，maxMembers 为 0 至 500 的整数，明确不限才填 0。"
                        + "category 可选学习交流、体育运动、文艺娱乐、志愿服务、竞赛组队、其他。此工具只创建草稿。",
                Map.of("title", "string", "description", "string", "category", "string", "location", "string",
                        "startTime", "string", "endTime", "string", "signupDeadline", "string", "maxMembers", "integer"),
                List.of("title"), this::prepare));
    }

    private JsonNode prepare(Long uid, JsonNode args) {
        activities.ensurePublishingEnabled();
        var form = new ActivityPublishDTO();
        form.setTitle(args.path("title").asText("").strip());
        if (form.getTitle().isEmpty() || form.getTitle().length() > 64) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写不超过 64 字的活动标题");
        }
        form.setDescription(optionalText(args, "description"));
        form.setCategory(optionalText(args, "category"));
        form.setLocation(optionalText(args, "location"));
        form.setStartTime(time(args, "startTime"));
        form.setEndTime(time(args, "endTime"));
        form.setSignupDeadline(time(args, "signupDeadline"));
        var members = args.get("maxMembers");
        if (members != null && !members.isNull()) {
            if (!members.isIntegralNumber() || !members.canConvertToInt()) {
                throw new BizException(ResultCode.BAD_REQUEST, "人数上限必须为整数");
            }
            form.setMaxMembers(members.asInt());
        }
        validateTextLengths(form);
        validateMaxMembers(form.getMaxMembers());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO agent_activity_draft(id,user_id,title,description,category,location,start_time,end_time,
                                                 signup_deadline,max_members,expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, id, uid, form.getTitle(), form.getDescription(), form.getCategory(), form.getLocation(),
                form.getStartTime(), form.getEndTime(), form.getSignupDeadline(), form.getMaxMembers());
        return json.valueToTree(get(uid, id));
    }

    public Draft get(Long uid, String id) {
        return requireDraft(uid, id, false).card();
    }

    Draft find(Long uid, String id) {
        var stored = query(uid, id, false);
        return stored == null ? null : stored.card();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Draft confirm(Long uid, String id, ActivityPublishDTO form) {
        var stored = requireDraft(uid, id, true);
        var draft = stored.card();
        if ("EXPIRED".equals(draft.state())) {
            throw new BizException(ResultCode.BAD_REQUEST, "草稿已过期，请让校园向导重新生成");
        }
        form.setTitle(form.getTitle().strip());
        form.setDescription(blankToNull(form.getDescription()));
        form.setCategory(blankToNull(form.getCategory()));
        form.setLocation(blankToNull(form.getLocation()));
        if (form.getImages() == null) form.setImages(List.of());
        validateTextLengths(form);
        validateMaxMembers(form.getMaxMembers());
        validateImageList(form.getImages());
        String hash = confirmationHash(form);
        if (draft.activityId() != null) {
            if (!hash.equals(stored.confirmationHash())) {
                throw new BizException(409, "草稿已提交，请在我发起的活动中继续编辑");
            }
            return draft;
        }
        if (form.getLocation() == null || form.getStartTime() == null || form.getEndTime() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请补充活动地点、开始时间和结束时间后再提交");
        }
        checkOwnedImages(uid, form.getImages());
        var activity = activities.publish(uid, form);
        jdbc.update("""
                UPDATE agent_activity_draft SET activity_id=?,title=?,description=?,category=?,location=?,
                    start_time=?,end_time=?,signup_deadline=?,max_members=?,confirmation_hash=? WHERE id=? AND user_id=?
                """, activity.getId(), form.getTitle(), form.getDescription(), form.getCategory(), form.getLocation(),
                form.getStartTime(), form.getEndTime(), form.getSignupDeadline(), form.getMaxMembers(), hash, id, uid);
        return get(uid, id);
    }

    private String confirmationHash(ActivityPublishDTO form) {
        // A fixed sequence keeps retries comparable even if the request DTO gains new fields.
        var fields = Arrays.asList(form.getTitle(), form.getDescription(), form.getCategory(), form.getLocation(),
                form.getStartTime(), form.getEndTime(), form.getSignupDeadline(), form.getMaxMembers(), form.getImages());
        return SecureUtil.sha256(json.valueToTree(fields).toString());
    }

    private static void validateMaxMembers(Integer maxMembers) {
        if (maxMembers != null && (maxMembers < 0 || maxMembers > MAX_MEMBERS)) {
            throw new BizException(ResultCode.BAD_REQUEST, "人数上限必须在 0 至 500 之间");
        }
    }

    private static void validateTextLengths(ActivityPublishDTO form) {
        if (form.getCategory() != null && form.getCategory().length() > 32) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动分类最长 32 字");
        }
        if (form.getLocation() != null && form.getLocation().length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动地点最长 128 字");
        }
        if (form.getDescription() != null && form.getDescription().getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动描述过长，请缩短后再提交");
        }
    }

    private static void validateImageList(List<String> images) {
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ResultCode.BAD_REQUEST, "活动最多可上传 3 张图片");
        }
        long encodedLength = 0;
        for (String url : images) {
            if (url == null || url.isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "请重新上传活动图片");
            }
            encodedLength += url.length();
            if (encodedLength > MAX_ENCODED_IMAGE_LENGTH) {
                throw new BizException(ResultCode.BAD_REQUEST, "图片总量过大，请压缩后再提交");
            }
        }
    }

    private void checkOwnedImages(Long uid, List<String> images) {
        for (String url : images) {
            boolean owned = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM upload_resource WHERE owner_user_id=? AND resource_type='image'
                                  AND CAST(resource_url AS BINARY)=CAST(? AS BINARY))
                    """, Boolean.class, uid, url));
            if (!owned) {
                throw new BizException(ResultCode.BAD_REQUEST, "图片资源不存在或不属于当前用户");
            }
        }
    }

    private static String optionalText(JsonNode args, String field) {
        return blankToNull(args.path(field).asText(null));
    }

    static void retainStudentFacts(ObjectNode args, List<String> questions) {
        retainStudentFacts(args, questions, LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
    }

    static void retainStudentFacts(ObjectNode args, List<String> questions, LocalDateTime now) {
        for (String field : List.of("description", "category", "location")) {
            var value = args.path(field);
            if (value.isNull() || (value.isTextual() && questions.stream().noneMatch(q -> q.contains(value.asText().strip())))) {
                args.remove(field);
            }
        }
        for (String field : List.of("startTime", "endTime", "signupDeadline")) {
            var value = args.path(field);
            if (value.isNull() || (value.isTextual() && !hasDateEvidence(questions, field, time(args, field), now))) {
                args.remove(field);
            }
        }
        if (!questions.isEmpty()) {
            String latest = questions.get(questions.size() - 1);
            for (String clause : latest.split("[。！？；，,;\\r\\n]")) {
                var relative = ActivityRelativeTime.resolve(clause, now);
                if (relative != null) {
                    if (SIGNUP_CUTOFF.matcher(clause).find()) {
                        args.put("signupDeadline", relative.start().toString());
                    } else {
                        args.put("startTime", relative.start().toString());
                        args.remove("endTime");
                        if (relative.end() != null) args.put("endTime", relative.end().toString());
                    }
                }
                var count = STANDALONE_COUNT.matcher(clause.strip());
                if (count.matches()) {
                    int provided = parseMemberCount(count.group(1));
                    if (provided > 0 && provided <= MAX_MEMBERS) args.put("maxMembers", provided);
                }
            }
        }
        var members = args.path("maxMembers");
        if (members.isNull() || (members.isIntegralNumber() && members.canConvertToInt()
                && !hasMemberEvidence(questions, members.asInt()))) {
            args.remove("maxMembers");
        }
    }

    private static int parseMemberCount(String value) {
        try {
            return Character.isDigit(value.charAt(0)) ? Integer.parseInt(value)
                    : NumberChineseFormatter.chineseToNumber(value.replace('两', '二'));
        } catch (IllegalArgumentException ignored) {
            return -1;
        }
    }

    private static boolean hasDateEvidence(List<String> questions, String field, LocalDateTime time, LocalDateTime now) {
        if (time == null) return false;
        String month = paddedNumber(time.getMonthValue());
        String day = paddedNumber(time.getDayOfMonth());
        String monthDay = month + "月" + day + "[日号]\\s*";
        String year = Integer.toString(time.getYear());
        String explicitDate = year + "(?:[-/]" + month + "[-/]" + day + "[ T]+|年" + monthDay + ")";
        String date = time.getYear() == now.getYear() ? "(?:" + explicitDate + "|" + monthDay + ")" : explicitDate;
        String minute = paddedNumber(time.getMinute());
        String second = paddedNumber(time.getSecond());
        String numericClock = "[:：]" + minute + (time.getSecond() == 0 ? "(?:[:：]00)?" : "[:：]" + second);
        String chineseClock = time.getSecond() != 0 ? "" : "|[点时]"
                + (time.getMinute() == 0 ? "(?:0{1,2}分?)?" : minute + "分?");
        String clock = paddedNumber(time.getHour()) + "(?:" + numericClock + chineseClock + ")";
        String rangeStart = "endTime".equals(field) ? "(?:" + CLOCK + "\\s*[到至—~～-]\\s*)?" : "";
        var exactDateTime = Pattern.compile("(?<![0-9年])" + date + rangeStart + clock + "(?![0-9:：点时分秒半.])");
        return questions.stream().flatMap(q -> Arrays.stream(q.split("[。！？；，,;\\r\\n]")))
                .filter(clause -> !"signupDeadline".equals(field) || SIGNUP_CUTOFF.matcher(clause).find())
                .anyMatch(clause -> {
                    if (exactDateTime.matcher(clause.replaceAll("年\\s+", "年")).find()) return true;
                    boolean deadline = "signupDeadline".equals(field);
                    if (deadline != SIGNUP_CUTOFF.matcher(clause).find()) return false;
                    var relative = ActivityRelativeTime.resolve(clause, now);
                    return relative != null && time.equals("endTime".equals(field) ? relative.end() : relative.start());
                });
    }

    private static String paddedNumber(int number) {
        return (number < 10 ? "0?" : "") + number;
    }

    private static boolean hasMemberEvidence(List<String> questions, int members) {
        validateMaxMembers(members);
        if (members == 0) {
            return questions.stream().anyMatch(q -> UNLIMITED.matcher(q).find() || q.strip().matches("不限[。！!]?"));
        }
        for (String question : questions) {
            for (String clause : question.split("[。！？；，,;\\r\\n]")) {
                var standalone = STANDALONE_COUNT.matcher(clause.strip());
                if (standalone.matches() && parseMemberCount(standalone.group(1)) == members) return true;
            }
            var matches = MEMBER_LIMIT.matcher(question);
            while (matches.find()) {
                String value = matches.group(1);
                try {
                    int provided = Character.isDigit(value.charAt(0)) ? Integer.parseInt(value)
                            : NumberChineseFormatter.chineseToNumber(value.replace('两', '二'));
                    if (provided == members) return true;
                } catch (IllegalArgumentException ignored) {
                    // Unparseable counts are left blank for the student to fill in.
                }
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static LocalDateTime time(JsonNode args, String field) {
        String value = optionalText(args, field);
        if (value == null) return null;
        try {
            LocalDateTime time = LocalDateTime.parse(value.replace(' ', 'T'));
            if (time.getYear() >= 1000 && time.getYear() <= 9999) return time;
        } catch (DateTimeParseException ignored) {
            // The assistant can retry with the student-provided time in the required format.
        }
        throw new BizException(ResultCode.BAD_REQUEST, "活动时间必须包含有效的日期和时间");
    }

    private StoredDraft requireDraft(Long uid, String id, boolean lock) {
        var draft = query(uid, id, lock);
        if (draft == null) throw new BizException(ResultCode.NOT_FOUND, "未找到活动发布草稿");
        return draft;
    }

    private StoredDraft query(Long uid, String id, boolean lock) {
        return jdbc.query("""
                SELECT d.id,d.title,d.description,d.category,d.location,d.start_time,d.end_time,d.signup_deadline,
                       d.max_members,d.expires_at,d.activity_id,d.confirmation_hash,a.audit_status,a.audit_reason,
                       CASE WHEN d.activity_id IS NOT NULL THEN 'SUBMITTED'
                            WHEN d.expires_at<=NOW() THEN 'EXPIRED' ELSE 'DRAFT' END AS state
                FROM agent_activity_draft d LEFT JOIN activity a ON a.id=d.activity_id AND a.user_id=d.user_id
                WHERE d.id=? AND d.user_id=?
                """ + (lock ? " FOR UPDATE OF d" : ""), (rs, row) -> new StoredDraft(new Draft(rs.getString("id"), rs.getString("title"),
                rs.getString("description"), rs.getString("category"), rs.getString("location"),
                rs.getObject("start_time", LocalDateTime.class), rs.getObject("end_time", LocalDateTime.class),
                rs.getObject("signup_deadline", LocalDateTime.class), rs.getObject("max_members", Integer.class),
                rs.getObject("expires_at", LocalDateTime.class), rs.getObject("activity_id", Long.class),
                rs.getString("state"), rs.getObject("audit_status", Integer.class), rs.getString("audit_reason")),
                rs.getString("confirmation_hash")),
                id, uid).stream().findFirst().orElse(null);
    }

    private record StoredDraft(Draft card, String confirmationHash) {}

    public record Draft(String id, String title, String description, String category, String location,
                        LocalDateTime startTime, LocalDateTime endTime, LocalDateTime signupDeadline,
                        Integer maxMembers, LocalDateTime expiresAt, Long activityId, String state,
                        Integer auditStatus, String auditReason) {}
}
