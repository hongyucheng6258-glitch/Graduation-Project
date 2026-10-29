package com.campus.platform.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.activity.entity.Activity;
import com.campus.platform.module.activity.entity.ActivityMember;
import com.campus.platform.module.activity.entity.ActivitySignin;
import com.campus.platform.module.activity.mapper.ActivityMapper;
import com.campus.platform.module.activity.mapper.ActivityMemberMapper;
import com.campus.platform.module.activity.mapper.ActivitySigninMapper;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端导出：活动报名名单 / 签到名单（CSV，UTF-8 BOM，Excel 可直接打开）。
 */
@Service
@RequiredArgsConstructor
public class AdminExportService {

    private final ActivityMapper activityMapper;
    private final ActivityMemberMapper activityMemberMapper;
    private final ActivitySigninMapper activitySigninMapper;
    private final UserMapper userMapper;

    /** 导出报名名单 */
    public void exportMembers(Long activityId, HttpServletResponse response) throws IOException {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        List<ActivityMember> members = activityMemberMapper.selectList(
                new LambdaQueryWrapper<ActivityMember>()
                        .eq(ActivityMember::getActivityId, activityId)
                        .orderByAsc(ActivityMember::getId));
        Map<Long, User> users = loadUsers(members.stream().map(ActivityMember::getUserId).toList());

        StringBuilder sb = new StringBuilder();
        sb.append("序号,姓名,学号,手机号,报名说明,状态,报名时间\r\n");
        int i = 1;
        for (ActivityMember m : members) {
            User u = users.get(m.getUserId());
            sb.append(i++).append(',')
                    .append(csv(u == null ? "" : u.getNickname())).append(',')
                    .append(csv(u == null ? "" : u.getStudentNo())).append(',')
                    .append(csv(u == null ? "" : u.getPhone())).append(',')
                    .append(csv(m.getRemark())).append(',')
                    .append(memberStatusText(m.getStatus())).append(',')
                    .append(m.getCreateTime() == null ? "" : m.getCreateTime().toString().replace('T', ' '))
                    .append("\r\n");
        }
        writeCsv(response, "activity_" + activityId + "_报名名单.csv", sb.toString());
    }

    /** 导出签到名单 */
    public void exportSignins(Long activityId, HttpServletResponse response) throws IOException {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        List<ActivitySignin> signins = activitySigninMapper.selectList(
                new LambdaQueryWrapper<ActivitySignin>()
                        .eq(ActivitySignin::getActivityId, activityId)
                        .orderByAsc(ActivitySignin::getId));
        Map<Long, User> users = loadUsers(signins.stream().map(ActivitySignin::getUserId).toList());

        StringBuilder sb = new StringBuilder();
        sb.append("序号,姓名,学号,签到时间\r\n");
        int i = 1;
        for (ActivitySignin s : signins) {
            User u = users.get(s.getUserId());
            sb.append(i++).append(',')
                    .append(csv(u == null ? "" : u.getNickname())).append(',')
                    .append(csv(u == null ? "" : u.getStudentNo())).append(',')
                    .append(s.getSignTime() == null ? "" : s.getSignTime().toString().replace('T', ' '))
                    .append("\r\n");
        }
        writeCsv(response, "activity_" + activityId + "_签到名单.csv", sb.toString());
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        Map<Long, User> map = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return map;
        }
        for (User u : userMapper.selectBatchIds(userIds)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private String memberStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case Constants.MEMBER_PENDING -> "待审批";
            case Constants.MEMBER_APPROVED -> "已通过";
            case Constants.MEMBER_REJECTED -> "已拒绝";
            default -> String.valueOf(status);
        };
    }

    /** CSV 字段转义：含逗号/引号/换行时加引号 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeCsv(HttpServletResponse response, String filename, String content) throws IOException {
        byte[] body = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        // RFC 5987：ASCII 文件名 + UTF-8 编码文件名（中文名经 filename* 传递，避免容器丢弃非 ASCII 头）
        String asciiName = filename.replaceAll("[^\\p{ASCII}]", "_");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodeFilename(asciiName) + "\"; filename*=UTF-8''"
                        + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setContentLength(body.length);
        try (OutputStream os = response.getOutputStream()) {
            os.write(body);
            os.flush();
        }
    }

    /** 文件名转义（去除逗号/引号，避免响应头注入） */
    private String encodeFilename(String name) {
        return name.replace("\"", "").replace(",", "").replace(";", "");
    }
}
