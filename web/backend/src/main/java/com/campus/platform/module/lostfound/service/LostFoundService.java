package com.campus.platform.module.lostfound.service;

import com.campus.platform.module.lostfound.dto.ClaimDTO;
import com.campus.platform.module.lostfound.dto.LostFoundPublishDTO;
import com.campus.platform.module.lostfound.entity.LostFoundClaim;
import com.campus.platform.module.lostfound.mapper.LostFoundClaimMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.lostfound.vo.ClaimVO;
import com.campus.platform.module.lostfound.vo.LostFoundVO;
import com.campus.platform.module.lostfound.entity.LostFound;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.message.service.MessageService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 失物招领服务（C4）：发布→审核→检索→认领标记完成。
 */
@Service
@RequiredArgsConstructor
public class LostFoundService {

    private final LostFoundMapper lostFoundMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ContentAiAuditService contentAiAuditService;
    private final LostFoundClaimMapper claimMapper;
    private final MessageService messageService;

    /** 发布（待审核） */
    public LostFound publish(Long userId, LostFoundPublishDTO dto) {
        if (sensitiveWordService.contains(dto.getTitle()) || sensitiveWordService.contains(dto.getDescription())) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        LostFound lf = new LostFound();
        BeanUtil.copyProperties(dto, lf);
        lf.setUserId(userId);
        lf.setImages(IdleService.toJson(dto.getImages()));
        lf.setAuditStatus(Constants.AUDIT_PENDING);
        lf.setStatus(Constants.LF_DOING);
        lostFoundMapper.insert(lf);
        contentAiAuditService.audit(Constants.BIZ_LOSTFOUND, lf, userId, dto.getTitle(), dto.getDescription());
        return lf;
    }

    /** 列表检索（公开，仅审核通过，type 筛选） */
    public PageResult<LostFoundVO> list(Integer type, String keyword, int pageNum, int pageSize) {
        Page<LostFound> page = lostFoundMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<LostFound>()
                        .eq(LostFound::getAuditStatus, Constants.AUDIT_PASS)
                        .eq(LostFound::getStatus, Constants.LF_DOING)
                        .eq(type != null, LostFound::getType, type)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(LostFound::getTitle, keyword)
                                .or().like(LostFound::getDescription, keyword))
                        .orderByDesc(LostFound::getId));
        return PageResult.of(page, this::toVO);
    }

    /** 详情 */
    public LostFoundVO detail(Long id, Long currentUid) {
        LostFound lf = lostFoundMapper.selectById(id);
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "信息不存在");
        }
        boolean isOwner = currentUid != null && currentUid.equals(lf.getUserId());
        if (lf.getAuditStatus() != Constants.AUDIT_PASS && !isOwner) {
            throw new BizException(ResultCode.AUDIT_PENDING);
        }
        LostFoundVO vo = toVO(lf);
        vo.setIsOwner(isOwner);
        return vo;
    }

    /** 编辑（仅发布者本人；已完成不可编辑；编辑后重新进入 AI 审核） */
    public LostFound update(Long userId, Long id, LostFoundPublishDTO dto) {
        LostFound lf = lostFoundMapper.selectById(id);
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "信息不存在");
        }
        if (!lf.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能编辑自己发布的信息");
        }
        if (lf.getStatus() == Constants.LF_DONE) {
            throw new BizException(ResultCode.BAD_REQUEST, "已完成的失物信息不可编辑");
        }
        if (sensitiveWordService.contains(dto.getTitle()) || sensitiveWordService.contains(dto.getDescription())) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        lf.setType(dto.getType());
        lf.setTitle(dto.getTitle());
        lf.setDescription(dto.getDescription());
        lf.setLocation(dto.getLocation());
        lf.setHappenTime(dto.getHappenTime());
        lf.setContact(dto.getContact());
        lf.setImages(IdleService.toJson(dto.getImages()));
        lf.setAuditStatus(Constants.AUDIT_PENDING);
        lf.setAuditReason(null);
        lostFoundMapper.updateById(lf);
        contentAiAuditService.audit(Constants.BIZ_LOSTFOUND, lf, userId, dto.getTitle(), dto.getDescription());
        return lf;
    }

    /**
     * 申请认领（仅招领信息，失主申请；发布者不能申请自己的）。
     * R3 复查修复：以父行 FOR UPDATE 作为事务内<b>第一条语句</b>（不再先普通 SELECT 建快照），
     * 有效认领数用 SELECT ... FOR UPDATE 当前读——拿到父行锁后，前一个并发事务的认领
     * 已提交，当前读必然可见，彻底消除 REPEATABLE READ 快照窗口。
     */
    @Transactional
    public LostFoundClaim claim(Long userId, Long lfId, ClaimDTO dto) {
        // 1) 第一时间锁父行：同一招领信息上的并发申请在此排队，且这是当前读
        LostFound lf = lostFoundMapper.selectByIdForUpdate(lfId);
        if (lf == null || lf.getAuditStatus() != Constants.AUDIT_PASS || lf.getStatus() != Constants.LF_DOING) {
            throw new BizException(ResultCode.NOT_FOUND, "招领信息不存在或已处理");
        }
        if (lf.getType() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "只有招领信息可以申请认领");
        }
        if (lf.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能认领自己发布的招领信息");
        }
        // 2) 当前读统计有效认领（FOR UPDATE 读已提交版本，不走事务快照）
        long active = claimMapper.countActiveForUpdate(lfId);
        if (active > 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该信息已有待处理的认领申请");
        }
        LostFoundClaim claim = new LostFoundClaim();
        claim.setLostFoundId(lfId);
        claim.setClaimUserId(userId);
        claim.setMessage(dto == null ? null : dto.getMessage());
        claim.setContact(dto == null ? null : dto.getContact());
        claim.setStatus(0);
        claimMapper.insert(claim);
        messageService.send(lf.getUserId(), Constants.MSG_INTERACT, "收到新的认领申请",
                String.format("有人申请认领你发布的「%s」，请及时核实处理。", lf.getTitle()),
                Constants.BIZ_LOSTFOUND, lfId);
        messageService.send(userId, Constants.MSG_INTERACT, "认领申请已提交",
                String.format("你对「%s」的认领申请已提交，等待发布者核实。", lf.getTitle()),
                Constants.BIZ_LOSTFOUND, lfId);
        return claim;
    }

    /** 认领申请列表（仅发布者可见；第8项修复：分页返回） */
    public PageResult<ClaimVO> claims(Long userId, Long lfId, int pageNum, int pageSize) {
        LostFound lf = lostFoundMapper.selectById(lfId);
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "信息不存在");
        }
        if (!lf.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能查看自己发布信息的认领申请");
        }
        Page<LostFoundClaim> page = claimMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<LostFoundClaim>()
                        .eq(LostFoundClaim::getLostFoundId, lfId)
                        .orderByDesc(LostFoundClaim::getId));
        return PageResult.of(page, c -> {
            ClaimVO vo = new ClaimVO();
            BeanUtil.copyProperties(c, vo);
            User u = userMapper.selectById(c.getClaimUserId());
            vo.setClaimNickname(u == null ? "" : u.getNickname());
            vo.setClaimAvatar(u == null ? null : u.getAvatar());
            return vo;
        });
    }

    /**
     * 处理认领申请（发布者同意/拒绝；同意后该信息其他待确认申请自动拒绝）。
     * P1 第6项修复：条件更新（WHERE status=0）原子化「同意/拒绝」，
     * 并发双击只生效一次；同意后其余待确认申请用一条 UPDATE 原子驳回，避免逐条读改写。
     */
    @Transactional
    public void handleClaim(Long userId, Long claimId, boolean accept) {
        LostFoundClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(ResultCode.NOT_FOUND, "认领申请不存在");
        }
        // R3：与 claim() 同一锁顺序——先锁父行，再做状态机转移
        LostFound lf = lostFoundMapper.selectByIdForUpdate(claim.getLostFoundId());
        if (lf == null || !lf.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有发布者可以处理认领申请");
        }
        int affected = claimMapper.updateStatusIfPending(claimId, accept ? 1 : 2);
        if (affected == 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该申请已处理");
        }
        if (accept) {
            // 同信息其他待确认申请自动拒绝（原子批量）
            claimMapper.rejectOtherPending(claim.getLostFoundId(), claimId);
        }
        messageService.send(claim.getClaimUserId(), Constants.MSG_INTERACT,
                accept ? "认领申请已通过" : "认领申请未通过",
                String.format("你对「%s」的认领申请%s。", lf.getTitle(),
                        accept ? "已通过，请联系发布者线下归还" : "未通过"),
                Constants.BIZ_LOSTFOUND, claim.getLostFoundId());
    }

    /** 当前用户对该信息的认领申请（无则返回 null；多次申请时取最新一条） */
    public ClaimVO myClaim(Long userId, Long lfId) {
        LostFoundClaim claim = claimMapper.selectOne(new LambdaQueryWrapper<LostFoundClaim>()
                .eq(LostFoundClaim::getLostFoundId, lfId)
                .eq(LostFoundClaim::getClaimUserId, userId)
                .orderByDesc(LostFoundClaim::getId)
                .last("LIMIT 1"));
        if (claim == null) {
            return null;
        }
        ClaimVO vo = new ClaimVO();
        BeanUtil.copyProperties(claim, vo);
        User u = userMapper.selectById(claim.getClaimUserId());
        vo.setClaimNickname(u == null ? "" : u.getNickname());
        vo.setClaimAvatar(u == null ? null : u.getAvatar());
        return vo;
    }

    /**
     * 失主确认已找回（认领申请者确认；完成后招领信息标记完成）。
     * P1 第6项修复：条件更新（仅 status=1 可转 3）+ 条件更新招领状态，防并发重复完成。
     */
    @Transactional
    public void confirmReturn(Long userId, Long claimId) {
        LostFoundClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(ResultCode.NOT_FOUND, "认领申请不存在");
        }
        if (!claim.getClaimUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有认领申请者可以确认找回");
        }
        // 快速拒绝已完成/已拒绝的申请，避免无意义的父行查询；最终仍由条件更新做并发裁决。
        if (claim.getStatus() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "认领申请未处于已同意状态");
        }
        // R3/R7：所有认领状态变更统一先锁父行，再更新子行，避免与 claim/handleClaim 反向持锁。
        LostFound lf = lostFoundMapper.selectByIdForUpdate(claim.getLostFoundId());
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "失物信息不存在");
        }
        int affected = claimMapper.updateReturnedIfAgreed(claimId);
        if (affected == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "认领申请未处于已同意状态");
        }
        lf.setStatus(Constants.LF_DONE);
        lostFoundMapper.updateById(lf);
        messageService.send(lf.getUserId(), Constants.MSG_INTERACT, "失物已确认找回",
                String.format("「%s」已被失主确认找回，感谢你的帮助！", lf.getTitle()),
                Constants.BIZ_LOSTFOUND, lf.getId());
    }

    /** 标记完成（仅本人） */
    public void finish(Long userId, Long id) {
        LostFound lf = lostFoundMapper.selectById(id);
        if (lf == null) {
            throw new BizException(ResultCode.NOT_FOUND, "信息不存在");
        }
        if (!lf.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己发布的信息");
        }
        lf.setStatus(Constants.LF_DONE);
        lostFoundMapper.updateById(lf);
    }

    /** 我的发布 */
    public PageResult<LostFoundVO> myList(Long userId, int pageNum, int pageSize) {
        Page<LostFound> page = lostFoundMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<LostFound>()
                        .eq(LostFound::getUserId, userId)
                        .orderByDesc(LostFound::getId));
        return PageResult.of(page, this::toVO);
    }

    private LostFoundVO toVO(LostFound lf) {
        LostFoundVO vo = new LostFoundVO();
        BeanUtil.copyProperties(lf, vo);
        vo.setImageList(IdleService.parseJson(lf.getImages()));
        User publisher = userMapper.selectById(lf.getUserId());
        vo.setPublisherNickname(publisher == null ? "" : publisher.getNickname());
        vo.setPublisherAvatar(publisher == null ? null : publisher.getAvatar());
        return vo;
    }
}
