package com.campus.platform.module.user.service;

import com.campus.platform.module.user.vo.UserContentVO;
import com.campus.platform.module.user.vo.UserProfileVO;
import com.campus.platform.module.user.vo.UserReviewVO;

import java.util.List;

/**
 * 他人主页公开查询服务接口（只读）：信息卡 + 内容聚合 + 评价聚合。
 */
public interface IUserPublicService {

    /** 公开信息卡 + 统计 */
    UserProfileVO profile(Long uid);

    /** TA 的闲置（已审核） */
    List<UserContentVO> idles(Long uid);

    /** TA 的动态（已审核） */
    List<UserContentVO> posts(Long uid);

    /** TA 发布的活动（已审核） */
    List<UserContentVO> activities(Long uid);

    /** TA 的失物招领（已审核） */
    List<UserContentVO> lostfounds(Long uid);

    /** TA 收到的评价 */
    List<UserReviewVO> reviews(Long uid);
}
