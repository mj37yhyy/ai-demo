package config

import (
	"fmt"
	"strings"

	"github.com/spf13/viper"
)

// Config 应用配置结构
type Config struct {
	Server    ServerConfig    `mapstructure:"server"`
	Database  DatabaseConfig  `mapstructure:"database"`
	Redis     RedisConfig     `mapstructure:"redis"`
	Model     ModelConfig     `mapstructure:"model"`
	Inference InferenceConfig `mapstructure:"inference"`
	Log       LogConfig       `mapstructure:"log"`
}

// ServerConfig 服务器配置
type ServerConfig struct {
	Port         int    `mapstructure:"port"`
	Mode         string `mapstructure:"mode"`
	ReadTimeout  int    `mapstructure:"read_timeout"`
	WriteTimeout int    `mapstructure:"write_timeout"`
	IdleTimeout  int    `mapstructure:"idle_timeout"`
}

// DatabaseConfig 数据库配置
type DatabaseConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	User     string `mapstructure:"user"`
	Password string `mapstructure:"password"`
	DBName   string `mapstructure:"dbname"`
	Charset  string `mapstructure:"charset"`
	ParseTime bool  `mapstructure:"parse_time"`
	Loc      string `mapstructure:"loc"`
}

// RedisConfig Redis配置
type RedisConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
}

// ModelConfig 模型配置
type ModelConfig struct {
	StoragePath     string `mapstructure:"storage_path"`
	CacheTTL        int    `mapstructure:"cache_ttl"`
	MaxLoadedModels int    `mapstructure:"max_loaded_models"`
	LoadTimeout     int    `mapstructure:"load_timeout"`
}

// InferenceConfig 推理配置
type InferenceConfig struct {
	MaxBatchSize    int `mapstructure:"max_batch_size"`
	TimeoutSeconds  int `mapstructure:"timeout_seconds"`
	MaxConcurrency  int `mapstructure:"max_concurrency"`
	ResultCacheTTL  int `mapstructure:"result_cache_ttl"`
	HistoryRetention int `mapstructure:"history_retention"`
}

// LogConfig 日志配置
type LogConfig struct {
	Level  string `mapstructure:"level"`
	Format string `mapstructure:"format"`
	Output string `mapstructure:"output"`
}

// Load 加载配置
func Load() (*Config, error) {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")
	viper.AddConfigPath(".")
	viper.AddConfigPath("./config")
	viper.AddConfigPath("/etc/textaudit/")

	// 设置默认值
	setDefaults()

	// 环境变量支持
	viper.SetEnvPrefix("TEXTAUDIT")
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv()

	// 读取配置文件
	if err := viper.ReadInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, fmt.Errorf("读取配置文件失败: %w", err)
		}
	}

	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, fmt.Errorf("解析配置失败: %w", err)
	}

	return &config, nil
}

// setDefaults 设置默认配置值
func setDefaults() {
	// 服务器配置
	viper.SetDefault("server.port", 8082)
	viper.SetDefault("server.mode", "debug")
	viper.SetDefault("server.read_timeout", 30)
	viper.SetDefault("server.write_timeout", 30)
	viper.SetDefault("server.idle_timeout", 60)

	// 数据库配置
	viper.SetDefault("database.host", "localhost")
	viper.SetDefault("database.port", 3306)
	viper.SetDefault("database.user", "audit_user")
	viper.SetDefault("database.password", "audit_pass")
	viper.SetDefault("database.dbname", "text_audit")
	viper.SetDefault("database.charset", "utf8mb4")
	viper.SetDefault("database.parse_time", true)
	viper.SetDefault("database.loc", "Local")

	// Redis配置
	viper.SetDefault("redis.host", "localhost")
	viper.SetDefault("redis.port", 6379)
	viper.SetDefault("redis.password", "")
	viper.SetDefault("redis.db", 0)

	// 模型配置
	viper.SetDefault("model.storage_path", "./models")
	viper.SetDefault("model.cache_ttl", 3600)
	viper.SetDefault("model.max_loaded_models", 10)
	viper.SetDefault("model.load_timeout", 300)

	// 推理配置
	viper.SetDefault("inference.max_batch_size", 100)
	viper.SetDefault("inference.timeout_seconds", 30)
	viper.SetDefault("inference.max_concurrency", 10)
	viper.SetDefault("inference.result_cache_ttl", 300)
	viper.SetDefault("inference.history_retention", 7)

	// 日志配置
	viper.SetDefault("log.level", "info")
	viper.SetDefault("log.format", "json")
	viper.SetDefault("log.output", "stdout")
}

// GetDSN 获取数据库连接字符串
func (d *DatabaseConfig) GetDSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=%s&parseTime=%t&loc=%s",
		d.User, d.Password, d.Host, d.Port, d.DBName, d.Charset, d.ParseTime, d.Loc)
}

// GetRedisAddr 获取Redis地址
func (r *RedisConfig) GetRedisAddr() string {
	return fmt.Sprintf("%s:%d", r.Host, r.Port)
}