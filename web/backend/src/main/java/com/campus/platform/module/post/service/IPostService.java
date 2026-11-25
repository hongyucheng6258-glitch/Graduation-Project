package com.campus.platform.module.post.service;

import com.campus.platform.module.post.mapper.PostCommentMapper;
import com.campus.platform.module.post.mapper.PostLikeMapper;
import com.campus.platform.module.post.dto.PostPublishDTO;
import com.campus.platform.module.post.entity.PostComment;
import com.campus.platform.module.post.dto.CommentDTO;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.post.vo.CommentVO;
import com.campus.platform.module.post.entity.PostLike;
import com.campus.platform.module.post.vo.PostVO;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.ai.service.IContentAiAuditService;
import com.campus.platform.module.favorite.entity.Favorite;
import com.campus.platform.module.favorite.mapper.FavoriteMapper;
import com.campus.platform.module.message.service.IMessageService;
import com.campus.platform.utils.ImageListJson;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.common.BizException;
import com.campus.platform.utils.ListedImages;
import com.campus.platform.common.Constants;
import com.campus.platform.common.PageResult;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * PostService 服务接口。
 */
public interface IPostService {

    /** 内联图片（base64 数据与内容类型） */
    record InlineImage(byte[] bytes, String contentType) {}

    Post publish(Long userId, PostPublishDTO dto);

    PageResult<PostVO> list(Long currentUid, String keyword, int pageNum, int pageSize);

    PageResult<PostVO> list(Long currentUid, String keyword, int pageNum, int pageSize, boolean compactImages);

    InlineImage image(Long postId, int index);

    PostVO detail(Long currentUid, Long postId);

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);

    PostComment comment(Long userId, Long postId, CommentDTO dto);

    PageResult<CommentVO> comments(Long postId, int pageNum, int pageSize);
}
