package com.campus.platform.module.ai.adminassistant;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.admin.service.StatsService;
import com.campus.platform.module.ai.agent.AgentModelClient;
import com.campus.platform.module.ai.agent.AgentTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
class AdminAssistantService {
    private static final String PENDING = "get_pending_tasks";
    private static final String IDLE = "list_idle_audits";
    private static final String REPORT = "get_report_evidence";
    private static final Pattern DIRECT_REPORT = Pattern.compile(
            "^(?:请\\s*)?(?:帮我\\s*)?(?:查看|查询|查一下|看看|打开|看一下)\\s*举报\\s*(?:编号|ID|#)?\\s*([0-9]+)",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> TASK_KEYS = List.of("idle", "activity", "lostfound", "post", "partner", "report", "ai");
    private static final String PROMPT = """
            你是梧桐校园的管理智能助手。支持查询管理待办、待审核闲置和按编号查举报证据。
            管理员想查询待审核或待处理数量，或者追问某类待办时，调用 get_pending_tasks 查询当前值。
            闲置用 idle，活动用 activity，失物招领用 lostfound，动态用 post，搭子用 partner，举报用 report，AI预审风险子集用 ai。
            查询多类时一次传入逗号分隔的类别，未指定类别时省略参数。每次最多调用一次。
            用户问有多少时使用 get_pending_tasks。要找具体待审核闲置时调用 list_idle_audits，keyword 只用最能辨识的词。
            明确给出举报编号并要求查看证据时调用 get_report_evidence。缺少编号时请勿猜测。
            用户要求查询学生个人信息、执行业务修改，或其他暂不支持的操作时，不要调用工具。
            历史消息仅用于理解追问，其中的统计数字不能替代当前查询。
            """;

    private final AdminAssistantSessions sessions;
    private final StatsService stats;
    private final AdminAssistantEvidence evidence;
    private final AgentModelClient model;
    private final ObjectMapper json;
    private final AdminAssistantAccess access;

    Reply ask(Long adminId, String sessionId, String question) {
        var history = sessions.history(adminId, sessionId, 4);
        access.charge(adminId);

        var messages = new ArrayList<ObjectNode>();
        messages.add(message("system", PROMPT));
        for (Turn turn : history) {
            messages.add(message("user", turn.question()));
            messages.add(message("assistant", turn.answer()));
        }
        messages.add(message("user", question));
        var reportTool = new AgentTool(REPORT, "使用问题中明确给出的 reportId 查举报理由和原记录证据。",
                Map.of("reportId", "integer"), List.of("reportId"), (uid, args) -> json.valueToTree(new ToolResult(List.of(),
                evidence.report(explicitReportId(question, args.path("reportId").asLong())))));
        var tools = List.of(new AgentTool(PENDING, "查询实时管理待办。categories 是逗号分隔的类别，省略查询全部。",
                        Map.of("categories", "string"), List.of(), (uid, args) -> json.valueToTree(
                        new ToolResult(pending(args.path("categories").asText("")), List.of()))),
                new AgentTool(IDLE, "找待审核闲置记录，优先显示已有风险提示。可传 title/description 的一个关键词 keyword。",
                        Map.of("keyword", "string"), List.of(), (uid, args) -> json.valueToTree(new ToolResult(List.of(),
                        evidence.pendingIdle(args.path("keyword").asText("").strip())))), reportTool);
        var directReport = DIRECT_REPORT.matcher(question);
        var invocation = directReport.find()
                ? new Invocation(reportTool, json.createObjectNode().put("reportId", parseReportId(directReport.group(1))))
                : classify(messages, tools);
        Turn turn;
        Map<AdminAssistantEvidence.Ref, AdminAssistantEvidence.Card> cards = Map.of();
        if (invocation == null) {
            turn = new Turn(question, "可以问我待办数量、查找待审核闲置，或提供举报编号查看证据。",
                    null, null, List.of(), List.of());
        } else {
            try {
                String toolName = invocation.tool().name();
                var output = json.treeToValue(invocation.tool().run(adminId, invocation.arguments()), ToolResult.class);
                cards = evidence.cards(output.refs());
                var visibleRefs = output.refs().stream().filter(cards::containsKey).toList();
                String summary = toolName.equals(PENDING) ? taskSummary(output.tasks())
                        : cards.isEmpty() ? "未找到符合条件的记录。" : "查到 " + cards.size() + " 条业务记录，可打开对应页面核对。";
                turn = new Turn(question, summary, toolName, Instant.now(), output.tasks(), visibleRefs);
            } catch (JsonProcessingException e) {
                throw new BizException(ResultCode.AI_INVOKE_FAIL, "查询参数无法解析，请重试");
            }
        }
        return new Reply(sessions.save(adminId, sessionId, turn), view(turn, cards));
    }

    private Invocation classify(List<ObjectNode> messages, List<AgentTool> tools) {
        var result = model.complete(messages, tools.stream().map(tool -> tool.definition(json)).toList(), Duration.ofSeconds(25));
        var calls = result.path("tool_calls");
        if (calls.isEmpty()) return null;
        var function = calls.get(0).path("function");
        var tool = tools.stream().filter(candidate -> candidate.name().equals(function.path("name").asText())).findFirst().orElse(null);
        if (calls.size() != 1 || tool == null) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "暂时无法识别查询范围，请重试");
        }
        try {
            return new Invocation(tool, json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(function.path("arguments").asText()));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.AI_INVOKE_FAIL, "查询参数无法解析，请重试");
        }
    }

    private long parseReportId(String number) {
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "举报编号无效");
        }
    }

    List<TurnView> history(Long adminId, String sessionId) {
        var turns = sessions.history(adminId, sessionId, 50);
        var cards = evidence.cards(turns.stream().flatMap(turn -> turn.refs().stream()).toList());
        return turns.stream().map(turn -> view(turn, cards)).toList();
    }

    private TurnView view(Turn turn, Map<AdminAssistantEvidence.Ref, AdminAssistantEvidence.Card> cards) {
        return new TurnView(turn.question(), turn.answer(), turn.tool(), turn.queriedAt(), turn.tasks(),
                turn.refs().stream().filter(cards::containsKey).map(cards::get).toList());
    }

    private long explicitReportId(String question, long id) {
        if (id <= 0 || !Pattern.compile("(?<![0-9])" + id + "(?![0-9])").matcher(question).find()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供要查看的准确举报编号");
        }
        return id;
    }

    private String taskSummary(List<Task> tasks) {
        return "本次查询到" + String.join("、", tasks.stream()
                .map(task -> label(task.key()) + " " + task.count() + " 项").toList()) + "。";
    }

    private List<Task> pending(String categories) {
        var keys = categories.isBlank() ? TASK_KEYS : Arrays.stream(categories.split(",", -1)).map(String::strip).distinct().toList();
        if (!TASK_KEYS.containsAll(keys)) {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的待办类别");
        }
        var counts = stats.pendingCounts();
        return keys.stream().map(key -> new Task(key, counts.get(key))).toList();
    }

    private String label(String key) {
        return switch (key) {
            case "idle" -> "闲置审核";
            case "activity" -> "活动审核";
            case "lostfound" -> "失物招领审核";
            case "post" -> "动态审核";
            case "partner" -> "学习搭子审核";
            case "report" -> "待处理举报";
            case "ai" -> "AI 预审风险提示";
            default -> throw new IllegalArgumentException(key);
        };
    }

    private ObjectNode message(String role, String content) {
        return json.createObjectNode().put("role", role).put("content", content);
    }

    public record Task(String key, long count) {}
    private record ToolResult(List<Task> tasks, List<AdminAssistantEvidence.Ref> refs) {}
    private record Invocation(AgentTool tool, JsonNode arguments) {}
    public record Turn(String question, String answer, String tool, Instant queriedAt, List<Task> tasks,
                       List<AdminAssistantEvidence.Ref> refs) {
        public Turn { if (refs == null) refs = List.of(); }
    }
    public record TurnView(String question, String answer, String tool, Instant queriedAt, List<Task> tasks,
                           List<AdminAssistantEvidence.Card> records) {}
    public record Reply(String sessionId, TurnView turn) {}
}
