package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.idle.vo.IdleGuideCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class IdleAgentTools implements AgentToolSource {
    static final String SEARCH = "search_idle_items";
    static final String DETAIL = "get_idle_detail";

    private final IdleService idle;
    private final ObjectMapper json;

    @Override
    public List<AgentTool> tools() {
        return List.of(
                new AgentTool(SEARCH, "按物品关键词 keyword、已有分类 category、期望换物关键词 exchange 查找审核通过且在架的校园闲置。参数可省略。",
                        Map.of("keyword", "string", "category", "string", "exchange", "string"), List.of(), this::search),
                new AgentTool(DETAIL, "查看闲置的实时详情。明确知道物品 ID 时传 itemId；用户说第几个时只传 resultNumber（从 1 开始）。",
                        Map.of("itemId", "integer", "resultNumber", "integer"), List.of(), this::detail)
        );
    }

    private JsonNode search(Long uid, JsonNode args) {
        var cards = idle.guideSearch(uid, term(args, "keyword", 80), term(args, "category", 32),
                term(args, "exchange", 80), 6);
        var result = json.createObjectNode();
        var items = result.putArray("items");
        for (int index = 0; index < cards.size(); index++) {
            items.add(project(cards.get(index), false).put("resultNumber", index + 1));
        }
        return result;
    }

    private JsonNode detail(Long uid, JsonNode args) {
        long id = args.path("itemId").asLong();
        if (id <= 0 || args.has("resultNumber")) {
            throw new BizException(ResultCode.BAD_REQUEST, "闲置编号无效");
        }
        return project(idle.guideDetail(uid, id), true);
    }

    private ObjectNode project(IdleGuideCard card, boolean withDescription) {
        var item = json.createObjectNode().put("itemId", card.itemId()).put("title", card.title())
                .put("category", card.category()).put("expectItem", card.expectItem()).put("status", card.status())
                .put("owned", card.owned());
        if (withDescription) {
            item.put("description", card.description());
        }
        return item;
    }

    private String term(JsonNode args, String field, int max) {
        String value = args.path(field).asText("").strip();
        if (value.length() > max) {
            throw new BizException(ResultCode.BAD_REQUEST, "闲置搜索条件过长");
        }
        return value;
    }
}
