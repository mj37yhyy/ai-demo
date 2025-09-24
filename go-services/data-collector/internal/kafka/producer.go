package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/IBM/sarama"
	"github.com/sirupsen/logrus"
)

// Producer Kafka生产者接口
type Producer interface {
	SendMessage(ctx context.Context, topic string, key string, value interface{}) error
	SendRawMessage(ctx context.Context, topic string, key string, value []byte) error
	Close() error
}

// SaramaProducer Sarama Kafka生产者实现
type SaramaProducer struct {
	producer sarama.SyncProducer
	logger   *logrus.Logger
}

// NewSaramaProducer 创建Sarama Kafka生产者
func NewSaramaProducer(brokers []string, config *sarama.Config) (*SaramaProducer, error) {
	if config == nil {
		config = sarama.NewConfig()
		config.Producer.RequiredAcks = sarama.WaitForAll
		config.Producer.Retry.Max = 5
		config.Producer.Return.Successes = true
		config.Producer.Compression = sarama.CompressionSnappy
		config.Producer.Flush.Frequency = 500 * time.Millisecond
		config.Producer.Flush.Messages = 100
		config.Producer.MaxMessageBytes = 1000000
		config.Version = sarama.V2_6_0_0
	}

	producer, err := sarama.NewSyncProducer(brokers, config)
	if err != nil {
		return nil, fmt.Errorf("failed to create Kafka producer: %w", err)
	}

	logger := logrus.New()
	logger.SetLevel(logrus.InfoLevel)

	return &SaramaProducer{
		producer: producer,
		logger:   logger,
	}, nil
}

// SendMessage 发送消息（自动序列化为JSON）
func (p *SaramaProducer) SendMessage(ctx context.Context, topic string, key string, value interface{}) error {
	// 序列化消息
	valueBytes, err := json.Marshal(value)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %w", err)
	}

	return p.SendRawMessage(ctx, topic, key, valueBytes)
}

// SendRawMessage 发送原始字节消息
func (p *SaramaProducer) SendRawMessage(ctx context.Context, topic string, key string, value []byte) error {
	msg := &sarama.ProducerMessage{
		Topic:     topic,
		Key:       sarama.StringEncoder(key),
		Value:     sarama.ByteEncoder(value),
		Timestamp: time.Now(),
	}

	// 添加请求ID到消息头
	if requestID := ctx.Value("request_id"); requestID != nil {
		msg.Headers = []sarama.RecordHeader{
			{
				Key:   []byte("request_id"),
				Value: []byte(fmt.Sprintf("%v", requestID)),
			},
		}
	}

	partition, offset, err := p.producer.SendMessage(msg)
	if err != nil {
		p.logger.WithFields(logrus.Fields{
			"topic": topic,
			"key":   key,
			"error": err,
		}).Error("Failed to send message to Kafka")
		return fmt.Errorf("failed to send message to Kafka: %w", err)
	}

	p.logger.WithFields(logrus.Fields{
		"topic":     topic,
		"key":       key,
		"partition": partition,
		"offset":    offset,
	}).Debug("Message sent to Kafka successfully")

	return nil
}

// Close 关闭生产者
func (p *SaramaProducer) Close() error {
	if p.producer != nil {
		return p.producer.Close()
	}
	return nil
}

// MessageEnvelope 消息包装器
type MessageEnvelope struct {
	MessageID   string                 `json:"message_id"`
	MessageType string                 `json:"message_type"`
	Source      string                 `json:"source"`
	Timestamp   int64                  `json:"timestamp"`
	Data        interface{}            `json:"data"`
	Metadata    map[string]interface{} `json:"metadata,omitempty"`
}

// NewMessageEnvelope 创建消息包装器
func NewMessageEnvelope(messageType, source string, data interface{}) *MessageEnvelope {
	return &MessageEnvelope{
		MessageID:   generateMessageID(),
		MessageType: messageType,
		Source:      source,
		Timestamp:   time.Now().Unix(),
		Data:        data,
		Metadata:    make(map[string]interface{}),
	}
}

// AddMetadata 添加元数据
func (e *MessageEnvelope) AddMetadata(key string, value interface{}) {
	if e.Metadata == nil {
		e.Metadata = make(map[string]interface{})
	}
	e.Metadata[key] = value
}

// generateMessageID 生成消息ID
func generateMessageID() string {
	return fmt.Sprintf("msg_%d_%d", time.Now().UnixNano(), time.Now().Nanosecond()%1000)
}

// Topics 定义Kafka主题常量
const (
	TopicRawText       = "raw-text-topic"
	TopicProcessedText = "processed-text-topic"
	TopicAuditRequest  = "text-audit.audit-request"
	TopicAuditResult   = "text-audit.audit-result"
	TopicModelUpdate   = "text-audit.model-update"
	TopicTrainingTask  = "text-audit.training-task"
	TopicSystemEvent   = "text-audit.system-event"
)

// MessageTypes 定义消息类型常量
const (
	MessageTypeRawText      = "raw_text"
	MessageTypeProcessed    = "processed_text"
	MessageTypeAuditRequest = "audit_request"
	MessageTypeAuditResult  = "audit_result"
	MessageTypeModelUpdate  = "model_update"
	MessageTypeTrainingTask = "training_task"
	MessageTypeSystemEvent  = "system_event"
)