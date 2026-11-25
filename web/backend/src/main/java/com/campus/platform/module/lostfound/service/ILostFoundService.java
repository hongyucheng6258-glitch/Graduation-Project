package com.campus.platform.module.lostfound.service;

import com.campus.platform.common.mapper.MapperQueries;
import com.campus.platform.module.lostfound.dto.ClaimDTO;
import com.campus.platform.module.lostfound.dto.LostFoundPublishDTO;
import com.campus.platform.module.lostfound.entity.LostFoundClaim;
import com.campus.platform.module.lostfound.mapper.LostFoundClaimMapper;
import com.campus.platform.module.lostfound.mapper.LostFoundMapper;
import com.campus.platform.module.lostfound.vo.ClaimVO;
import com.campus.platform.module.lostfound.vo.LostFoundVO;
import com.campus.platform.module.lostfound.entity.LostFound;
import com.campus.platform.module.ai.service.IContentAiAuditService;
import com.campus.platform.utils.ImageListJson;
import com.campus.platform.module.message.service.IMessageService;
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
import java.util.Map;

/**
 * LostFoundService 服务接口。
 */
public interface ILostFoundService {

    LostFound publish(Long userId, LostFoundPublishDTO dto);

    PageResult<LostFoundVO> list(Integer type, String keyword, int pageNum, int pageSize);

    LostFoundVO detail(Long id, Long currentUid);

    LostFound update(Long userId, Long id, LostFoundPublishDTO dto);

    LostFoundClaim claim(Long userId, Long lfId, ClaimDTO dto);

    PageResult<ClaimVO> claims(Long userId, Long lfId, int pageNum, int pageSize);

    void handleClaim(Long userId, Long claimId, boolean accept);

    ClaimVO myClaim(Long userId, Long lfId);

    void confirmReturn(Long userId, Long claimId);

    void finish(Long userId, Long id);

    PageResult<LostFoundVO> myList(Long userId, int pageNum, int pageSize);
}
