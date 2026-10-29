package com.campus.platform.module.post;

import com.campus.platform.common.BizException;
import com.campus.platform.common.Constants;
import com.campus.platform.common.ResultCode;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.favorite.mapper.FavoriteMapper;
import com.campus.platform.module.idle.service.IdleService;
import com.campus.platform.module.message.service.MessageService;
import com.campus.platform.module.post.dto.CommentDTO;
import com.campus.platform.module.post.entity.Post;
import com.campus.platform.module.post.entity.PostComment;
import com.campus.platform.module.post.entity.PostLike;
import com.campus.platform.module.post.mapper.PostCommentMapper;
import com.campus.platform.module.post.mapper.PostLikeMapper;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.post.service.PostService;
import com.campus.platform.module.user.entity.User;
import com.campus.platform.module.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 第7项回归：动态点赞/评论计数用原子 SQL 自增自减，不整行读改写。
 * 覆盖：点赞幂等、并发重复点赞唯一索引兜底、取消点赞删除成功才减、评论计数原子自增。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P1 PostService 点赞/评论计数并发")
class PostServiceConcurrencyTest {

    @Mock private PostMapper postMapper;
    @Mock private PostCommentMapper commentMapper;
    @Mock private PostLikeMapper likeMapper;
    @Mock private UserMapper userMapper;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private MessageService messageService;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private ContentAiAuditService contentAiAuditService;

    @InjectMocks
    private PostService postService;

    private Post passedPost(Long id) {
        Post p = new Post();
        p.setId(id);
        p.setUserId(1L);
        p.setAuditStatus(Constants.AUDIT_PASS);
        p.setContent("test post");
        return p;
    }

    @Nested
    @DisplayName("点赞计数")
    class Like {

        @Test
        @DisplayName("点赞成功后用原子 SQL 自增计数，不整行更新")
        void like_shouldUseAtomicIncr() {
            when(postMapper.selectById(100L)).thenReturn(passedPost(100L));

            postService.like(9L, 100L);

            ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
            verify(likeMapper).insert(captor.capture());
            assertThat(captor.getValue().getPostId()).isEqualTo(100L);
            assertThat(captor.getValue().getUserId()).isEqualTo(9L);
            verify(postMapper).incrLikeCount(100L, 1);
        }

        @Test
        @DisplayName("并发重复点赞触发唯一索引异常 → 业务幂等报错，不再自增计数")
        void duplicateLike_shouldBeIdempotentReject() {
            when(postMapper.selectById(100L)).thenReturn(passedPost(100L));
            doThrow(new DuplicateKeyException("uk_post_user")).when(likeMapper).insert(org.mockito.ArgumentMatchers.<PostLike>any());

            assertThatThrownBy(() -> postService.like(9L, 100L))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DUPLICATE_OPERATION.getCode());
            verify(postMapper, never()).incrLikeCount(100L, 1);
        }

        @Test
        @DisplayName("取消点赞删除成功才原子自减，未删除不自减")
        void unlike_shouldDecrOnlyWhenDeleted() {
            when(postMapper.selectById(100L)).thenReturn(passedPost(100L));
            when(likeMapper.delete(any())).thenReturn(1);

            postService.unlike(9L, 100L);
            verify(postMapper).incrLikeCount(100L, -1);

            when(likeMapper.delete(any())).thenReturn(0);
            postService.unlike(9L, 100L);
            verify(postMapper, org.mockito.Mockito.times(1)).incrLikeCount(100L, -1);
        }
    }

    @Nested
    @DisplayName("评论计数")
    class Comment {

        @Test
        @DisplayName("正常评论落库后原子自增评论数")
        void comment_shouldUseAtomicIncr() {
            when(postMapper.selectById(100L)).thenReturn(passedPost(100L));
            when(sensitiveWordService.contains("你好")).thenReturn(false);
            CommentDTO dto = new CommentDTO();
            dto.setContent("你好");

            postService.comment(9L, 100L, dto);

            verify(commentMapper).insert(any(PostComment.class));
            verify(postMapper).incrCommentCount(100L, 1);
        }

        @Test
        @DisplayName("命中敏感词的隐藏评论不计入评论数")
        void hiddenComment_shouldNotIncr() {
            when(postMapper.selectById(100L)).thenReturn(passedPost(100L));
            when(sensitiveWordService.contains("转账给我")).thenReturn(true);
            CommentDTO dto = new CommentDTO();
            dto.setContent("转账给我");

            assertThatThrownBy(() -> postService.comment(9L, 100L, dto))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.SENSITIVE_WORD.getCode());
            verify(postMapper, never()).incrCommentCount(100L, 1);
        }
    }
}
