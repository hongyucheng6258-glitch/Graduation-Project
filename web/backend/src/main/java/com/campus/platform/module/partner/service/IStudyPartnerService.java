package com.campus.platform.module.partner.service;

import com.campus.platform.common.PageResult;
import com.campus.platform.module.partner.dto.StudyPartnerDTO;
import com.campus.platform.module.partner.entity.StudyPartner;
import com.campus.platform.module.partner.vo.StudyPartnerVO;

import java.util.List;

/** 学习搭子服务接口：发布→审核→浏览→AI匹配撮合 */
public interface IStudyPartnerService {

    /** 发布（待审核） */
    StudyPartner publish(Long userId, StudyPartnerDTO dto);

    /** 列表（公开，仅审核通过） */
    PageResult<StudyPartnerVO> list(String subject, String keyword, int pageNum, int pageSize);

    /** 我的发布 */
    PageResult<StudyPartnerVO> myList(Long userId, int pageNum, int pageSize);

    /** 标记已找到（仅本人） */
    void finish(Long userId, Long id);

    /** AI 智能匹配：基于我的需求从进行中的搭子中挑选最合适的 */
    List<StudyPartnerVO> match(Long userId, StudyPartnerDTO dto);
}
