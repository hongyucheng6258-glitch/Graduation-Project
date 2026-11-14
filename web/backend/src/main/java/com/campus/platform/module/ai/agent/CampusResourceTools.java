package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.platform.module.ai.mapper.CampusResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class CampusResourceTools implements AgentToolSource {
    static final String SEARCH = "search_campus_resources";
    private static final Pattern LOOKUP = Pattern.compile("^(?:请\\s*)?(?:帮我\\s*|我想\\s*)?(?:查|搜|找|看看|有没有|有人捡到|有人拾到)");
    private static final Pattern MATERIAL_LOOKUP = Pattern.compile(
            "^(?:请\\s*)?(?:帮我\\s*|我想\\s*)?(?:查阅|查询|查找|查|搜索|检索|搜|找)\\s*(?:一下\\s*)?"
                    + "(?:校园)?(?:公共)?(?:知识库|资料)");

    private final CampusResourceMapper resources;
    private final ObjectMapper json;

    static String explicitLookupKind(String question) {
        if (!LOOKUP.matcher(question).find() || MATERIAL_LOOKUP.matcher(question).find()) return null;
        boolean lost = question.contains("失物招领") || question.contains("寻物启事");
        boolean partner = question.contains("学习搭子");
        boolean qa = question.contains("互助问答") || question.contains("校园问答");
        if ((lost ? 1 : 0) + (partner ? 1 : 0) + (qa ? 1 : 0) != 1) return null;
        if (lost) return Kind.LOST.code;
        if (partner) return Kind.PARTNER.code;
        return Kind.QA.code;
    }

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool(SEARCH,
                "查找当前可见的失物招领、学习搭子或校园互助问答。kind 必填：lost、partner 或 qa。"
                        + "keyword 传一个最有辨识度的物品、地点、科目或问题关键词，可省略查看近期记录。"
                        + "lostType 仅适用于 lost，明确问丢失信息时用0，找拾获信息时用1，否则省略。",
                Map.of("kind", "string", "keyword", "string", "lostType", "integer"), List.of("kind"), this::search));
    }

    private JsonNode search(Long uid, JsonNode args) {
        Kind kind = Kind.from(args.path("kind").asText());
        String keyword = args.path("keyword").asText("").strip();
        if (keyword.length() > 80) throw new BizException(ResultCode.BAD_REQUEST, "校园搜索关键词过长");
        String term = keyword.isEmpty() ? null
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Long lostType = null;
        if (args.has("lostType")) {
            lostType = args.path("lostType").asLong(-1);
            if (kind != Kind.LOST || (lostType != 0 && lostType != 1)) {
                throw new BizException(ResultCode.BAD_REQUEST, "失物招领类型不合法");
            }
        }
        var result = json.createObjectNode().put("kind", kind.code);
        var items = result.putArray("items");
        query(kind, term, lostType, null).forEach(card -> items.addObject()
                .put("id", card.id()).put("title", card.title()).put("subtitle", card.subtitle()).put("status", card.status()));
        return result;
    }

    List<Card> cards(List<Ref> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        var byKind = refs.stream().collect(Collectors.groupingBy(Ref::kind));
        Map<Ref, Card> visible = byKind.entrySet().stream()
                .flatMap(group -> query(Kind.from(group.getKey()), null, null,
                        group.getValue().stream().map(Ref::id).toList()).stream())
                .collect(Collectors.toMap(card -> new Ref(card.kind(), card.id()), Function.identity()));
        return refs.stream().map(visible::get).filter(Objects::nonNull).toList();
    }

    private List<Card> query(Kind kind, String term, Long lostType, List<Long> ids) {
        var rows = switch (kind) {
            case LOST -> resources.findLost(Constants.AUDIT_PASS, Constants.LF_DOING, term, lostType, ids);
            case PARTNER -> resources.findPartners(Constants.AUDIT_PASS, Constants.LF_DOING, term, ids);
            case QA -> resources.findQuestions(Constants.QA_OFF, term, ids);
        };
        return rows.stream().map(row ->
                new Card(kind.code, row.id(), row.title(), row.subtitle(), row.status(), row.profileId())).toList();
    }

    private enum Kind {
        LOST("lost"), PARTNER("partner"), QA("qa");

        final String code;

        Kind(String code) {
            this.code = code;
        }

        static Kind from(String code) {
            return switch (code) {
                case "lost" -> LOST;
                case "partner" -> PARTNER;
                case "qa" -> QA;
                default -> throw new BizException(ResultCode.BAD_REQUEST, "未知的校园资源类别");
            };
        }
    }

    record Ref(String kind, long id) {}
    record Card(String kind, long id, String title, String subtitle, int status, Long profileId) {}
}
