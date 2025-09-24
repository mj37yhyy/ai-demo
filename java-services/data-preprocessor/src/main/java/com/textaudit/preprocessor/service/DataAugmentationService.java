package com.textaudit.preprocessor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 数据增强服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class DataAugmentationService {

    // 配置参数
    @Value("${text-processing.data-augmentation.enable-synonym-replacement:true}")
    private boolean enableSynonymReplacement;

    @Value("${text-processing.data-augmentation.enable-random-insertion:true}")
    private boolean enableRandomInsertion;

    @Value("${text-processing.data-augmentation.enable-random-swap:true}")
    private boolean enableRandomSwap;

    @Value("${text-processing.data-augmentation.enable-random-deletion:true}")
    private boolean enableRandomDeletion;

    @Value("${text-processing.data-augmentation.synonym-replacement-rate:0.1}")
    private double synonymReplacementRate;

    @Value("${text-processing.data-augmentation.random-insertion-rate:0.1}")
    private double randomInsertionRate;

    @Value("${text-processing.data-augmentation.random-swap-rate:0.1}")
    private double randomSwapRate;

    @Value("${text-processing.data-augmentation.random-deletion-rate:0.1}")
    private double randomDeletionRate;

    @Value("${text-processing.data-augmentation.max-augmented-samples:5}")
    private int maxAugmentedSamples;

    // 同义词词典（简化版）
    private static final Map<String, List<String>> SYNONYM_DICT = new HashMap<>();
    
    static {
        // 中文同义词
        SYNONYM_DICT.put("好", Arrays.asList("棒", "优秀", "不错", "很好"));
        SYNONYM_DICT.put("坏", Arrays.asList("差", "糟糕", "不好", "恶劣"));
        SYNONYM_DICT.put("大", Arrays.asList("巨大", "庞大", "很大", "硕大"));
        SYNONYM_DICT.put("小", Arrays.asList("微小", "细小", "很小", "迷你"));
        SYNONYM_DICT.put("快", Arrays.asList("迅速", "快速", "敏捷", "急速"));
        SYNONYM_DICT.put("慢", Arrays.asList("缓慢", "迟缓", "缓缓", "徐徐"));
        
        // 英文同义词
        SYNONYM_DICT.put("good", Arrays.asList("great", "excellent", "wonderful", "fantastic"));
        SYNONYM_DICT.put("bad", Arrays.asList("terrible", "awful", "horrible", "poor"));
        SYNONYM_DICT.put("big", Arrays.asList("large", "huge", "enormous", "massive"));
        SYNONYM_DICT.put("small", Arrays.asList("tiny", "little", "mini", "compact"));
        SYNONYM_DICT.put("fast", Arrays.asList("quick", "rapid", "swift", "speedy"));
        SYNONYM_DICT.put("slow", Arrays.asList("sluggish", "gradual", "leisurely", "unhurried"));
    }

    // 常用词汇库（用于随机插入）
    private static final List<String> COMMON_WORDS = Arrays.asList(
        // 中文常用词
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "个", "上", "也", "很", "到", "说", "要", "去",
        // 英文常用词
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "up", "about", "into", "through", "during"
    );

    // 标点符号模式
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}]");

    /**
     * 数据增强主方法
     */
    public List<String> augmentText(String originalText, int numAugmentations) {
        if (originalText == null || originalText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        numAugmentations = Math.min(numAugmentations, maxAugmentedSamples);
        List<String> augmentedTexts = new ArrayList<>();
        
        // 分词
        List<String> words = tokenize(originalText);
        if (words.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("开始数据增强，原始文本长度: {}, 词汇数: {}, 目标增强数量: {}", 
                 originalText.length(), words.size(), numAugmentations);

        // 生成增强样本
        for (int i = 0; i < numAugmentations; i++) {
            String augmentedText = applyRandomAugmentation(words);
            if (augmentedText != null && !augmentedText.equals(originalText)) {
                augmentedTexts.add(augmentedText);
            }
        }

        log.debug("数据增强完成，生成 {} 个增强样本", augmentedTexts.size());
        return augmentedTexts;
    }

    /**
     * 应用随机增强策略
     */
    private String applyRandomAugmentation(List<String> words) {
        List<String> augmentedWords = new ArrayList<>(words);
        Random random = ThreadLocalRandom.current();

        // 随机选择增强策略
        List<AugmentationStrategy> strategies = new ArrayList<>();
        if (enableSynonymReplacement) strategies.add(AugmentationStrategy.SYNONYM_REPLACEMENT);
        if (enableRandomInsertion) strategies.add(AugmentationStrategy.RANDOM_INSERTION);
        if (enableRandomSwap) strategies.add(AugmentationStrategy.RANDOM_SWAP);
        if (enableRandomDeletion) strategies.add(AugmentationStrategy.RANDOM_DELETION);

        if (strategies.isEmpty()) {
            return String.join(" ", words);
        }

        AugmentationStrategy strategy = strategies.get(random.nextInt(strategies.size()));

        switch (strategy) {
            case SYNONYM_REPLACEMENT:
                augmentedWords = applySynonymReplacement(augmentedWords);
                break;
            case RANDOM_INSERTION:
                augmentedWords = applyRandomInsertion(augmentedWords);
                break;
            case RANDOM_SWAP:
                augmentedWords = applyRandomSwap(augmentedWords);
                break;
            case RANDOM_DELETION:
                augmentedWords = applyRandomDeletion(augmentedWords);
                break;
        }

        return String.join(" ", augmentedWords);
    }

    /**
     * 同义词替换
     */
    private List<String> applySynonymReplacement(List<String> words) {
        List<String> result = new ArrayList<>(words);
        Random random = ThreadLocalRandom.current();
        
        int numReplacements = Math.max(1, (int) (words.size() * synonymReplacementRate));
        
        for (int i = 0; i < numReplacements; i++) {
            int randomIndex = random.nextInt(result.size());
            String word = result.get(randomIndex);
            
            List<String> synonyms = SYNONYM_DICT.get(word.toLowerCase());
            if (synonyms != null && !synonyms.isEmpty()) {
                String synonym = synonyms.get(random.nextInt(synonyms.size()));
                result.set(randomIndex, synonym);
            }
        }
        
        return result;
    }

    /**
     * 随机插入
     */
    private List<String> applyRandomInsertion(List<String> words) {
        List<String> result = new ArrayList<>(words);
        Random random = ThreadLocalRandom.current();
        
        int numInsertions = Math.max(1, (int) (words.size() * randomInsertionRate));
        
        for (int i = 0; i < numInsertions; i++) {
            int randomIndex = random.nextInt(result.size() + 1);
            String randomWord = COMMON_WORDS.get(random.nextInt(COMMON_WORDS.size()));
            result.add(randomIndex, randomWord);
        }
        
        return result;
    }

    /**
     * 随机交换
     */
    private List<String> applyRandomSwap(List<String> words) {
        if (words.size() < 2) {
            return new ArrayList<>(words);
        }
        
        List<String> result = new ArrayList<>(words);
        Random random = ThreadLocalRandom.current();
        
        int numSwaps = Math.max(1, (int) (words.size() * randomSwapRate));
        
        for (int i = 0; i < numSwaps; i++) {
            int index1 = random.nextInt(result.size());
            int index2 = random.nextInt(result.size());
            
            if (index1 != index2) {
                Collections.swap(result, index1, index2);
            }
        }
        
        return result;
    }

    /**
     * 随机删除
     */
    private List<String> applyRandomDeletion(List<String> words) {
        if (words.size() <= 1) {
            return new ArrayList<>(words);
        }
        
        List<String> result = new ArrayList<>(words);
        Random random = ThreadLocalRandom.current();
        
        int numDeletions = Math.max(1, (int) (words.size() * randomDeletionRate));
        numDeletions = Math.min(numDeletions, result.size() - 1); // 至少保留一个词
        
        for (int i = 0; i < numDeletions; i++) {
            if (result.size() > 1) {
                int randomIndex = random.nextInt(result.size());
                result.remove(randomIndex);
            }
        }
        
        return result;
    }

    /**
     * 回译增强（模拟）
     */
    public List<String> backTranslationAugment(String text, int numAugmentations) {
        List<String> augmentedTexts = new ArrayList<>();
        
        // 模拟回译过程（实际应用中需要调用翻译API）
        for (int i = 0; i < numAugmentations; i++) {
            String augmented = simulateBackTranslation(text);
            if (augmented != null && !augmented.equals(text)) {
                augmentedTexts.add(augmented);
            }
        }
        
        return augmentedTexts;
    }

    /**
     * 模拟回译
     */
    private String simulateBackTranslation(String text) {
        // 简单的模拟：随机改变一些词汇顺序和表达方式
        List<String> words = tokenize(text);
        if (words.size() < 2) {
            return text;
        }

        Random random = ThreadLocalRandom.current();
        
        // 随机应用一些变换
        if (random.nextDouble() < 0.3) {
            // 交换相邻词汇
            int index = random.nextInt(words.size() - 1);
            Collections.swap(words, index, index + 1);
        }
        
        if (random.nextDouble() < 0.2) {
            // 同义词替换
            words = applySynonymReplacement(words);
        }
        
        return String.join(" ", words);
    }

    /**
     * 噪声注入增强
     */
    public List<String> noiseInjectionAugment(String text, int numAugmentations) {
        List<String> augmentedTexts = new ArrayList<>();
        
        for (int i = 0; i < numAugmentations; i++) {
            String augmented = injectNoise(text);
            if (augmented != null && !augmented.equals(text)) {
                augmentedTexts.add(augmented);
            }
        }
        
        return augmentedTexts;
    }

    /**
     * 注入噪声
     */
    private String injectNoise(String text) {
        Random random = ThreadLocalRandom.current();
        StringBuilder result = new StringBuilder(text);
        
        // 随机字符替换
        if (random.nextDouble() < 0.1) {
            int index = random.nextInt(result.length());
            char randomChar = (char) ('a' + random.nextInt(26));
            result.setCharAt(index, randomChar);
        }
        
        // 随机字符插入
        if (random.nextDouble() < 0.05) {
            int index = random.nextInt(result.length() + 1);
            char randomChar = (char) ('a' + random.nextInt(26));
            result.insert(index, randomChar);
        }
        
        // 随机字符删除
        if (random.nextDouble() < 0.05 && result.length() > 1) {
            int index = random.nextInt(result.length());
            result.deleteCharAt(index);
        }
        
        return result.toString();
    }

    /**
     * 批量数据增强
     */
    public Map<String, List<String>> batchAugment(List<String> texts, int numAugmentationsPerText) {
        Map<String, List<String>> results = new HashMap<>();
        
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            try {
                List<String> augmented = augmentText(text, numAugmentationsPerText);
                results.put("text_" + i, augmented);
            } catch (Exception e) {
                log.error("增强第{}个文本时发生错误: {}", i, e.getMessage(), e);
                results.put("text_" + i, Collections.emptyList());
            }
        }
        
        return results;
    }

    /**
     * 简单分词（按空格和标点符号分割）
     */
    private List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // 简单的分词：按空格和标点符号分割
        String[] tokens = text.split("[\\s\\p{Punct}]+");
        List<String> result = new ArrayList<>();
        
        for (String token : tokens) {
            if (!token.trim().isEmpty()) {
                result.add(token.trim());
            }
        }
        
        return result;
    }

    /**
     * 获取增强统计信息
     */
    public Map<String, Object> getAugmentationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enableSynonymReplacement", enableSynonymReplacement);
        stats.put("enableRandomInsertion", enableRandomInsertion);
        stats.put("enableRandomSwap", enableRandomSwap);
        stats.put("enableRandomDeletion", enableRandomDeletion);
        stats.put("synonymReplacementRate", synonymReplacementRate);
        stats.put("randomInsertionRate", randomInsertionRate);
        stats.put("randomSwapRate", randomSwapRate);
        stats.put("randomDeletionRate", randomDeletionRate);
        stats.put("maxAugmentedSamples", maxAugmentedSamples);
        stats.put("synonymDictSize", SYNONYM_DICT.size());
        stats.put("commonWordsSize", COMMON_WORDS.size());
        return stats;
    }

    /**
     * 增强策略枚举
     */
    private enum AugmentationStrategy {
        SYNONYM_REPLACEMENT,
        RANDOM_INSERTION,
        RANDOM_SWAP,
        RANDOM_DELETION
    }
}