package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.lostfound.dto.LostFoundPublishDTO;
import com.campus.platform.module.lostfound.service.LostFoundService;
import com.campus.platform.module.partner.dto.StudyPartnerDTO;
import com.campus.platform.module.partner.service.StudyPartnerService;
import com.campus.platform.module.qa.dto.AnswerDTO;
import com.campus.platform.module.qa.dto.QuestionDTO;
import com.campus.platform.module.qa.service.QuestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.campus.platform.module.ai.mapper.AgentCampusDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CampusPublishDrafts implements AgentToolSource {
    private static final String PREFIX = "prepare_campus_";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> CATEGORIES = Set.of("课程", "考试", "技术", "生活", "其他");

    private final AgentCampusDraftMapper drafts;
    private final ObjectMapper json;
    private final LostFoundService lostFound;
    private final StudyPartnerService partners;
    private final QuestionService questions;
    private final CampusResourceTools resources;
    private final IdlePublishDrafts idleDrafts;

    @Override
    public List<AgentTool> tools() {
        return List.of(
                tool(Kind.LOST, "生成寻物启事或拾物招领草稿。type 为 0 寻物、1 招领，未知时省略。"
                        + "happenTime 只接收学生已明确的完整日期和时刻 yyyy-MM-dd HH:mm:ss。"),
                tool(Kind.PARTNER, "生成学习搭子发布草稿。subject 是科目，goal 是目标，schedule 是可搭时间，intro 是介绍。"),
                tool(Kind.QUESTION, "生成校园互助提问草稿。title 是问题，content 是详细说明，category 仅可选课程、考试、技术、生活、其他。"),
                tool(Kind.ANSWER, "为学生明确指定的现有问答生成回答草稿。questionId 必须来自本轮问答搜索，"
                        + "或学生给出的 /qa/detail/{id} 链接。content 填写学生提供的回答内容，未提供可留空。")
        );
    }

    private AgentTool tool(Kind kind, String description) {
        Map<String, String> fields = new LinkedHashMap<>();
        kind.textLimits.forEach((key, limit) -> fields.put(key, "string"));
        if (kind == Kind.LOST) fields.put("type", "integer");
        if (kind == Kind.ANSWER) fields.put("questionId", "integer");
        return new AgentTool(PREFIX + kind.code, description + " 只生成可编辑卡片，由学生确认提交。"
                + "请省略缺失的可选内容。", fields,
                List.of(kind == Kind.ANSWER ? "questionId" : kind.required), (uid, args) -> prepare(uid, kind, args));
    }

    static boolean handles(String tool) {
        return tool.startsWith(PREFIX);
    }

    static boolean isAnswerTool(String tool) {
        return (PREFIX + Kind.ANSWER.code).equals(tool);
    }

    static String explicitTool(String question) {
        if (Pattern.compile("(?:发表|提交|发布|帮我发|写)(?:一条|一个)?(?:.{0,12})?(?:回答|回复)").matcher(question).find()
                && (question.contains("问答") || question.contains("/qa/detail/"))) return PREFIX + Kind.ANSWER.code;
        if (!Pattern.compile("发布|发一条|发个|刊登").matcher(question).find()) return null;
        if (Pattern.compile("失物招领|寻物启事|拾物招领").matcher(question).find()) return PREFIX + Kind.LOST.code;
        if (question.contains("学习搭子") || question.contains("搭子招募")) return PREFIX + Kind.PARTNER.code;
        if (Pattern.compile("互助问答|互助提问|校园问答").matcher(question).find()) return PREFIX + Kind.QUESTION.code;
        return null;
    }

    private JsonNode prepare(Long uid, Kind kind, JsonNode args) {
        ObjectNode form = normalize(kind, args, false);
        Long target = null;
        String targetTitle = null;
        if (kind == Kind.ANSWER) {
            target = args.path("questionId").asLong();
            if (target <= 0) throw invalid("请选择要回答的问题");
            var cards = resources.cards(List.of(new CampusResourceTools.Ref("qa", target)));
            if (cards.isEmpty()) throw new BizException(ResultCode.NOT_FOUND, "问题不存在或已下架");
            targetTitle = cards.get(0).title();
        }
        String id = UUID.randomUUID().toString();
        drafts.insertDraft(id, uid, kind.code, form.toString(), target, targetTitle);
        return json.valueToTree(find(uid, id));
    }

    Draft find(Long uid, String id) {
        return query(uid, id, false);
    }

    @Transactional
    public Draft confirm(Long uid, String id, JsonNode input) {
        var draft = query(uid, id, true);
        if (draft == null) throw new BizException(ResultCode.NOT_FOUND, "未找到发布草稿");
        if (draft.submittedId() != null) return draft;
        if ("EXPIRED".equals(draft.state())) throw invalid("草稿已过期，请在对话中重新生成");
        Kind kind = Kind.valueOf(draft.kind().toUpperCase(Locale.ROOT));
        ObjectNode form = normalize(kind, input, true);
        Long submitted = switch (kind) {
            case LOST -> publishLost(uid, form, input.path("imageIds"));
            case PARTNER -> partners.publish(uid, json.convertValue(form, StudyPartnerDTO.class)).getId();
            case QUESTION -> questions.publish(uid, json.convertValue(form, QuestionDTO.class)).getId();
            case ANSWER -> questions.answer(uid, draft.targetId(), json.convertValue(form, AnswerDTO.class)).getId();
        };
        drafts.markSubmitted(id, form.toString(), submitted);
        return query(uid, id, false);
    }

    private Long publishLost(Long uid, ObjectNode form, JsonNode imageIds) {
        if (imageIds.isMissingNode()) imageIds = json.createArrayNode();
        if (!imageIds.isArray() || imageIds.size() > 6) throw invalid("最多可上传 6 张图片");
        var ids = new ArrayList<Long>();
        for (JsonNode image : imageIds) {
            if (!image.isIntegralNumber() || !image.canConvertToLong() || image.asLong() <= 0) {
                throw invalid("图片资源无效");
            }
            ids.add(image.asLong());
        }
        var dto = json.convertValue(form, LostFoundPublishDTO.class);
        dto.setImages(idleDrafts.ownedImages(uid, ids));
        return lostFound.publish(uid, dto).getId();
    }

    private ObjectNode normalize(Kind kind, JsonNode input, boolean confirming) {
        if (input == null || !input.isObject()) throw invalid("请填写草稿内容");
        var form = json.createObjectNode();
        kind.textLimits.forEach((key, limit) -> {
            JsonNode value = input.path(key);
            if (value.isMissingNode() || value.isNull()) return;
            if (!value.isTextual() || value.asText().strip().length() > limit) throw invalid("草稿字段格式或长度不正确");
            String text = value.asText().strip();
            if (!text.isEmpty()) form.put(key, text);
        });
        if ((confirming || kind != Kind.ANSWER) && !form.has(kind.required)) {
            throw invalid("请补充标题、科目或回答内容");
        }
        if (kind == Kind.LOST) {
            JsonNode type = input.path("type");
            if (type.isIntegralNumber() && (type.asLong() == 0 || type.asLong() == 1)) {
                form.put("type", type.asInt());
            } else if (confirming) {
                throw invalid("请选择寻物启事或拾物招领");
            }
            if (form.has("happenTime")) {
                try {
                    LocalDateTime.parse(form.path("happenTime").asText(), TIME);
                } catch (DateTimeParseException e) {
                    if (confirming) throw invalid("请检查丢失或拾获时间");
                    form.remove("happenTime");
                }
            }
        }
        if (kind == Kind.QUESTION && form.has("category") && !CATEGORIES.contains(form.path("category").asText())) {
            if (confirming) throw invalid("请选择有效的问题分类");
            form.remove("category");
        }
        return form;
    }

    private Draft query(Long uid, String id, boolean lock) {
        var row = lock ? drafts.lockDraft(id, uid) : drafts.findDraft(id, uid);
        if (row == null) {
            return null;
        }
        try {
            return new Draft(row.id(), row.kind(), json.readTree(row.form()), row.targetId(), row.targetTitle(),
                    row.submittedId(), row.state(), row.auditStatus());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("校园发布草稿内容无效", e);
        }
    }

    private static BizException invalid(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    private enum Kind {
        LOST("lost", "title", Map.of("title", 64, "description", 2000, "location", 128, "contact", 64, "happenTime", 19)),
        PARTNER("partner", "subject", Map.of("subject", 64, "goal", 128, "schedule", 255, "intro", 500, "contact", 64)),
        QUESTION("question", "title", Map.of("title", 128, "content", 4000, "category", 32)),
        ANSWER("answer", "content", Map.of("content", 4000));

        final String code;
        final String required;
        final Map<String, Integer> textLimits;

        Kind(String code, String required, Map<String, Integer> textLimits) {
            this.code = code;
            this.required = required;
            this.textLimits = textLimits;
        }
    }

    public record Draft(String id, String kind, JsonNode form, Long targetId, String targetTitle,
                        Long submittedId, String state, Integer auditStatus) {}
}
