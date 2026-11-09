package com.campus.platform.module.ai.rag;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.agent.AgentTool;
import com.campus.platform.module.ai.agent.AgentToolSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.agent.rag.enabled", havingValue = "true")
class KnowledgeAgentTools implements AgentToolSource {
    private final KnowledgeService knowledge;
    private final ObjectMapper json;

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool("search_knowledge",
                "查找校园规章、办事指南、开放时间等有来源的资料，以及学生有权查看的已索引 PDF。query 填待检索的问题。",
                Map.of("query", "string", "documentId", "integer"), List.of("query"), (uid, args, deadline) -> {
                    String query = args.path("query").asText().strip();
                    if (query.isEmpty() || query.length() > 500) {
                        throw new BizException(ResultCode.BAD_REQUEST, "知识库查询问题须为 1 至 500 字符");
                    }
                    var sources = knowledge.search(uid, args.has("documentId") ? args.path("documentId").asLong() : null,
                            query, deadline);
                    var result = json.createObjectNode();
                    var items = result.putArray("sources");
                    sources.forEach(source -> items.addObject().put("documentId", source.documentId())
                            .put("fileName", source.fileName()).put("page", source.page()).put("text", source.text())
                            .put("version", source.version()));
                    return result;
                }));
    }
}
