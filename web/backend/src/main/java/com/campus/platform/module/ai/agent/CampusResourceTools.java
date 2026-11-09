package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class CampusResourceTools implements AgentToolSource {
    static final String SEARCH = "search_campus_resources";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

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
        var params = parameters();
        String filter = "";
        if (!keyword.isEmpty()) {
            filter = kind.keywordFilter;
            params.addValue("term", "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (args.has("lostType")) {
            int lostType = args.path("lostType").asInt(-1);
            if (kind != Kind.LOST || (lostType != 0 && lostType != 1)) {
                throw new BizException(ResultCode.BAD_REQUEST, "失物招领类型不合法");
            }
            filter += " AND type=:lostType";
            params.addValue("lostType", lostType);
        }
        var result = json.createObjectNode().put("kind", kind.code);
        var items = result.putArray("items");
        query(kind, filter + " ORDER BY id DESC LIMIT 6", params).forEach(card -> items.addObject()
                .put("id", card.id()).put("title", card.title()).put("subtitle", card.subtitle()).put("status", card.status()));
        return result;
    }

    List<Card> cards(List<Ref> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        var byKind = refs.stream().collect(Collectors.groupingBy(Ref::kind));
        Map<Ref, Card> visible = byKind.entrySet().stream().flatMap(group -> query(Kind.from(group.getKey()),
                        " AND id IN (:ids)", parameters().addValue("ids", group.getValue().stream().map(Ref::id).toList())).stream())
                .collect(Collectors.toMap(card -> new Ref(card.kind(), card.id()), Function.identity()));
        return refs.stream().map(visible::get).filter(Objects::nonNull).toList();
    }

    private List<Card> query(Kind kind, String suffix, MapSqlParameterSource params) {
        return jdbc.query(kind.query + suffix, params, (rs, row) -> new Card(kind.code, rs.getLong(1),
                rs.getString(2), rs.getString(3), rs.getInt(4), rs.getObject(5, Long.class)));
    }

    private MapSqlParameterSource parameters() {
        return new MapSqlParameterSource().addValue("approved", Constants.AUDIT_PASS)
                .addValue("active", Constants.LF_DOING).addValue("qaOff", Constants.QA_OFF);
    }

    private enum Kind {
        LOST("lost", "SELECT id,title,location,type,NULL FROM lost_found WHERE audit_status=:approved AND status=:active",
                " AND (title LIKE :term ESCAPE '!' OR description LIKE :term ESCAPE '!' OR location LIKE :term ESCAPE '!')"),
        PARTNER("partner", "SELECT id,subject,goal,status,user_id FROM study_partner WHERE audit_status=:approved AND status=:active",
                " AND (subject LIKE :term ESCAPE '!' OR goal LIKE :term ESCAPE '!' OR intro LIKE :term ESCAPE '!')"),
        QA("qa", "SELECT id,title,category,status,NULL FROM campus_question WHERE status<>:qaOff",
                " AND (title LIKE :term ESCAPE '!' OR content LIKE :term ESCAPE '!')");

        final String code;
        final String query;
        final String keywordFilter;

        Kind(String code, String query, String keywordFilter) {
            this.code = code;
            this.query = query;
            this.keywordFilter = keywordFilter;
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
