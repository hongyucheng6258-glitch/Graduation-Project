package com.campus.platform.module.ai.agent;

import com.campus.platform.common.BizException;
import com.campus.platform.common.ResultCode;
import com.campus.platform.config.SystemConfigHolder;
import com.campus.platform.module.idle.dto.IdlePublishDTO;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.upload.mapper.UploadResourceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdlePublishDrafts implements AgentToolSource {
    static final String PREPARE = "prepare_idle_publish";
    private static final Set<String> CATEGORIES = Set.of("教材书籍", "数码电子", "生活用品", "运动器材", "服饰鞋包", "其他");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdleService idle;
    private final UploadResourceMapper uploads;
    private final SystemConfigHolder config;

    @Override
    public List<AgentTool> tools() {
        return List.of(new AgentTool(PREPARE,
                "用户明确要求发布自己的闲置时创建可编辑草稿。title 必填，description、expectItem、category 可省略。"
                        + "分类仅可选教材书籍、数码电子、生活用品、运动器材、服饰鞋包、其他。"
                        + "仅写入用户提供的物品事实，图片由用户在发布表单上传。此工具不发布物品。",
                Map.of("title", "string", "description", "string", "expectItem", "string", "category", "string"),
                List.of("title"), this::prepare));
    }

    private JsonNode prepare(Long uid, JsonNode args) {
        checkPublishing();
        var form = form(new Submission(args.path("title").asText(), args.path("description").asText(),
                args.path("category").asText(), args.path("expectItem").asText(), List.of()));
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO agent_idle_draft(id,user_id,title,description,category,expect_item,expires_at)
                VALUES(?,?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 1 DAY))
                """, id, uid, form.getTitle(), form.getDescription(), form.getCategory(), form.getExpectItem());
        return json.valueToTree(requireDraft(uid, id, false));
    }

    public Draft get(Long uid, String id) {
        return requireDraft(uid, id, false);
    }

    Draft find(Long uid, String id) {
        return query(uid, id, false);
    }

    @Transactional
    public Draft confirm(Long uid, String id, Submission request) {
        Draft current = requireDraft(uid, id, true);
        if (current.itemId() != null) {
            return current;
        }
        if ("EXPIRED".equals(current.state())) {
            throw new BizException(ResultCode.BAD_REQUEST, "草稿已过期，请让校园向导重新生成");
        }
        checkPublishing();
        var form = form(request);
        form.setImages(ownedImages(uid, request.imageIds()));
        var item = idle.publish(uid, form);
        jdbc.update("""
                UPDATE agent_idle_draft SET item_id=?,title=?,description=?,category=?,expect_item=? WHERE id=?
                """, item.getId(), form.getTitle(), form.getDescription(), form.getCategory(), form.getExpectItem(), id);
        return requireDraft(uid, id, false);
    }

    private IdlePublishDTO form(Submission request) {
        String title = clean(request.title(), 64);
        if (title.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请填写物品标题");
        String category = clean(request.category(), 32);
        if (!category.isEmpty() && !CATEGORIES.contains(category)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择有效的闲置分类");
        }
        var dto = new IdlePublishDTO();
        dto.setTitle(title);
        dto.setDescription(clean(request.description(), 2000));
        dto.setCategory(category);
        dto.setExpectItem(clean(request.expectItem(), 128));
        return dto;
    }

    List<String> ownedImages(Long uid, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        var urls = new ArrayList<String>(ids.size());
        long encodedLength = 0;
        for (Long id : ids) {
            var image = uploads.selectById(id);
            if (image == null || !uid.equals(image.getOwnerUserId()) || !"image".equals(image.getResourceType())) {
                throw new BizException(ResultCode.BAD_REQUEST, "图片资源不存在或不属于当前用户");
            }
            String url = image.getResourceUrl();
            encodedLength += url.length();
            if (encodedLength > 15L * 1024 * 1024) {
                throw new BizException(ResultCode.BAD_REQUEST, "图片总量过大，请压缩后再提交");
            }
            urls.add(url);
        }
        return urls;
    }

    private String clean(String value, int max) {
        String text = value == null ? "" : value.strip();
        if (text.length() > max) throw new BizException(ResultCode.BAD_REQUEST, "草稿字段超过允许长度");
        return text;
    }

    private void checkPublishing() {
        if (!config.isIdlePublishEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "当前已关闭闲置发布，请联系管理员");
        }
    }

    private Draft requireDraft(Long uid, String id, boolean lock) {
        var draft = query(uid, id, lock);
        if (draft == null) throw new BizException(ResultCode.NOT_FOUND, "未找到闲置发布草稿");
        return draft;
    }

    private Draft query(Long uid, String id, boolean lock) {
        return jdbc.query("""
                SELECT id,title,description,category,expect_item,expires_at,item_id,
                       CASE WHEN item_id IS NOT NULL THEN 'SUBMITTED'
                            WHEN expires_at<=NOW() THEN 'EXPIRED' ELSE 'DRAFT' END
                FROM agent_idle_draft WHERE id=? AND user_id=?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> new Draft(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, LocalDateTime.class),
                rs.getObject(7, Long.class), rs.getString(8)), id, uid).stream().findFirst().orElse(null);
    }

    public record Submission(@NotBlank @Size(max = 64) String title, @Size(max = 2000) String description,
                             @Size(max = 32) String category, @Size(max = 128) String expectItem,
                             @Size(max = 9) List<@NotNull @Positive Long> imageIds) {}

    public record Draft(String id, String title, String description, String category, String expectItem,
                        LocalDateTime expiresAt, Long itemId, String state) {}
}
