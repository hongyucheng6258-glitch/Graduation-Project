package com.campus.platform.module.post;

import com.campus.platform.module.post.mapper.PostCommentMapper;
import com.campus.platform.module.post.mapper.PostLikeMapper;
import com.campus.platform.module.post.service.PostService;
import com.campus.platform.module.post.mapper.PostMapper;
import com.campus.platform.module.post.entity.Post;

import com.campus.platform.module.ai.service.ContentAiAuditService;
import com.campus.platform.module.message.service.MessageService;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.platform.module.ai.gateway.SensitiveWordService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.campus.platform.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceSearchTest {
    @Mock private PostMapper postMapper;
    @Mock private PostCommentMapper commentMapper;
    @Mock private PostLikeMapper likeMapper;
    @Mock private UserMapper userMapper;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private MessageService messageService;
    @Mock private ContentAiAuditService contentAiAuditService;
    @InjectMocks private PostService postService;

    @Test
    void listFiltersApprovedPostsByKeyword() {
        initPostTableInfo();
        when(postMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());

        postService.list(null, "高数", 1, 10);

        ArgumentCaptor<Wrapper<Post>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper).selectPage(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("content", "LIKE");
    }

    @Test
    void listAllowsMissingKeywordForHomePage() {
        initPostTableInfo();
        when(postMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());

        postService.list(null, null, 1, 4);

        verify(postMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    private void initPostTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Post.class);
    }
}
