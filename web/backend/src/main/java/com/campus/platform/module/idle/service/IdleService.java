package com.campus.platform.module.idle.service;

import com.campus.platform.module.idle.mapper.IdleAppointmentMapper;
import com.campus.platform.module.idle.vo.PendingAppointmentVO;
import com.campus.platform.module.idle.mapper.IdleReviewMapper;
import com.campus.platform.module.idle.entity.IdleAppointment;
import com.campus.platform.module.idle.mapper.IdleItemMapper;
import com.campus.platform.module.idle.dto.IdlePublishDTO;
import com.campus.platform.module.idle.vo.AppointmentVO;
import com.campus.platform.module.idle.vo.IdleDetailVO;
import com.campus.platform.module.idle.dto.AppointDTO;
import com.campus.platform.module.idle.vo.IdleItemVO;
import com.campus.platform.module.idle.entity.IdleReview;
import com.campus.platform.module.idle.entity.IdleItem;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.message.service.MessageService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.post.dto.ReviewDTO;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 闲置互换服务（C1）：发布→审核→预约→确认→互评 闭环。
 */
@Service
@RequiredArgsConstructor
public class IdleService {

    private final IdleItemMapper idleItemMapper;
    private final IdleAppointmentMapper appointmentMapper;
    private final IdleReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final SensitiveWordService sensitiveWordService;
    private final ContentAiAuditService contentAiAuditService;

    /** 发布闲置（进入待审核） */
    public IdleItem publish(Long userId, IdlePublishDTO dto) {
        if (sensitiveWordService.contains(dto.getTitle()) || sensitiveWordService.contains(dto.getDescription())) {
            throw new BizException(ResultCode.SENSITIVE_WORD);
        }
        IdleItem item = new IdleItem();
        item.setUserId(userId);
        item.setTitle(dto.getTitle());
        item.setDescription(dto.getDescription());
        item.setImages(toJson(dto.getImages()));
        item.setExpectItem(dto.getExpectItem());
        item.setCategory(dto.getCategory());
        item.setAuditStatus(Constants.AUDIT_PENDING);
        item.setStatus(Constants.IDLE_ON_SHELF);
        item.setViewCount(0);
        idleItemMapper.insert(item);
        contentAiAuditService.audit(Constants.BIZ_IDLE, item, userId, dto.getTitle(), dto.getDescription());
        return item;
    }

    /** 列表检索（公开，仅审核通过 + 在架） */
    public PageResult<IdleItemVO> list(String keyword, String category, int pageNum, int pageSize) {
        Page<IdleItem> page = idleItemMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<IdleItem>()
                        .eq(IdleItem::getAuditStatus, Constants.AUDIT_PASS)
                        .eq(IdleItem::getStatus, Constants.IDLE_ON_SHELF)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(IdleItem::getTitle, keyword)
                                .or().like(IdleItem::getDescription, keyword))
                        .eq(StrUtil.isNotBlank(category), IdleItem::getCategory, category)
                        .orderByDesc(IdleItem::getId));
        return PageResult.of(page, this::toVO);
    }

    /** 详情（浏览数+1；待审内容仅本人/管理员可见） */
    public IdleDetailVO detail(Long id, Long currentUid) {
        IdleItem item = idleItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物品不存在或已删除");
        }
        boolean isOwner = currentUid != null && currentUid.equals(item.getUserId());
        if (item.getAuditStatus() != Constants.AUDIT_PASS && !isOwner) {
            throw new BizException(ResultCode.AUDIT_PENDING);
        }
        // 浏览数自增
        item.setViewCount(item.getViewCount() + 1);
        idleItemMapper.updateById(item);

        IdleDetailVO vo = new IdleDetailVO();
        BeanUtil.copyProperties(toVO(item), vo);
        vo.setIsOwner(isOwner);
        // 当前用户对该物品的进行中预约
        if (currentUid != null && !isOwner) {
            IdleAppointment my = appointmentMapper.selectOne(new LambdaQueryWrapper<IdleAppointment>()
                    .eq(IdleAppointment::getItemId, id)
                    .eq(IdleAppointment::getBuyerId, currentUid)
                    .in(IdleAppointment::getStatus, Constants.APPOINT_PENDING, Constants.APPOINT_ACCEPTED)
                    .last("LIMIT 1"));
            vo.setMyAppointmentId(my == null ? null : my.getId());
        }
        // 卖家视角：返回进行中的预约（待确认→接受/拒绝，已接受→确认完成）
        if (isOwner) {
            IdleAppointment pending = appointmentMapper.selectOne(new LambdaQueryWrapper<IdleAppointment>()
                    .eq(IdleAppointment::getItemId, id)
                    .in(IdleAppointment::getStatus, Constants.APPOINT_PENDING, Constants.APPOINT_ACCEPTED)
                    .last("LIMIT 1"));
            if (pending != null) {
                User buyer = userMapper.selectById(pending.getBuyerId());
                PendingAppointmentVO pv = new PendingAppointmentVO();
                pv.setAppointmentId(pending.getId());
                pv.setStatus(pending.getStatus());
                pv.setBuyerNickname(buyer == null ? "校园用户" : buyer.getNickname());
                pv.setBuyerAvatar(buyer == null ? null : buyer.getAvatar());
                pv.setMessage(pending.getMessage());
                vo.setPendingAppointment(pv);
            }
        }
        // 当前用户可评价的已完成预约（买/卖双方都可评价）
        if (currentUid != null) {
            IdleAppointment reviewable = appointmentMapper.selectOne(new LambdaQueryWrapper<IdleAppointment>()
                    .eq(IdleAppointment::getItemId, id)
                    .eq(IdleAppointment::getStatus, Constants.APPOINT_FINISHED)
                    .and(w -> w.eq(IdleAppointment::getBuyerId, currentUid)
                            .or()
                            .eq(IdleAppointment::getSellerId, currentUid))
                    .last("LIMIT 1"));
            if (reviewable != null) {
                vo.setReviewAppointmentId(reviewable.getId());
                Long reviewed = reviewMapper.selectCount(new LambdaQueryWrapper<IdleReview>()
                        .eq(IdleReview::getAppointmentId, reviewable.getId())
                        .eq(IdleReview::getFromUserId, currentUid));
                vo.setReviewed(reviewed != null && reviewed > 0);
            }
        }
        // 卖家平均评分
        List<IdleReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<IdleReview>()
                .eq(IdleReview::getToUserId, item.getUserId()));
        vo.setSellerAvgScore(reviews.isEmpty() ? null :
                reviews.stream().mapToInt(IdleReview::getScore).average().orElse(0));
        return vo;
    }

    /** 编辑（仅本人，编辑后重新待审） */
    public IdleItem update(Long userId, Long id, IdlePublishDTO dto) {
        IdleItem item = checkOwner(userId, id);
        item.setTitle(dto.getTitle());
        item.setDescription(dto.getDescription());
        item.setImages(toJson(dto.getImages()));
        item.setExpectItem(dto.getExpectItem());
        item.setCategory(dto.getCategory());
        item.setAuditStatus(Constants.AUDIT_PENDING);
        item.setAuditReason(null);
        idleItemMapper.updateById(item);
        contentAiAuditService.audit(Constants.BIZ_IDLE, item, userId, dto.getTitle(), dto.getDescription());
        return item;
    }

    /** 下架（仅本人） */
    public void offline(Long userId, Long id) {
        IdleItem item = checkOwner(userId, id);
        item.setStatus(Constants.IDLE_OFF_SHELF);
        idleItemMapper.updateById(item);
    }

    /** 重新上架（仅本人；需审核通过且非已成交） */
    public void relist(Long userId, Long id) {
        IdleItem item = checkOwner(userId, id);
        if (item.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.AUDIT_PENDING, "物品未通过审核，无法上架");
        }
        if (item.getStatus() == Constants.IDLE_FINISHED) {
            throw new BizException(ResultCode.BAD_REQUEST, "已成交的物品不可重新上架");
        }
        if (item.getStatus() == Constants.IDLE_ON_SHELF) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "物品已在架上");
        }
        item.setStatus(Constants.IDLE_ON_SHELF);
        idleItemMapper.updateById(item);
    }

    /** 我的发布 */
    public PageResult<IdleItemVO> myList(Long userId, int pageNum, int pageSize) {
        Page<IdleItem> page = idleItemMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<IdleItem>()
                        .eq(IdleItem::getUserId, userId)
                        .orderByDesc(IdleItem::getId));
        return PageResult.of(page, this::toVO);
    }

    /**
     * 发起预约：物品需在架且审核通过，不能预约自己的物品；预约成功后物品标记"已预约"并通知卖家。
     */
    @Transactional
    public IdleAppointment appoint(Long userId, Long itemId, AppointDTO dto) {
        IdleItem item = idleItemMapper.selectById(itemId);
        if (item == null || item.getAuditStatus() != Constants.AUDIT_PASS) {
            throw new BizException(ResultCode.NOT_FOUND, "物品不存在或未通过审核");
        }
        if (item.getUserId().equals(userId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能预约自己发布的物品");
        }
        if (item.getStatus() != Constants.IDLE_ON_SHELF) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该物品已被预约或交易完成");
        }
        int claimed = idleItemMapper.update(null, new UpdateWrapper<IdleItem>()
                .eq("id", itemId)
                .eq("audit_status", Constants.AUDIT_PASS)
                .eq("status", Constants.IDLE_ON_SHELF)
                .set("status", Constants.IDLE_RESERVED));
        if (claimed != 1) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该物品已被其他同学预约，请刷新后重试");
        }

        IdleAppointment appointment = new IdleAppointment();
        appointment.setItemId(itemId);
        appointment.setBuyerId(userId);
        appointment.setSellerId(item.getUserId());
        appointment.setMessage(dto.getMessage());
        appointment.setStatus(Constants.APPOINT_PENDING);
        appointmentMapper.insert(appointment);

        User buyer = userMapper.selectById(userId);
        messageService.send(item.getUserId(), Constants.MSG_INTERACT,
                "你的闲置有新预约",
                String.format("「%s」想与你互换「%s」，请尽快处理。",
                        buyer == null ? "有用户" : buyer.getNickname(), item.getTitle()),
                Constants.BIZ_IDLE, appointment.getItemId());
        messageService.send(userId, Constants.MSG_INTERACT,
                "预约已提交",
                String.format("你对「%s」的预约已提交，等待发布者确认。", item.getTitle()),
                Constants.BIZ_IDLE, appointment.getItemId());
        return appointment;
    }

    /** 卖家接受/拒绝预约 */
    @Transactional
    public void handleAppoint(Long userId, Long appointId, boolean accept) {
        IdleAppointment appointment = getAppointment(appointId);
        if (!appointment.getSellerId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有物品发布者可以处理预约");
        }
        if (appointment.getStatus() != Constants.APPOINT_PENDING) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "该预约已处理");
        }
        appointment.setStatus(accept ? Constants.APPOINT_ACCEPTED : Constants.APPOINT_REJECTED);
        appointmentMapper.updateById(appointment);
        // 拒绝则物品恢复在架
        if (!accept) {
            IdleItem item = idleItemMapper.selectById(appointment.getItemId());
            if (item != null && item.getStatus() == Constants.IDLE_RESERVED) {
                item.setStatus(Constants.IDLE_ON_SHELF);
                idleItemMapper.updateById(item);
            }
        }
        IdleItem item = idleItemMapper.selectById(appointment.getItemId());
        messageService.send(appointment.getBuyerId(), Constants.MSG_INTERACT,
                accept ? "预约已被接受" : "预约已被拒绝",
                String.format("你对「%s」的预约%s。",
                        item == null ? "物品" : item.getTitle(), accept ? "已被接受，请线下完成互换" : "已被拒绝"),
                Constants.BIZ_IDLE, appointment.getItemId());
    }

    /** 双方确认完成（任一一方点击即完成，毕设简化） */
    @Transactional
    public void finishAppoint(Long userId, Long appointId) {
        IdleAppointment appointment = getAppointment(appointId);
        if (!appointment.getBuyerId().equals(userId) && !appointment.getSellerId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该预约");
        }
        if (appointment.getStatus() != Constants.APPOINT_ACCEPTED) {
            throw new BizException(ResultCode.BAD_REQUEST, "预约未处于已接受状态");
        }
        appointment.setStatus(Constants.APPOINT_FINISHED);
        appointmentMapper.updateById(appointment);
        IdleItem item = idleItemMapper.selectById(appointment.getItemId());
        if (item != null) {
            item.setStatus(Constants.IDLE_FINISHED);
            idleItemMapper.updateById(item);
        }
        Long other = appointment.getBuyerId().equals(userId) ? appointment.getSellerId() : appointment.getBuyerId();
        messageService.send(other, Constants.MSG_INTERACT, "互换已完成",
                "一笔闲置互换已确认完成，快去互评吧。", Constants.BIZ_IDLE, appointment.getItemId());
    }

    /** 互评（1-5分+评语，联合唯一索引防重复） */
    public void review(Long userId, Long appointId, ReviewDTO dto) {
        IdleAppointment appointment = getAppointment(appointId);
        if (appointment.getStatus() != Constants.APPOINT_FINISHED) {
            throw new BizException(ResultCode.BAD_REQUEST, "交易完成后才能评价");
        }
        Long toUserId;
        if (appointment.getBuyerId().equals(userId)) {
            toUserId = appointment.getSellerId();
        } else if (appointment.getSellerId().equals(userId)) {
            toUserId = appointment.getBuyerId();
        } else {
            throw new BizException(ResultCode.FORBIDDEN, "无权评价该预约");
        }
        Long exist = reviewMapper.selectCount(new LambdaQueryWrapper<IdleReview>()
                .eq(IdleReview::getAppointmentId, appointId)
                .eq(IdleReview::getFromUserId, userId));
        if (exist > 0) {
            throw new BizException(ResultCode.DUPLICATE_OPERATION, "你已评价过本次互换");
        }
        IdleReview review = new IdleReview();
        review.setAppointmentId(appointId);
        review.setFromUserId(userId);
        review.setToUserId(toUserId);
        review.setScore(dto.getScore());
        review.setContent(dto.getContent());
        reviewMapper.insert(review);
        messageService.send(toUserId, Constants.MSG_INTERACT, "收到新的互评",
                "你收到了一条闲置互换评价，快去查看对方的反馈吧。",
                Constants.BIZ_IDLE, appointment.getItemId());
    }

    /** 我的预约（买/卖双向） */
    public PageResult<AppointmentVO> myAppointments(Long userId, String role, int pageNum, int pageSize) {
        Page<IdleAppointment> page = appointmentMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<IdleAppointment>()
                        .eq("buyer".equals(role), IdleAppointment::getBuyerId, userId)
                        .eq("seller".equals(role), IdleAppointment::getSellerId, userId)
                        .orderByDesc(IdleAppointment::getId));
        List<IdleAppointment> records = page.getRecords();
        // 批量取物品与用户信息
        Map<Long, IdleItem> itemMap = records.isEmpty() ? Map.of() :
                idleItemMapper.selectBatchIds(records.stream().map(IdleAppointment::getItemId).toList())
                        .stream().collect(Collectors.toMap(IdleItem::getId, Function.identity()));
        List<Long> uids = records.stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getBuyerId(), a.getSellerId()))
                .distinct().toList();
        Map<Long, User> userMap = uids.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(uids).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        // 当前用户已评价的预约
        Map<Long, Boolean> reviewedMap = records.isEmpty() ? Map.of() :
                reviewMapper.selectList(new LambdaQueryWrapper<IdleReview>()
                        .eq(IdleReview::getFromUserId, userId)
                        .in(IdleReview::getAppointmentId, records.stream().map(IdleAppointment::getId).toList()))
                        .stream().collect(Collectors.toMap(IdleReview::getAppointmentId, r -> true));

        return PageResult.of(page, a -> {
            AppointmentVO vo = new AppointmentVO();
            BeanUtil.copyProperties(a, vo);
            IdleItem item = itemMap.get(a.getItemId());
            vo.setItemTitle(item == null ? "物品已删除" : item.getTitle());
            vo.setItemImage(firstImage(item == null ? null : item.getImages()));
            User buyer = userMap.get(a.getBuyerId());
            User seller = userMap.get(a.getSellerId());
            vo.setBuyerNickname(buyer == null ? "" : buyer.getNickname());
            vo.setSellerNickname(seller == null ? "" : seller.getNickname());
            vo.setReviewed(reviewedMap.getOrDefault(a.getId(), false));
            return vo;
        });
    }

    // ---------- 内部方法 ----------

    private IdleAppointment getAppointment(Long id) {
        IdleAppointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        }
        return appointment;
    }

    private IdleItem checkOwner(Long userId, Long id) {
        IdleItem item = idleItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物品不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作自己发布的物品");
        }
        return item;
    }

    private IdleItemVO toVO(IdleItem item) {
        IdleItemVO vo = new IdleItemVO();
        BeanUtil.copyProperties(item, vo);
        vo.setImageList(parseJson(item.getImages()));
        User publisher = userMapper.selectById(item.getUserId());
        vo.setPublisherNickname(publisher == null ? "" : publisher.getNickname());
        vo.setPublisherAvatar(publisher == null ? null : publisher.getAvatar());
        return vo;
    }

    public static String toJson(List<String> images) {
        return images == null || images.isEmpty() ? null : JSONUtil.toJsonStr(images);
    }

    public static List<String> parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static String firstImage(String json) {
        List<String> list = parseJson(json);
        return list.isEmpty() ? null : list.get(0);
    }
}
