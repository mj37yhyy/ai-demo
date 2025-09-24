package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/go-redis/redis/v8"
)

// CacheRepository 缓存仓库接口
type CacheRepository interface {
	Set(ctx context.Context, key string, value interface{}, expiration time.Duration) error
	Get(ctx context.Context, key string, dest interface{}) error
	Delete(ctx context.Context, key string) error
	Exists(ctx context.Context, key string) (bool, error)
	SetNX(ctx context.Context, key string, value interface{}, expiration time.Duration) (bool, error)
	Expire(ctx context.Context, key string, expiration time.Duration) error
	Keys(ctx context.Context, pattern string) ([]string, error)
	DeletePattern(ctx context.Context, pattern string) error
	Incr(ctx context.Context, key string) (int64, error)
	Decr(ctx context.Context, key string) (int64, error)
	HSet(ctx context.Context, key string, field string, value interface{}) error
	HGet(ctx context.Context, key string, field string, dest interface{}) error
	HGetAll(ctx context.Context, key string) (map[string]string, error)
	HDel(ctx context.Context, key string, fields ...string) error
}

// cacheRepository 缓存仓库实现
type cacheRepository struct {
	client *redis.Client
}

// NewCacheRepository 创建缓存仓库
func NewCacheRepository(client *redis.Client) CacheRepository {
	return &cacheRepository{client: client}
}

// Set 设置缓存
func (r *cacheRepository) Set(ctx context.Context, key string, value interface{}, expiration time.Duration) error {
	data, err := json.Marshal(value)
	if err != nil {
		return fmt.Errorf("序列化数据失败: %w", err)
	}
	
	if err := r.client.Set(ctx, key, data, expiration).Err(); err != nil {
		return fmt.Errorf("设置缓存失败: %w", err)
	}
	
	return nil
}

// Get 获取缓存
func (r *cacheRepository) Get(ctx context.Context, key string, dest interface{}) error {
	data, err := r.client.Get(ctx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return nil // 缓存不存在
		}
		return fmt.Errorf("获取缓存失败: %w", err)
	}
	
	if err := json.Unmarshal([]byte(data), dest); err != nil {
		return fmt.Errorf("反序列化数据失败: %w", err)
	}
	
	return nil
}

// Delete 删除缓存
func (r *cacheRepository) Delete(ctx context.Context, key string) error {
	if err := r.client.Del(ctx, key).Err(); err != nil {
		return fmt.Errorf("删除缓存失败: %w", err)
	}
	return nil
}

// Exists 检查缓存是否存在
func (r *cacheRepository) Exists(ctx context.Context, key string) (bool, error) {
	count, err := r.client.Exists(ctx, key).Result()
	if err != nil {
		return false, fmt.Errorf("检查缓存存在失败: %w", err)
	}
	return count > 0, nil
}

// SetNX 设置缓存（仅当不存在时）
func (r *cacheRepository) SetNX(ctx context.Context, key string, value interface{}, expiration time.Duration) (bool, error) {
	data, err := json.Marshal(value)
	if err != nil {
		return false, fmt.Errorf("序列化数据失败: %w", err)
	}
	
	result, err := r.client.SetNX(ctx, key, data, expiration).Result()
	if err != nil {
		return false, fmt.Errorf("设置缓存失败: %w", err)
	}
	
	return result, nil
}

// Expire 设置过期时间
func (r *cacheRepository) Expire(ctx context.Context, key string, expiration time.Duration) error {
	if err := r.client.Expire(ctx, key, expiration).Err(); err != nil {
		return fmt.Errorf("设置过期时间失败: %w", err)
	}
	return nil
}

// Keys 获取匹配模式的键
func (r *cacheRepository) Keys(ctx context.Context, pattern string) ([]string, error) {
	keys, err := r.client.Keys(ctx, pattern).Result()
	if err != nil {
		return nil, fmt.Errorf("获取键失败: %w", err)
	}
	return keys, nil
}

// DeletePattern 删除匹配模式的键
func (r *cacheRepository) DeletePattern(ctx context.Context, pattern string) error {
	keys, err := r.Keys(ctx, pattern)
	if err != nil {
		return err
	}
	
	if len(keys) > 0 {
		if err := r.client.Del(ctx, keys...).Err(); err != nil {
			return fmt.Errorf("删除缓存失败: %w", err)
		}
	}
	
	return nil
}

// Incr 递增
func (r *cacheRepository) Incr(ctx context.Context, key string) (int64, error) {
	result, err := r.client.Incr(ctx, key).Result()
	if err != nil {
		return 0, fmt.Errorf("递增失败: %w", err)
	}
	return result, nil
}

// Decr 递减
func (r *cacheRepository) Decr(ctx context.Context, key string) (int64, error) {
	result, err := r.client.Decr(ctx, key).Result()
	if err != nil {
		return 0, fmt.Errorf("递减失败: %w", err)
	}
	return result, nil
}

// HSet 设置哈希字段
func (r *cacheRepository) HSet(ctx context.Context, key string, field string, value interface{}) error {
	data, err := json.Marshal(value)
	if err != nil {
		return fmt.Errorf("序列化数据失败: %w", err)
	}
	
	if err := r.client.HSet(ctx, key, field, data).Err(); err != nil {
		return fmt.Errorf("设置哈希字段失败: %w", err)
	}
	
	return nil
}

// HGet 获取哈希字段
func (r *cacheRepository) HGet(ctx context.Context, key string, field string, dest interface{}) error {
	data, err := r.client.HGet(ctx, key, field).Result()
	if err != nil {
		if err == redis.Nil {
			return nil // 字段不存在
		}
		return fmt.Errorf("获取哈希字段失败: %w", err)
	}
	
	if err := json.Unmarshal([]byte(data), dest); err != nil {
		return fmt.Errorf("反序列化数据失败: %w", err)
	}
	
	return nil
}

// HGetAll 获取所有哈希字段
func (r *cacheRepository) HGetAll(ctx context.Context, key string) (map[string]string, error) {
	result, err := r.client.HGetAll(ctx, key).Result()
	if err != nil {
		return nil, fmt.Errorf("获取所有哈希字段失败: %w", err)
	}
	return result, nil
}

// HDel 删除哈希字段
func (r *cacheRepository) HDel(ctx context.Context, key string, fields ...string) error {
	if err := r.client.HDel(ctx, key, fields...).Err(); err != nil {
		return fmt.Errorf("删除哈希字段失败: %w", err)
	}
	return nil
}