package com.textaudit.preprocessor.service;

import com.textaudit.preprocessor.dto.TextFeatures;
import com.textaudit.preprocessor.dto.TokenizationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 特征提取服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class FeatureExtractionService {

    // 配置参数
    @Value("${text-processing.feature-extraction.enable-tfidf:true}")
    private boolean enableTfidf;

    @Value("${text-processing.feature-extraction.enable-word2vec:true}")
    private boolean enableWord2vec;

    @Value("${text-processing.feature-extraction.enable-ngram:true}")
    private boolean enableNgram;

    @Value("${text-processing.feature-extraction.ngram-size:3}")
    private int ngramSize;

    @Value("${text-processing.feature-extraction.max-features:10000}")
    private int maxFeatures;

    @Value("${text-processing.feature-extraction.min-df:2}")
    private int minDocumentFrequency;

    @Value("${text-processing.feature-extraction.word2vec-dimension:100}")
    private int word2vecDimension;

    // 正则表达式模式
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("[a-zA-Z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    // 情感词典（简化版）
    private static final Set<String> POSITIVE_WORDS = Set.of(
        "好", "棒", "优秀", "完美", "满意", "喜欢", "爱", "赞", "支持", "推荐",
        "good", "great", "excellent", "perfect", "amazing", "wonderful", "love", "like"
    );

    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "坏", "差", "糟糕", "失望", "讨厌", "恨", "反对", "批评", "抱怨", "问题",
        "bad", "terrible", "awful", "hate", "dislike", "problem", "issue", "wrong"
    );

    // 敏感词词典（简化版）
    private static final Set<String> SENSITIVE_WORDS = Set.of(
        "敏感词1", "敏感词2", "敏感词3"
    );

    // 文档频率缓存
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final Map<String, Double> idfCache = new HashMap<>();

    /**
     * 提取文本特征
     */
    public TextFeatures extractFeatures(String text, TokenizationResult tokenization) {
        log.debug("开始提取文本特征，文本长度: {}", text.length());

        TextFeatures.TextFeaturesBuilder builder = TextFeatures.builder();

        // 提取统计特征
        TextFeatures.StatisticalFeatures statistical = extractStatisticalFeatures(text, tokenization);
        builder.statistical(statistical);

        // 提取TF-IDF特征
        if (enableTfidf && tokenization.getFilteredTokens() != null) {
            Map<String, Double> tfidfVector = calculateTfidfVector(tokenization.getFilteredTokens());
            builder.tfidfVector(tfidfVector);
        }

        // 提取Word2Vec特征
        if (enableWord2vec && tokenization.getFilteredTokens() != null) {
            List<Double> word2vecVector = calculateWord2vecVector(tokenization.getFilteredTokens());
            builder.word2vecVector(word2vecVector);
        }

        // 提取N-gram特征
        if (enableNgram && tokenization.getFilteredTokens() != null) {
            Map<String, Integer> ngramFeatures = extractNgramFeatures(tokenization.getFilteredTokens());
            builder.ngramFeatures(ngramFeatures);
        }

        // 提取情感特征
        TextFeatures.SentimentFeatures sentiment = extractSentimentFeatures(text, tokenization);
        builder.sentiment(sentiment);

        // 提取语言特征
        TextFeatures.LanguageFeatures language = extractLanguageFeatures(text, tokenization);
        builder.language(language);

        TextFeatures features = builder.build();
        log.debug("特征提取完成，特征维度: {}", features.getFeatureDimension());

        return features;
    }

    /**
     * 提取统计特征
     */
    private TextFeatures.StatisticalFeatures extractStatisticalFeatures(String text, TokenizationResult tokenization) {
        int textLength = text.length();
        int wordCount = tokenization.getTotalTokenCount();
        int sentenceCount = countSentences(text);
        int paragraphCount = countParagraphs(text);

        double averageWordLength = tokenization.getTokens().stream()
            .mapToInt(String::length)
            .average()
            .orElse(0.0);

        double averageSentenceLength = sentenceCount > 0 ? (double) textLength / sentenceCount : 0.0;

        int punctuationCount = countMatches(text, PUNCTUATION_PATTERN);
        int digitCount = countMatches(text, DIGIT_PATTERN);
        int uppercaseCount = (int) text.chars().filter(Character::isUpperCase).count();
        int lowercaseCount = (int) text.chars().filter(Character::isLowerCase).count();
        int whitespaceCount = countMatches(text, WHITESPACE_PATTERN);
        int specialCharCount = textLength - uppercaseCount - lowercaseCount - digitCount - whitespaceCount;

        double lexicalDensity = textLength > 0 ? (double) wordCount / textLength : 0.0;
        double typeTokenRatio = wordCount > 0 ? (double) tokenization.getUniqueTokenCount() / wordCount : 0.0;

        return TextFeatures.StatisticalFeatures.builder()
            .textLength(textLength)
            .wordCount(wordCount)
            .sentenceCount(sentenceCount)
            .paragraphCount(paragraphCount)
            .averageWordLength(averageWordLength)
            .averageSentenceLength(averageSentenceLength)
            .punctuationCount(punctuationCount)
            .digitCount(digitCount)
            .uppercaseCount(uppercaseCount)
            .lowercaseCount(lowercaseCount)
            .whitespaceCount(whitespaceCount)
            .specialCharCount(specialCharCount)
            .lexicalDensity(lexicalDensity)
            .typeTokenRatio(typeTokenRatio)
            .build();
    }

    /**
     * 提取情感特征
     */
    private TextFeatures.SentimentFeatures extractSentimentFeatures(String text, TokenizationResult tokenization) {
        List<String> tokens = tokenization.getFilteredTokens();
        
        int positiveCount = 0;
        int negativeCount = 0;

        for (String token : tokens) {
            if (POSITIVE_WORDS.contains(token.toLowerCase())) {
                positiveCount++;
            } else if (NEGATIVE_WORDS.contains(token.toLowerCase())) {
                negativeCount++;
            }
        }

        int totalSentimentWords = positiveCount + negativeCount;
        double polarityScore = totalSentimentWords > 0 ? 
            (double) (positiveCount - negativeCount) / totalSentimentWords : 0.0;
        
        double intensityScore = tokens.size() > 0 ? 
            (double) totalSentimentWords / tokens.size() : 0.0;

        String sentimentClass;
        if (polarityScore > 0.1) {
            sentimentClass = "positive";
        } else if (polarityScore < -0.1) {
            sentimentClass = "negative";
        } else {
            sentimentClass = "neutral";
        }

        double confidence = Math.abs(polarityScore);

        return TextFeatures.SentimentFeatures.builder()
            .polarityScore(polarityScore)
            .intensityScore(intensityScore)
            .sentimentClass(sentimentClass)
            .confidence(confidence)
            .positiveWordCount(positiveCount)
            .negativeWordCount(negativeCount)
            .totalSentimentWords(totalSentimentWords)
            .build();
    }

    /**
     * 提取语言特征
     */
    private TextFeatures.LanguageFeatures extractLanguageFeatures(String text, TokenizationResult tokenization) {
        int textLength = text.length();
        
        int chineseCount = countMatches(text, CHINESE_PATTERN);
        int englishCount = countMatches(text, ENGLISH_PATTERN);
        int digitCount = countMatches(text, DIGIT_PATTERN);
        int punctuationCount = countMatches(text, PUNCTUATION_PATTERN);

        double chineseRatio = textLength > 0 ? (double) chineseCount / textLength : 0.0;
        double englishRatio = textLength > 0 ? (double) englishCount / textLength : 0.0;
        double digitRatio = textLength > 0 ? (double) digitCount / textLength : 0.0;
        double punctuationRatio = textLength > 0 ? (double) punctuationCount / textLength : 0.0;

        // 简单的语言检测
        String detectedLanguage;
        double languageConfidence;
        if (chineseRatio > englishRatio) {
            detectedLanguage = "zh";
            languageConfidence = chineseRatio;
        } else {
            detectedLanguage = "en";
            languageConfidence = englishRatio;
        }

        // 检测敏感词
        List<String> tokens = tokenization.getFilteredTokens();
        int sensitiveWordCount = 0;
        for (String token : tokens) {
            if (SENSITIVE_WORDS.contains(token.toLowerCase())) {
                sensitiveWordCount++;
            }
        }
        boolean containsSensitiveWords = sensitiveWordCount > 0;

        // 计算文本复杂度（基于词汇多样性和平均词长）
        double complexityScore = calculateComplexityScore(tokenization);

        // 计算可读性分数（基于句长和词长）
        double readabilityScore = calculateReadabilityScore(text, tokenization);

        return TextFeatures.LanguageFeatures.builder()
            .detectedLanguage(detectedLanguage)
            .languageConfidence(languageConfidence)
            .chineseCharRatio(chineseRatio)
            .englishCharRatio(englishRatio)
            .digitCharRatio(digitRatio)
            .punctuationRatio(punctuationRatio)
            .containsSensitiveWords(containsSensitiveWords)
            .sensitiveWordCount(sensitiveWordCount)
            .complexityScore(complexityScore)
            .readabilityScore(readabilityScore)
            .build();
    }

    /**
     * 计算TF-IDF向量
     */
    private Map<String, Double> calculateTfidfVector(List<String> tokens) {
        Map<String, Integer> termFrequency = new HashMap<>();
        
        // 计算词频
        for (String token : tokens) {
            termFrequency.merge(token, 1, Integer::sum);
        }

        Map<String, Double> tfidfVector = new HashMap<>();
        int totalTokens = tokens.size();

        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            
            // 计算TF
            double tfScore = (double) tf / totalTokens;
            
            // 计算IDF（简化版，使用缓存）
            double idf = idfCache.computeIfAbsent(term, this::calculateIdf);
            
            // 计算TF-IDF
            double tfidf = tfScore * idf;
            tfidfVector.put(term, tfidf);
        }

        // 限制特征数量
        return tfidfVector.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(maxFeatures)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    /**
     * 计算Word2Vec向量（简化版，使用随机向量模拟）
     */
    private List<Double> calculateWord2vecVector(List<String> tokens) {
        Random random = new Random(tokens.hashCode()); // 使用tokens的hash作为种子保证一致性
        List<Double> vector = new ArrayList<>();
        
        for (int i = 0; i < word2vecDimension; i++) {
            vector.add(random.nextGaussian());
        }
        
        return vector;
    }

    /**
     * 提取N-gram特征
     */
    private Map<String, Integer> extractNgramFeatures(List<String> tokens) {
        Map<String, Integer> ngramFeatures = new HashMap<>();
        
        for (int n = 1; n <= ngramSize; n++) {
            for (int i = 0; i <= tokens.size() - n; i++) {
                List<String> ngram = tokens.subList(i, i + n);
                String ngramKey = String.join("_", ngram);
                ngramFeatures.merge(ngramKey, 1, Integer::sum);
            }
        }
        
        // 限制特征数量
        return ngramFeatures.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(maxFeatures)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    /**
     * 计算IDF值（简化版）
     */
    private double calculateIdf(String term) {
        // 简化的IDF计算，实际应用中需要基于整个语料库
        int df = documentFrequency.getOrDefault(term, 1);
        int totalDocuments = Math.max(documentFrequency.size(), 1000); // 假设总文档数
        return Math.log((double) totalDocuments / df);
    }

    /**
     * 计算文本复杂度
     */
    private double calculateComplexityScore(TokenizationResult tokenization) {
        if (tokenization.getFilteredTokens().isEmpty()) {
            return 0.0;
        }

        // 基于词汇多样性和平均词长
        double typeTokenRatio = (double) tokenization.getUniqueFilteredTokenCount() / 
                               tokenization.getFilteredTokenCount();
        
        double averageWordLength = tokenization.getFilteredTokens().stream()
            .mapToInt(String::length)
            .average()
            .orElse(0.0);

        return (typeTokenRatio * 0.6 + Math.min(averageWordLength / 10.0, 1.0) * 0.4);
    }

    /**
     * 计算可读性分数
     */
    private double calculateReadabilityScore(String text, TokenizationResult tokenization) {
        int sentenceCount = countSentences(text);
        if (sentenceCount == 0 || tokenization.getFilteredTokens().isEmpty()) {
            return 0.0;
        }

        double averageSentenceLength = (double) tokenization.getFilteredTokenCount() / sentenceCount;
        double averageWordLength = tokenization.getFilteredTokens().stream()
            .mapToInt(String::length)
            .average()
            .orElse(0.0);

        // 简化的可读性公式（数值越小越易读）
        double readabilityScore = 1.0 / (1.0 + 0.1 * averageSentenceLength + 0.05 * averageWordLength);
        return Math.max(0.0, Math.min(1.0, readabilityScore));
    }

    /**
     * 统计句子数量
     */
    private int countSentences(String text) {
        return text.split("[.!?。！？]+").length;
    }

    /**
     * 统计段落数量
     */
    private int countParagraphs(String text) {
        return text.split("\n\n+").length;
    }

    /**
     * 统计正则表达式匹配数量
     */
    private int countMatches(String text, Pattern pattern) {
        return (int) pattern.matcher(text).results().count();
    }

    /**
     * 批量提取特征
     */
    public List<TextFeatures> batchExtractFeatures(List<String> texts, List<TokenizationResult> tokenizations) {
        if (texts.size() != tokenizations.size()) {
            throw new IllegalArgumentException("文本数量与分词结果数量不匹配");
        }

        List<TextFeatures> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            try {
                TextFeatures features = extractFeatures(texts.get(i), tokenizations.get(i));
                results.add(features);
            } catch (Exception e) {
                log.error("提取第{}个文本特征时发生错误: {}", i, e.getMessage(), e);
                results.add(null);
            }
        }

        return results;
    }

    /**
     * 更新文档频率统计
     */
    public void updateDocumentFrequency(List<String> tokens) {
        Set<String> uniqueTokens = new HashSet<>(tokens);
        for (String token : uniqueTokens) {
            documentFrequency.merge(token, 1, Integer::sum);
        }
        // 清空IDF缓存，强制重新计算
        idfCache.clear();
    }

    /**
     * 获取特征统计信息
     */
    public Map<String, Object> getFeatureStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("documentFrequencySize", documentFrequency.size());
        stats.put("idfCacheSize", idfCache.size());
        stats.put("enableTfidf", enableTfidf);
        stats.put("enableWord2vec", enableWord2vec);
        stats.put("enableNgram", enableNgram);
        stats.put("ngramSize", ngramSize);
        stats.put("maxFeatures", maxFeatures);
        stats.put("word2vecDimension", word2vecDimension);
        return stats;
    }
}