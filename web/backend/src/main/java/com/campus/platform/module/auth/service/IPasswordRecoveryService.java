package com.campus.platform.module.auth.service;

import java.util.List;
import java.util.Map;

/**
 * 人工找回密码服务接口：申请→审核→凭据领取临时密码。
 */
public interface IPasswordRecoveryService {

    /** 找回申请 */
    record Application(String studentNo, String name, String contact, String explanation) {}

    /** 申请状态 */
    record Status(String status, String reason) {}

    /** 提交申请，返回领取凭据 */
    String submit(Application application);

    /** 查询申请状态 */
    Status status(String receipt);

    /** 管理端申请列表 */
    List<Map<String, Object>> list(long before);

    /** 管理端审核 */
    void decide(long id, boolean approve, String reason);

    /** 凭据领取临时密码 */
    String claim(String receipt);
}
