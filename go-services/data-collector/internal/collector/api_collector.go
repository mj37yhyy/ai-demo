package collector

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"golang.org/x/time/rate"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

type APICollector struct {
	config  *config.Config
	client  *http.Client
	limiter *rate.Limiter
}

type APIResponse struct {
	Data    []APITextItem `json:"data"`
	HasMore bool          `json:"has_more"`
	NextURL string        `json:"next_url"`
}

type APITextItem struct {
	ID      string            `json:"id"`
	Content string            `json:"content"`
	Source  string            `json:"source"`
	Meta    map[string]string `json:"meta"`
}

func NewAPICollector(cfg *config.Config) (*APICollector, error) {
	client := &http.Client{
		Timeout: cfg.Collector.Timeout,
	}

	// 创建速率限制器
	limiter := rate.NewLimiter(rate.Limit(cfg.Collector.RateLimit), 1)

	return &APICollector{
		config:  cfg,
		client:  client,
		limiter: limiter,
	}, nil
}

func (c *APICollector) Collect(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	logrus.WithField("url", source.Url).Info("Starting API collection")

	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 1000 // 默认最大采集数量
	}

	currentURL := source.Url
	
	for collected < maxCount && currentURL != "" {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		// 速率限制
		if err := c.limiter.Wait(ctx); err != nil {
			return fmt.Errorf("rate limiter error: %w", err)
		}

		// 发送请求
		texts, nextURL, err := c.fetchTextsFromAPI(ctx, currentURL, source.Parameters)
		if err != nil {
			logrus.WithError(err).WithField("url", currentURL).Error("Failed to fetch from API")
			return fmt.Errorf("failed to fetch from API: %w", err)
		}

		// 处理返回的文本
		for _, text := range texts {
			if collected >= maxCount {
				break
			}

			// 应用过滤器
			if !c.applyFilters(text.Content, config.Filters) {
				continue
			}

			rawText := &pb.RawText{
				Id:        uuid.New().String(),
				Content:   text.Content,
				Source:    fmt.Sprintf("api:%s", text.Source),
				Timestamp: time.Now().UnixMilli(),
				Metadata:  text.Meta,
			}

			select {
			case textChan <- rawText:
				collected++
				logrus.WithFields(logrus.Fields{
					"collected": collected,
					"text_id":   rawText.Id,
				}).Debug("Collected text from API")
			case <-ctx.Done():
				return ctx.Err()
			}
		}

		currentURL = nextURL
		
		// 如果没有更多数据，退出循环
		if nextURL == "" {
			break
		}
	}

	logrus.WithField("total_collected", collected).Info("API collection completed")
	return nil
}

func (c *APICollector) fetchTextsFromAPI(ctx context.Context, apiURL string, params map[string]string) ([]APITextItem, string, error) {
	// 构建请求URL
	u, err := url.Parse(apiURL)
	if err != nil {
		return nil, "", fmt.Errorf("invalid URL: %w", err)
	}

	// 添加参数
	query := u.Query()
	for key, value := range params {
		query.Set(key, value)
	}
	u.RawQuery = query.Encode()

	// 创建请求
	req, err := http.NewRequestWithContext(ctx, "GET", u.String(), nil)
	if err != nil {
		return nil, "", fmt.Errorf("failed to create request: %w", err)
	}

	// 设置请求头
	c.setRequestHeaders(req)

	// 发送请求
	resp, err := c.client.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// 检查响应状态
	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("API returned status %d", resp.StatusCode)
	}

	// 读取响应体
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, "", fmt.Errorf("failed to read response: %w", err)
	}

	// 解析响应
	var apiResp APIResponse
	if err := json.Unmarshal(body, &apiResp); err != nil {
		// 如果不是标准格式，尝试解析为简单的文本数组
		return c.parseSimpleResponse(body)
	}

	return apiResp.Data, apiResp.NextURL, nil
}

func (c *APICollector) parseSimpleResponse(body []byte) ([]APITextItem, string, error) {
	// 尝试解析为字符串数组
	var texts []string
	if err := json.Unmarshal(body, &texts); err != nil {
		// 尝试解析为单个字符串
		var text string
		if err := json.Unmarshal(body, &text); err != nil {
			return nil, "", fmt.Errorf("failed to parse response: %w", err)
		}
		texts = []string{text}
	}

	var items []APITextItem
	for i, text := range texts {
		items = append(items, APITextItem{
			ID:      fmt.Sprintf("api_%d", i),
			Content: text,
			Source:  "api",
			Meta:    make(map[string]string),
		})
	}

	return items, "", nil
}

func (c *APICollector) setRequestHeaders(req *http.Request) {
	// 设置User-Agent
	if len(c.config.Collector.UserAgents) > 0 {
		userAgent := c.config.Collector.UserAgents[0]
		req.Header.Set("User-Agent", userAgent)
	}

	// 设置其他常用头部
	req.Header.Set("Accept", "application/json, text/plain, */*")
	req.Header.Set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
	req.Header.Set("Cache-Control", "no-cache")
}

func (c *APICollector) applyFilters(content string, filters []string) bool {
	if len(filters) == 0 {
		return true
	}

	content = strings.ToLower(strings.TrimSpace(content))
	
	// 长度过滤
	if len(content) < 5 || len(content) > 500 {
		return false
	}

	// 应用自定义过滤器
	for _, filter := range filters {
		switch filter {
		case "no_empty":
			if content == "" {
				return false
			}
		case "no_short":
			if len(content) < 10 {
				return false
			}
		case "no_long":
			if len(content) > 200 {
				return false
			}
		case "no_url":
			if strings.Contains(content, "http://") || strings.Contains(content, "https://") {
				return false
			}
		case "no_email":
			if strings.Contains(content, "@") && strings.Contains(content, ".") {
				return false
			}
		}
	}

	return true
}