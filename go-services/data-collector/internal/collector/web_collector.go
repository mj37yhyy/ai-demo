package collector

import (
	"context"
	"fmt"
	"math/rand"
	"strings"
	"time"

	"github.com/gocolly/colly/v2"
	"github.com/gocolly/colly/v2/debug"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

type WebCollector struct {
	config *config.Config
}

func NewWebCollector(cfg *config.Config) (*WebCollector, error) {
	return &WebCollector{
		config: cfg,
	}, nil
}

func (c *WebCollector) Collect(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	logrus.WithField("url", source.Url).Info("Starting web crawling")

	// 创建 Colly 收集器
	collector := colly.NewCollector(
		colly.Debugger(&debug.LogDebugger{}),
		colly.UserAgent(c.getRandomUserAgent()),
	)

	// 设置限制
	collector.Limit(&colly.LimitRule{
		DomainGlob:  "*",
		Parallelism: int(config.ConcurrentLimit),
		Delay:       time.Second / time.Duration(config.RateLimit),
	})

	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 100 // 默认最大采集数量
	}

	// 设置请求回调
	collector.OnRequest(func(r *colly.Request) {
		logrus.WithField("url", r.URL.String()).Debug("Visiting URL")
		
		// 随机设置User-Agent
		r.Headers.Set("User-Agent", c.getRandomUserAgent())
		
		// 设置其他头部
		r.Headers.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
		r.Headers.Set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
		r.Headers.Set("Accept-Encoding", "gzip, deflate")
		r.Headers.Set("Connection", "keep-alive")
	})

	// 设置响应回调
	collector.OnResponse(func(r *colly.Response) {
		logrus.WithFields(logrus.Fields{
			"url":    r.Request.URL.String(),
			"status": r.StatusCode,
			"size":   len(r.Body),
		}).Debug("Received response")
	})

	// 设置HTML回调 - 根据参数配置选择器
	selectors := c.getSelectors(source.Parameters)
	for _, selector := range selectors {
		collector.OnHTML(selector, func(e *colly.HTMLElement) {
			if collected >= maxCount {
				return
			}

			text := strings.TrimSpace(e.Text)
			if !c.applyFilters(text, config.Filters) {
				return
			}

			rawText := &pb.RawText{
				Id:        uuid.New().String(),
				Content:   text,
				Source:    fmt.Sprintf("web:%s", e.Request.URL.Host),
				Timestamp: time.Now().UnixMilli(),
				Metadata: map[string]string{
					"url":      e.Request.URL.String(),
					"selector": selector,
					"tag":      e.Name,
				},
			}

			select {
			case textChan <- rawText:
				collected++
				logrus.WithFields(logrus.Fields{
					"collected": collected,
					"text_id":   rawText.Id,
					"url":       e.Request.URL.String(),
				}).Debug("Collected text from web")
			case <-ctx.Done():
				return
			}
		})
	}

	// 设置链接回调 - 自动发现新链接
	if c.shouldFollowLinks(source.Parameters) {
		collector.OnHTML("a[href]", func(e *colly.HTMLElement) {
			if collected >= maxCount {
				return
			}

			link := e.Attr("href")
			if c.isValidLink(link, source.Url) {
				e.Request.Visit(link)
			}
		})
	}

	// 错误处理
	collector.OnError(func(r *colly.Response, err error) {
		logrus.WithFields(logrus.Fields{
			"url":   r.Request.URL.String(),
			"error": err.Error(),
		}).Error("Crawling error")
	})

	// 完成回调
	collector.OnScraped(func(r *colly.Response) {
		logrus.WithField("url", r.Request.URL.String()).Debug("Finished scraping")
	})

	// 开始爬取
	if err := collector.Visit(source.Url); err != nil {
		return fmt.Errorf("failed to start crawling: %w", err)
	}

	// 等待完成
	collector.Wait()

	logrus.WithField("total_collected", collected).Info("Web crawling completed")
	return nil
}

func (c *WebCollector) getSelectors(params map[string]string) []string {
	// 从参数中获取选择器，如果没有则使用默认选择器
	if selectors, exists := params["selectors"]; exists {
		return strings.Split(selectors, ",")
	}

	// 默认选择器 - 常见的文本内容选择器
	return []string{
		"p",                    // 段落
		".comment",             // 评论
		".content",             // 内容
		".text",                // 文本
		".description",         // 描述
		".review",              // 评论/评价
		"[class*='comment']",   // 包含comment的class
		"[class*='content']",   // 包含content的class
		"[class*='text']",      // 包含text的class
		"article",              // 文章
		".post",                // 帖子
		".message",             // 消息
		".reply",               // 回复
	}
}

func (c *WebCollector) shouldFollowLinks(params map[string]string) bool {
	if follow, exists := params["follow_links"]; exists {
		return follow == "true" || follow == "1"
	}
	return false // 默认不跟随链接
}

func (c *WebCollector) isValidLink(link, baseURL string) bool {
	// 过滤无效链接
	if link == "" || link == "#" {
		return false
	}

	// 过滤外部链接（可选）
	if strings.HasPrefix(link, "http") && !strings.Contains(link, extractDomain(baseURL)) {
		return false
	}

	// 过滤特定类型的链接
	excludePatterns := []string{
		".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".zip",
		"javascript:", "mailto:", "tel:",
	}

	linkLower := strings.ToLower(link)
	for _, pattern := range excludePatterns {
		if strings.Contains(linkLower, pattern) {
			return false
		}
	}

	return true
}

func (c *WebCollector) getRandomUserAgent() string {
	if len(c.config.Collector.UserAgents) == 0 {
		return "Mozilla/5.0 (compatible; TextAuditBot/1.0)"
	}

	index := rand.Intn(len(c.config.Collector.UserAgents))
	return c.config.Collector.UserAgents[index]
}

func (c *WebCollector) applyFilters(content string, filters []string) bool {
	if len(filters) == 0 {
		return true
	}

	content = strings.TrimSpace(content)
	
	// 基本长度过滤
	if len(content) < 5 || len(content) > 1000 {
		return false
	}

	// 过滤纯数字或特殊字符
	if isOnlyNumbersOrSymbols(content) {
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
			if len(content) < 20 {
				return false
			}
		case "no_long":
			if len(content) > 500 {
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
		case "chinese_only":
			if !containsChinese(content) {
				return false
			}
		}
	}

	return true
}

func extractDomain(url string) string {
	if strings.HasPrefix(url, "http://") {
		url = url[7:]
	} else if strings.HasPrefix(url, "https://") {
		url = url[8:]
	}
	
	if idx := strings.Index(url, "/"); idx != -1 {
		url = url[:idx]
	}
	
	return url
}

func isOnlyNumbersOrSymbols(text string) bool {
	for _, r := range text {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= 0x4e00 && r <= 0x9fff) {
			return false
		}
	}
	return true
}