package com.campus.platform.module.ai.gateway;

import cn.hutool.dfa.WordTree;
import com.campus.platform.module.ai.entity.SensitiveWord;
import com.campus.platform.module.ai.mapper.SensitiveWordMapper;
import com.campus.platform.utils.RedisCache;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Duration;

/**
 * 敏感词过滤服务：基于 Hutool WordTree（DFA 算法）。
 * 启动时从 DB(sensitive_word 表) + classpath:sensitive-words.txt 合并加载。
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;
    private final RedisCache cache;
    private final WordTree wordTree = new WordTree();

    @PostConstruct
    public void init() {
        // 1. 从 DB 加载
        List<String> words = cache.get("sensitive-words", new TypeReference<List<String>>() {},
                Duration.ofMinutes(10), () -> sensitiveWordMapper.selectList(null).stream()
                        .map(SensitiveWord::getWord).filter(word -> word != null && !word.isBlank()).toList());
        words.forEach(wordTree::addWord);
        // 2. 从 classpath:sensitive-words.txt 追加加载
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource("sensitive-words.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    wordTree.addWord(line);
                }
            }
        } catch (Exception e) {
            // 文件不存在时忽略，仅用 DB 词库
        }
    }

    /** 检测文本是否包含敏感词 */
    public boolean contains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return wordTree.isMatch(text);
    }
}
