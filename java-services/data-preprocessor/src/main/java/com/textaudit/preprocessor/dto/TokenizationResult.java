package com.textaudit.preprocessor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 分词结果DTO
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationResult {

    /**
     * 原始分词结果
     */
    private List<String> tokens;

    /**
     * 过滤停用词后的分词结果
     */
    private List<String> filteredTokens;

    /**
     * 词性标注结果
     * Key: 词语, Value: 词性
     */
    private Map<String, String> posTagging;

    /**
     * 命名实体识别结果
     * Key: 实体, Value: 实体类型
     */
    private Map<String, String> namedEntities;

    /**
     * 获取词汇总数
     */
    public int getTotalTokenCount() {
        return tokens != null ? tokens.size() : 0;
    }

    /**
     * 获取过滤后词汇总数
     */
    public int getFilteredTokenCount() {
        return filteredTokens != null ? filteredTokens.size() : 0;
    }

    /**
     * 获取唯一词汇数
     */
    public int getUniqueTokenCount() {
        return tokens != null ? (int) tokens.stream().distinct().count() : 0;
    }

    /**
     * 获取过滤后唯一词汇数
     */
    public int getUniqueFilteredTokenCount() {
        return filteredTokens != null ? (int) filteredTokens.stream().distinct().count() : 0;
    }

    /**
     * 获取词性标注数量
     */
    public int getPosTagCount() {
        return posTagging != null ? posTagging.size() : 0;
    }

    /**
     * 获取命名实体数量
     */
    public int getNamedEntityCount() {
        return namedEntities != null ? namedEntities.size() : 0;
    }

    /**
     * 是否包含词性标注
     */
    public boolean hasPosTagging() {
        return posTagging != null && !posTagging.isEmpty();
    }

    /**
     * 是否包含命名实体
     */
    public boolean hasNamedEntities() {
        return namedEntities != null && !namedEntities.isEmpty();
    }
}