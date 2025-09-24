package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	HTTP     HTTPConfig     `yaml:"http"`
	GRPC     GRPCConfig     `yaml:"grpc"`
	Database DatabaseConfig `yaml:"database"`
	Redis    RedisConfig    `yaml:"redis"`
	Kafka    KafkaConfig    `yaml:"kafka"`
	Collector CollectorConfig `yaml:"collector"`
}

type HTTPConfig struct {
	Address string `yaml:"address"`
}

type GRPCConfig struct {
	Address string `yaml:"address"`
}

type DatabaseConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Username string `yaml:"username"`
	Password string `yaml:"password"`
	Database string `yaml:"database"`
}

type RedisConfig struct {
	Address  string `yaml:"address"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

type KafkaConfig struct {
	Brokers   []string `yaml:"brokers"`
	RawTopic  string   `yaml:"raw_topic"`
}

type CollectorConfig struct {
	RateLimit       int           `yaml:"rate_limit"`
	ConcurrentLimit int           `yaml:"concurrent_limit"`
	Timeout         time.Duration `yaml:"timeout"`
	UserAgents      []string      `yaml:"user_agents"`
	ProxyURLs       []string      `yaml:"proxy_urls"`
}

func Load() (*Config, error) {
	cfg := &Config{
		HTTP: HTTPConfig{
			Address: getEnv("HTTP_ADDRESS", ":8080"),
		},
		GRPC: GRPCConfig{
			Address: getEnv("GRPC_ADDRESS", ":9090"),
		},
		Database: DatabaseConfig{
			Host:     getEnv("DB_HOST", "localhost"),
			Port:     getEnvInt("DB_PORT", 3306),
			Username: getEnv("DB_USERNAME", "audit_user"),
			Password: getEnv("DB_PASSWORD", "audit_pass"),
			Database: getEnv("DB_DATABASE", "text_audit"),
		},
		Redis: RedisConfig{
			Address:  getEnv("REDIS_ADDRESS", "localhost:6379"),
			Password: getEnv("REDIS_PASSWORD", ""),
			DB:       getEnvInt("REDIS_DB", 0),
		},
		Kafka: KafkaConfig{
			Brokers:  []string{getEnv("KAFKA_BROKERS", "localhost:9092")},
			RawTopic: getEnv("KAFKA_RAW_TOPIC", "raw-text-topic"),
		},
		Collector: CollectorConfig{
			RateLimit:       getEnvInt("COLLECTOR_RATE_LIMIT", 5),
			ConcurrentLimit: getEnvInt("COLLECTOR_CONCURRENT_LIMIT", 10),
			Timeout:         time.Duration(getEnvInt("COLLECTOR_TIMEOUT_SECONDS", 30)) * time.Second,
			UserAgents: []string{
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
				"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
				"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
			},
			ProxyURLs: []string{},
		},
	}

	return cfg, nil
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}