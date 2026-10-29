package com.campus.platform.module.lostfound.service;

import cn.hutool.core.util.StrUtil;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.lostfound.vo.LostMatchVO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 失物招领 AI 智能匹配：发布"丢失"信息时，把库中的"拾到"记录交给 AI
 * 综合物品名称/特征/关键词/地点/时间判断相似度，命中即提示"可能找到了！"。
 */
@Service
@RequiredArgsConstructor
public class LostMatchService {

    private static final Logger log = LoggerFactory.getLogger(LostMatchService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final int MAX_CANDIDATES = 20;

    private final LostFoundMapper lostFoundMapper;
    private final UserMapper userMapper;
    private final AiGatewayService aiGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 智能匹配：返回可能的拾到记录（最多 3 条，AI 排序） */
    public List<LostMatchVO> match(Long userId, String title, String description) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        // 1. 检索进行中的"拾到"记录（审核通过）
        List<LostFound> candidates = lostFoundMapper.selectList(new LambdaQueryWrapper<LostFound>()
                .eq(LostFound::getAuditStatus, Constants.AUDIT_PASS)
                .eq(LostFound::getStatus, Constants.LF_DOING)
                .eq(LostFound::getType, 1)
                .orderByDesc(LostFound::getId)
                .last("LIMIT " + MAX_CANDIDATES));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 构造候选文本
        StringBuilder sb = new StringBuilder();
        for (LostFound c : candidates) {
            sb.append("[id=").append(c.getId()).append("] 标题：").append(nullTo(c.getTitle(), "未命名"))
                    .append(" | 描述：").append(nullTo(c.getDescription(), ""))
                    .append(" | 地点：").append(nullTo(c.getLocation(), "未知"))
                    .append(" | 时间：").append(c.getHappenTime() == null ? "未知" : c.getHappenTime().format(FMT))
                    .append("\n");
        }
        // 3. 调用 AI 判断
        Map<String, String> params = new HashMap<>();
        params.put("candidates", sb.toString());
        params.put("lost_title", nullTo(title, ""));
        params.put("lost_desc", nullTo(description, ""));
        String raw = aiGatewayService.internalChat(userId, Constants.SCENE_LOST_MATCH, "请进行失物匹配", params);
        // 4. 容错解析 JSON -> 按 id 映射候选
        List<Long> matchedIds = new ArrayList<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        if (!parseMatches(raw, matchedIds, reasons)) {
            return Collections.emptyList();
        }
        Map<Long, LostFound> byId = new HashMap<>();
        for (LostFound c : candidates) {
            byId.put(c.getId(), c);
        }
        Map<Long, String> nickCache = new HashMap<>();
        List<LostMatchVO> result = new ArrayList<>();
        for (Long id : matchedIds) {
            if (result.size() >= 3) break;
            LostFound lf = byId.get(id);
            if (lf == null) continue;
            LostMatchVO vo = new LostMatchVO();
            vo.setId(lf.getId());
            vo.setTitle(lf.getTitle());
            vo.setLocation(lf.getLocation());
            vo.setHappenTime(lf.getHappenTime());
            vo.setContact(lf.getContact());
            vo.setReason(reasons.get(id));
            String nick = nickCache.computeIfAbsent(lf.getUserId(), uid -> {
                User u = userMapper.selectById(uid);
                return u == null ? "" : u.getNickname();
            });
            vo.setPublisherNickname(nick);
            result.add(vo);
        }
        return result;
    }

    /** 解析 AI 输出中的匹配 id 与理由（容错 ```围栏 与多余文字） */
    private boolean parseMatches(String raw, List<Long> ids, Map<Long, String> reasons) {
        if (StrUtil.isBlank(raw)) return false;
        String text = raw.trim();
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int brace = text.indexOf('{', fenceStart);
            int braceEnd = text.lastIndexOf('}');
            if (brace >= 0 && braceEnd > brace) {
                text = text.substring(brace, braceEnd + 1);
            }
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            JsonNode arr = node.path("matches");
            if (!arr.isArray()) return false;
            for (JsonNode m : arr) {
                long id = m.path("id").asLong(0);
                if (id <= 0) continue;
                ids.add(id);
                reasons.put(id, m.path("reason").asText(""));
            }
            return !ids.isEmpty();
        } catch (Exception e) {
            log.warn("失物AI匹配 JSON 解析失败，原文: {}", raw);
            return false;
        }
    }

    private String nullTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
    }
}
