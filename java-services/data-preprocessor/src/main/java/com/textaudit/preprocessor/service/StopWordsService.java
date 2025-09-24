package com.textaudit.preprocessor.service;

import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 停用词服务
 * 负责加载和管理中文停用词列表
 */
@Slf4j
@Service
public class StopWordsService {
    
    private final Set<String> stopWords;
    
    public StopWordsService() {
        this.stopWords = loadStopWords();
        log.info("停用词加载完成，共 {} 个", stopWords.size());
    }
    
    /**
     * 检查是否为停用词
     */
    public boolean isStopWord(String word) {
        return stopWords.contains(word.toLowerCase());
    }
    
    /**
     * 获取所有停用词
     */
    public Set<String> getStopWords() {
        return new HashSet<>(stopWords);
    }
    
    /**
     * 从资源文件加载停用词
     */
    private Set<String> loadStopWords() {
        Set<String> words = new HashSet<>();
        
        // 添加基本的中文停用词
        words.add("的");
        words.add("了");
        words.add("在");
        words.add("是");
        words.add("我");
        words.add("有");
        words.add("和");
        words.add("就");
        words.add("不");
        words.add("人");
        words.add("都");
        words.add("一");
        words.add("一个");
        words.add("上");
        words.add("也");
        words.add("很");
        words.add("到");
        words.add("说");
        words.add("要");
        words.add("去");
        words.add("你");
        words.add("会");
        words.add("着");
        words.add("没有");
        words.add("看");
        words.add("好");
        words.add("自己");
        words.add("这");
        words.add("那");
        words.add("里");
        words.add("吧");
        words.add("啊");
        words.add("呢");
        words.add("吗");
        words.add("哦");
        words.add("嗯");
        words.add("哈");
        words.add("呵");
        words.add("嘿");
        words.add("嗨");
        
        // 尝试从资源文件加载更多停用词
        try {
            ClassPathResource resource = new ClassPathResource("stopwords/chinese_stopwords.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            words.add(line.toLowerCase());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("无法加载停用词文件，使用默认停用词列表: {}", e.getMessage());
        }
        
        return words;
    }
}