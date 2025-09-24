package com.textaudit.preprocessor.service;

import com.textaudit.preprocessor.dto.ProcessingResult;
import com.textaudit.preprocessor.dto.TextFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String PROCESSING_RESULT_PREFIX = "processing_result:";
    private static final String TEXT_FEATURES_PREFIX = "text_features:";
    private static final String STATISTICS_PREFIX = "statistics:";
    private static final String WORD_FREQUENCY_PREFIX = "word_freq:";

    /**
     * 缓存文本处理结果
     */
    @CachePut(value = "processingResults", key = "#textId")
    public ProcessingResult cacheProcessingResult(String textId, ProcessingResult result) {
        try {
            String key = PROCESSING_RESULT_PREFIX + textId;
            redisTemplate.opsForValue().set(key, result, Duration.ofHours(2));
            log.debug("缓存文本处理结果: {}", textId);
            return result;
        } catch (Exception e) {
            log.error("缓存文本处理结果失败: {}", textId, e);
            return result;
        }
    }

    /**
     * 获取缓存的文本处理结果
     */
    @Cacheable(value = "processingResults", key = "#textId")
    public ProcessingResult getProcessingResult(String textId) {
        try {
            String key = PROCESSING_RESULT_PREFIX + textId;
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof ProcessingResult) {
                log.debug("从缓存获取文本处理结果: {}", textId);
                return (ProcessingResult) result;
            }
        } catch (Exception e) {
            log.error("获取缓存的文本处理结果失败: {}", textId, e);
        }
        return null;
    }

    /**
     * 缓存文本特征
     */
    @CachePut(value = "textFeatures", key = "#textId")
    public TextFeatures cacheTextFeatures(String textId, TextFeatures features) {
        try {
            String key = TEXT_FEATURES_PREFIX + textId;
            redisTemplate.opsForValue().set(key, features, Duration.ofHours(6));
            log.debug("缓存文本特征: {}", textId);
            return features;
        } catch (Exception e) {
            log.error("缓存文本特征失败: {}", textId, e);
            return features;
        }
    }

    /**
     * 获取缓存的文本特征
     */
    @Cacheable(value = "textFeatures", key = "#textId")
    public TextFeatures getTextFeatures(String textId) {
        try {
            String key = TEXT_FEATURES_PREFIX + textId;
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof TextFeatures) {
                log.debug("从缓存获取文本特征: {}", textId);
                return (TextFeatures) result;
            }
        } catch (Exception e) {
            log.error("获取缓存的文本特征失败: {}", textId, e);
        }
        return null;
    }

    /**
     * 缓存统计信息
     */
    public void cacheStatistics(String key, Object value, Duration ttl) {
        try {
            String cacheKey = STATISTICS_PREFIX + key;
            redisTemplate.opsForValue().set(cacheKey, value, ttl);
            log.debug("缓存统计信息: {}", key);
        } catch (Exception e) {
            log.error("缓存统计信息失败: {}", key, e);
        }
    }

    /**
     * 获取缓存的统计信息
     */
    public Object getStatistics(String key) {
        try {
            String cacheKey = STATISTICS_PREFIX + key;
            Object result = redisTemplate.opsForValue().get(cacheKey);
            if (result != null) {
                log.debug("从缓存获取统计信息: {}", key);
            }
            return result;
        } catch (Exception e) {
            log.error("获取缓存的统计信息失败: {}", key, e);
            return null;
        }
    }

    /**
     * 缓存词频统计
     */
    public void cacheWordFrequency(String word, Integer frequency) {
        try {
            String key = WORD_FREQUENCY_PREFIX + word;
            redisTemplate.opsForValue().set(key, frequency, Duration.ofDays(1));
        } catch (Exception e) {
            log.error("缓存词频统计失败: {}", word, e);
        }
    }

    /**
     * 获取词频统计
     */
    public Integer getWordFrequency(String word) {
        try {
            String key = WORD_FREQUENCY_PREFIX + word;
            Object result = redisTemplate.opsForValue().get(key);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            log.error("获取词频统计失败: {}", word, e);
        }
        return 0;
    }

    /**
     * 批量缓存词频统计
     */
    public void batchCacheWordFrequency(java.util.Map<String, Integer> wordFreqMap) {
        try {
            wordFreqMap.forEach((word, freq) -> {
                String key = WORD_FREQUENCY_PREFIX + word;
                redisTemplate.opsForValue().set(key, freq, Duration.ofDays(1));
            });
            log.debug("批量缓存词频统计，数量: {}", wordFreqMap.size());
        } catch (Exception e) {
            log.error("批量缓存词频统计失败", e);
        }
    }

    /**
     * 删除缓存
     */
    @CacheEvict(value = {"processingResults", "textFeatures"}, key = "#textId")
    public void evictCache(String textId) {
        try {
            String processingKey = PROCESSING_RESULT_PREFIX + textId;
            String featuresKey = TEXT_FEATURES_PREFIX + textId;
            redisTemplate.delete(List.of(processingKey, featuresKey));
            log.debug("删除缓存: {}", textId);
        } catch (Exception e) {
            log.error("删除缓存失败: {}", textId, e);
        }
    }

    /**
     * 清空所有缓存
     */
    @CacheEvict(value = {"processingResults", "textFeatures"}, allEntries = true)
    public void clearAllCache() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            log.info("清空所有缓存");
        } catch (Exception e) {
            log.error("清空缓存失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public java.util.Map<String, Object> getCacheStats() {
        try {
            java.util.Map<String, Object> stats = new java.util.HashMap<>();
            
            // 统计不同类型的缓存数量
            Set<String> processingKeys = redisTemplate.keys(PROCESSING_RESULT_PREFIX + "*");
            Set<String> featuresKeys = redisTemplate.keys(TEXT_FEATURES_PREFIX + "*");
            Set<String> statisticsKeys = redisTemplate.keys(STATISTICS_PREFIX + "*");
            Set<String> wordFreqKeys = redisTemplate.keys(WORD_FREQUENCY_PREFIX + "*");
            
            stats.put("processingResultsCount", processingKeys != null ? processingKeys.size() : 0);
            stats.put("textFeaturesCount", featuresKeys != null ? featuresKeys.size() : 0);
            stats.put("statisticsCount", statisticsKeys != null ? statisticsKeys.size() : 0);
            stats.put("wordFrequencyCount", wordFreqKeys != null ? wordFreqKeys.size() : 0);
            
            // 总缓存数量
            Set<String> allKeys = redisTemplate.keys("*");
            stats.put("totalCacheCount", allKeys != null ? allKeys.size() : 0);
            
            return stats;
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查缓存是否存在失败: {}", key, e);
            return false;
        }
    }

    /**
     * 设置缓存过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("设置缓存过期时间失败: {}", key, e);
            return false;
        }
    }
}