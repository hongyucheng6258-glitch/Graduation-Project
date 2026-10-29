package com.campus.platform.module.ai.gateway;


import com.campus.platform.module.ai.entity.SensitiveWord;
import com.campus.platform.module.ai.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 当前 SensitiveWordService 的 DFA 命中测试。
 *
 * 服务现阶段只提供 contains 能力，测试不再引用已删除的 matchFirst、filter 和 reload 接口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 网关 DFA 敏感词服务")
class SensitiveWordServiceTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of());
        service = new SensitiveWordService(sensitiveWordMapper);
        service.init();
    }

    @Test
    @DisplayName("classpath 词库应正确加载")
    void contains_shouldHitBuiltinWordFromTxt() {
        assertThat(service.contains("有人在群里搞赌博")).isTrue();
    }

    @ParameterizedTest
    @DisplayName("多个内置敏感词均应被拦截")
    @ValueSource(strings = {"赌博", "博彩", "色情", "诈骗", "代考", "代写论文", "毒品", "洗钱", "传销", "刷单"})
    void contains_shouldHitEachBuiltinWord(String word) {
        assertThat(service.contains("正常前缀" + word + "正常后缀")).isTrue();
    }

    @Test
    @DisplayName("正常文本不应被误伤")
    void contains_shouldNotFalsePositiveOnCleanText() {
        assertThat(service.contains("请帮我讲解一下二叉树的层序遍历")).isFalse();
    }

    @Test
    @DisplayName("注释行不得被当作敏感词加载")
    void contains_shouldIgnoreCommentLines() {
        assertThat(service.contains("DFA 初始敏感词库")).isFalse();
        assertThat(service.contains("#")).isFalse();
    }

    @Test
    @DisplayName("空输入不应判定为命中")
    void contains_shouldReturnFalseForBlank() {
        assertThat(service.contains(null)).isFalse();
        assertThat(service.contains("")).isFalse();
        assertThat(service.contains("   ")).isFalse();
    }

    @Test
    @DisplayName("数据库词库应与 classpath 词库合并加载")
    void init_shouldMergeDbWordsAndClasspathWords() {
        SensitiveWord dbWord = new SensitiveWord();
        dbWord.setWord("测试专用违禁词");
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(dbWord));

        SensitiveWordService mergedService = new SensitiveWordService(sensitiveWordMapper);
        mergedService.init();

        assertThat(mergedService.contains("这里有测试专用违禁词出现")).isTrue();
        assertThat(mergedService.contains("赌博")).isTrue();
    }

    @Test
    @DisplayName("数据库中的空词条应被忽略")
    void init_shouldIgnoreBlankDbWords() {
        SensitiveWord blank = new SensitiveWord();
        blank.setWord("   ");
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(blank));

        SensitiveWordService mergedService = new SensitiveWordService(sensitiveWordMapper);
        mergedService.init();

        assertThat(mergedService.contains("任意正常文本")).isFalse();
        assertThat(mergedService.contains("赌博")).isTrue();
    }
}
