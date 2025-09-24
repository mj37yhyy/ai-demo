package com.textaudit.preprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.textaudit.preprocessor.dto.ProcessingResult;
import com.textaudit.preprocessor.dto.TextFeatures;
import com.textaudit.preprocessor.dto.TokenizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文本处理服务
 * 负责文本清洗、分词、特征提取等核心功能
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextProcessingService {

    private final ObjectMapper objectMapper;
    private final StopWordsService stopWordsService;
    private final FeatureExtractionService featureExtractionService;

    // 配置参数
    @Value("${app.text-processing.cleaning.remove-html:true}")
    private boolean removeHtml;

    @Value("${app.text-processing.cleaning.remove-urls:true}")
    private boolean removeUrls;

    @Value("${app.text-processing.cleaning.remove-emails:true}")
    private boolean removeEmails;

    @Value("${app.text-processing.cleaning.remove-phone-numbers:true}")
    private boolean removePhoneNumbers;

    @Value("${app.text-processing.cleaning.remove-special-chars:true}")
    private boolean removeSpecialChars;

    @Value("${app.text-processing.cleaning.normalize-whitespace:true}")
    private boolean normalizeWhitespace;

    @Value("${app.text-processing.cleaning.convert-to-lowercase:false}")
    private boolean convertToLowercase;

    @Value("${app.text-processing.cleaning.min-text-length:10}")
    private int minTextLength;

    @Value("${app.text-processing.cleaning.max-text-length:10000}")
    private int maxTextLength;

    @Value("${app.text-processing.tokenization.min-word-length:1}")
    private int minWordLength;

    @Value("${app.text-processing.tokenization.max-word-length:20}")
    private int maxWordLength;

    // 正则表达式模式
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}|\\d{3,4}-\\d{7,8}");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\u4e00-\\u9fa5\\w\\s.,!?;:\"'()\\[\\]{}\\-]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * 处理文本
     * 
     * @param rawText 原始文本
     * @param source 数据源
     * @param metadata 元数据
     * @return 处理结果
     */
    public ProcessingResult processText(String rawText, String source, Map<String, Object> metadata) {
        if (StringUtils.isBlank(rawText)) {
            throw new IllegalArgumentException("Raw text cannot be blank");
        }

        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 文本清洗
            String cleanedText = cleanText(rawText);
            
            // 2. 验证文本长度
            if (cleanedText.length() < minTextLength || cleanedText.length() > maxTextLength) {
                log.warn("Text length {} is out of range [{}, {}]", 
                    cleanedText.length(), minTextLength, maxTextLength);
                return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("Text length out of range")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            }

            // 3. 分词
            TokenizationResult tokenizationResult = tokenizeText(cleanedText);

            // 4. 特征提取
            TextFeatures features = featureExtractionService.extractFeatures(
                cleanedText, tokenizationResult);

            // 5. 构建处理结果
            ProcessingResult result = ProcessingResult.builder()
                .success(true)
                .originalText(rawText)
                .cleanedText(cleanedText)
                .tokenizationResult(tokenizationResult)
                .features(features)
                .source(source)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();

            log.debug("Text processing completed in {}ms for source: {}", 
                result.getProcessingTimeMs(), source);

            return result;

        } catch (Exception e) {
            log.error("Error processing text from source: {}", source, e);
            return ProcessingResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * 清洗文本
     * 
     * @param text 原始文本
     * @return 清洗后的文本
     */
    @Cacheable(value = "cleanedText", key = "#text.hashCode()")
    public String cleanText(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }

        String result = text;

        // HTML标签清理
        if (removeHtml) {
            result = HTML_PATTERN.matcher(result).replaceAll("");
            result = StringEscapeUtils.unescapeHtml4(result);
        }

        // URL清理
        if (removeUrls) {
            result = URL_PATTERN.matcher(result).replaceAll("");
        }

        // 邮箱清理
        if (removeEmails) {
            result = EMAIL_PATTERN.matcher(result).replaceAll("");
        }

        // 电话号码清理
        if (removePhoneNumbers) {
            result = PHONE_PATTERN.matcher(result).replaceAll("");
        }

        // 特殊字符清理
        if (removeSpecialChars) {
            result = SPECIAL_CHARS_PATTERN.matcher(result).replaceAll(" ");
        }

        // 空白字符规范化
        if (normalizeWhitespace) {
            result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
            result = result.trim();
        }

        // 转换为小写
        if (convertToLowercase) {
            result = result.toLowerCase();
        }

        return result;
    }

    /**
     * 分词处理
     * 
     * @param text 清洗后的文本
     * @return 分词结果
     */
    @Cacheable(value = "tokenization", key = "#text.hashCode()")
    public TokenizationResult tokenizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return TokenizationResult.builder()
                .tokens(Collections.emptyList())
                .filteredTokens(Collections.emptyList())
                .build();
        }

        try {
            // 使用HanLP进行分词
            List<Term> terms = HanLP.segment(text);
            
            // 提取词语
            List<String> tokens = terms.stream()
                .map(term -> term.word)
                .filter(word -> word.length() >= minWordLength && word.length() <= maxWordLength)
                .collect(Collectors.toList());

            // 过滤停用词
            List<String> filteredTokens = tokens.stream()
                .filter(token -> !stopWordsService.isStopWord(token))
                .collect(Collectors.toList());

            // 词性标注
            Map<String, String> posTagging = terms.stream()
                .collect(Collectors.toMap(
                    term -> term.word,
                    term -> term.nature.toString(),
                    (existing, replacement) -> existing
                ));

            return TokenizationResult.builder()
                .tokens(tokens)
                .filteredTokens(filteredTokens)
                .posTagging(posTagging)
                .build();

        } catch (Exception e) {
            log.error("Error during tokenization", e);
            return TokenizationResult.builder()
                .tokens(Collections.emptyList())
                .filteredTokens(Collections.emptyList())
                .build();
        }
    }

    /**
     * 批量处理文本
     * 
     * @param texts 文本列表
     * @param source 数据源
     * @return 处理结果列表
     */
    public List<ProcessingResult> batchProcessTexts(List<String> texts, String source) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        return texts.parallelStream()
            .map(text -> processText(text, source, null))
            .collect(Collectors.toList());
    }

    /**
     * 序列化对象为JSON字符串
     * 
     * @param object 对象
     * @return JSON字符串
     */
    public String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            return "{}";
        }
    }

    /**
     * 从JSON字符串反序列化对象
     * 
     * @param json JSON字符串
     * @param clazz 目标类型
     * @return 反序列化的对象
     */
    public <T> T fromJsonString(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON to object", e);
            return null;
        }
    }

    /**
     * 计算文本相似度
     * 
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度分数 (0-1)
     */
    public double calculateSimilarity(String text1, String text2) {
        if (StringUtils.isBlank(text1) || StringUtils.isBlank(text2)) {
            return 0.0;
        }

        TokenizationResult tokens1 = tokenizeText(text1);
        TokenizationResult tokens2 = tokenizeText(text2);

        Set<String> set1 = new HashSet<>(tokens1.getFilteredTokens());
        Set<String> set2 = new HashSet<>(tokens2.getFilteredTokens());

        // 计算Jaccard相似度
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 检测文本语言
     * 
     * @param text 文本
     * @return 语言代码
     */
    public String detectLanguage(String text) {
        if (StringUtils.isBlank(text)) {
            return "unknown";
        }

        // 简单的中文检测
        long chineseCharCount = text.chars()
            .filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF)
            .count();

        double chineseRatio = (double) chineseCharCount / text.length();

        if (chineseRatio > 0.3) {
            return "zh";
        } else {
            return "en";
        }
    }
}