package com.campus.platform.module.partner.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.AiGatewayService;
import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.partner.dto.StudyPartnerDTO;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.mapper.StudyPartnerMapper;
import com.campus.platform.module.partner.vo.StudyPartnerVO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 学习搭子服务：发布→审核→浏览→AI匹配撮合 */
@Service
@RequiredArgsConstructor
public class StudyPartnerService {

    private final StudyPartnerMapper partnerMapper;
    private final UserMapper userMapper;
    private final AiGatewayService aiGatewayService;
    private final ContentAiAuditService contentAiAuditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 发布（待审核） */
    public StudyPartner publish(Long userId, StudyPartnerDTO dto) {
        StudyPartner p = new StudyPartner();
        p.setUserId(userId);
        p.setSubject(dto.getSubject().trim());
        p.setGoal(StrUtil.trimToEmpty(dto.getGoal()));
        p.setSchedule(StrUtil.trimToEmpty(dto.getSchedule()));
        p.setIntro(StrUtil.trimToEmpty(dto.getIntro()));
        p.setContact(StrUtil.trimToEmpty(dto.getContact()));
        p.setStatus(Constants.LF_DOING); // 0 匹配中
        p.setAuditStatus(Constants.AUDIT_PENDING);
        partnerMapper.insert(p);
        // AI 分级审核：低风险自动通过，中高风险转人工审核（与其他 UGC 一致）
        contentAiAuditService.audit(Constants.BIZ_PARTNER, p, userId,
                dto.getSubject(), StrUtil.nullToEmpty(dto.getIntro()));
        return partnerMapper.selectById(p.getId());
    }

    /** 列表（公开，仅审核通过） */
    public PageResult<StudyPartnerVO> list(String subject, String keyword, int pageNum, int pageSize) {
        Page<StudyPartner> page = partnerMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<StudyPartner>()
                        .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PASS)
                        .eq(StudyPartner::getStatus, Constants.LF_DOING)
                        .eq(StrUtil.isNotBlank(subject), StudyPartner::getSubject, subject)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(StudyPartner::getSubject, keyword)
                                .or().like(StudyPartner::getGoal, keyword)
                                .or().like(StudyPartner::getIntro, keyword))
                        .orderByDesc(StudyPartner::getId));
        return PageResult.of(page, p -> toVO(p, null));
    }

    /** 我的发布 */
    public PageResult<StudyPartnerVO> myList(Long userId, int pageNum, int pageSize) {
        Page<StudyPartner> page = partnerMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<StudyPartner>()
                        .eq(StudyPartner::getUserId, userId)
                        .orderByDesc(StudyPartner::getId));
        return PageResult.of(page, p -> toVO(p, userId));
    }

    /** 标记已找到（仅本人） */
    public void finish(Long userId, Long id) {
        StudyPartner p = partnerMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "搭子信息不存在");
        }
        if (!p.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己发布的信息");
        }
        p.setStatus(Constants.LF_DONE);
        partnerMapper.updateById(p);
    }

    /** AI 智能匹配：基于我的需求从进行中的搭子中挑选最合适的 */
    public List<StudyPartnerVO> match(Long userId, StudyPartnerDTO dto) {
        if (!aiGatewayService.isAiConfigured()) {
            throw new BizException(ResultCode.AI_NOT_CONFIGURED,
                    "AI 服务暂不可用（未配置 API Key），可稍后重试");
        }
        String subject = dto.getSubject().trim();
        List<StudyPartner> candidates = partnerMapper.selectList(new LambdaQueryWrapper<StudyPartner>()
                .eq(StudyPartner::getAuditStatus, Constants.AUDIT_PASS)
                .eq(StudyPartner::getStatus, Constants.LF_DOING)
                .ne(StudyPartner::getUserId, userId)
                .like(StudyPartner::getSubject, subject.substring(0, Math.min(subject.length(), 2)))
                .last("LIMIT 20"));
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sb = new StringBuilder();
        for (StudyPartner c : candidates) {
            sb.append("[id=").append(c.getId()).append("] 科目：").append(nullTo(c.getSubject(), ""))
                    .append(" | 目标：").append(nullTo(c.getGoal(), ""))
                    .append(" | 可搭时间：").append(nullTo(c.getSchedule(), ""))
                    .append(" | 自我介绍：").append(nullTo(c.getIntro(), ""))
                    .append("\n");
        }
        Map<String, String> params = new HashMap<>();
        params.put("my_subject", subject);
        params.put("my_goal", nullTo(dto.getGoal(), ""));
        params.put("my_schedule", nullTo(dto.getSchedule(), ""));
        params.put("my_intro", nullTo(dto.getIntro(), ""));
        params.put("candidates", sb.toString());
        String raw = aiGatewayService.internalChat(userId, Constants.SCENE_PARTNER_MATCH, "请进行学习搭子匹配", params);

        List<Long> ids = new ArrayList<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        if (!parseMatches(raw, ids, reasons)) {
            return Collections.emptyList();
        }
        Map<Long, StudyPartner> byId = new HashMap<>();
        for (StudyPartner c : candidates) {
            byId.put(c.getId(), c);
        }
        Map<Long, String> nickCache = new HashMap<>();
        List<StudyPartnerVO> result = new ArrayList<>();
        for (Long id : ids) {
            if (result.size() >= 3) break;
            StudyPartner p = byId.get(id);
            if (p == null) continue;
            StudyPartnerVO vo = toVO(p, null);
            vo.setReason(reasons.get(id));
            result.add(vo);
        }
        return result;
    }

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
            return false;
        }
    }

    private StudyPartnerVO toVO(StudyPartner p, Long currentUid) {
        StudyPartnerVO vo = new StudyPartnerVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setSubject(p.getSubject());
        vo.setGoal(p.getGoal());
        vo.setSchedule(p.getSchedule());
        vo.setIntro(p.getIntro());
        vo.setContact(p.getContact());
        vo.setStatus(p.getStatus());
        vo.setCreateTime(p.getCreateTime());
        User u = userMapper.selectById(p.getUserId());
        vo.setPublisherNickname(u == null ? "" : u.getNickname());
        vo.setPublisherAvatar(u == null ? null : u.getAvatar());
        vo.setIsOwner(currentUid != null && currentUid.equals(p.getUserId()));
        return vo;
    }

    private String nullTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
    }
}
