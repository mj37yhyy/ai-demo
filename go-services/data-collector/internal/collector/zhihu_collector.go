package collector

import (
	"context"
	"fmt"
	"math/rand"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
	"github.com/gocolly/colly/v2"
	"github.com/gocolly/colly/v2/debug"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
	"golang.org/x/time/rate"

	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

// ZhihuCollector 知乎专用爬虫
type ZhihuCollector struct {
	config    *config.Config
	limiter   *rate.Limiter
	userAgent []string
	cookies   map[string]string
	proxies   []string
}

// ZhihuQuestion 知乎问题结构
type ZhihuQuestion struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Detail      string `json:"detail"`
	AnswerCount int    `json:"answer_count"`
	FollowCount int    `json:"follow_count"`
	ViewCount   int    `json:"view_count"`
	CreatedTime int64  `json:"created_time"`
	UpdatedTime int64  `json:"updated_time"`
	Topics      []struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	} `json:"topics"`
}

// ZhihuAnswer 知乎回答结构
type ZhihuAnswer struct {
	ID           string `json:"id"`
	QuestionID   string `json:"question_id"`
	Content      string `json:"content"`
	VoteupCount  int    `json:"voteup_count"`
	CommentCount int    `json:"comment_count"`
	CreatedTime  int64  `json:"created_time"`
	UpdatedTime  int64  `json:"updated_time"`
	Author       struct {
		ID       string `json:"id"`
		Name     string `json:"name"`
		Headline string `json:"headline"`
	} `json:"author"`
}

// NewZhihuCollector 创建知乎爬虫
func NewZhihuCollector(cfg *config.Config) (*ZhihuCollector, error) {
	// 创建速率限制器 - 知乎需要更严格的限制
	limiter := rate.NewLimiter(rate.Limit(5), 1) // 每秒最多5个请求

	userAgents := []string{
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
		"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0",
	}

	return &ZhihuCollector{
		config:    cfg,
		limiter:   limiter,
		userAgent: userAgents,
		cookies:   make(map[string]string),
		proxies:   []string{}, // 可以配置代理列表
	}, nil
}

// Collect 执行知乎数据采集
func (z *ZhihuCollector) Collect(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	logrus.WithField("url", source.Url).Info("Starting Zhihu crawling")

	// 解析采集类型
	collectType := z.getCollectType(source.Parameters)
	
	switch collectType {
	case "questions":
		return z.collectQuestions(ctx, source, config, textChan)
	case "answers":
		return z.collectAnswers(ctx, source, config, textChan)
	case "search":
		return z.collectSearchResults(ctx, source, config, textChan)
	case "topic":
		return z.collectTopicContent(ctx, source, config, textChan)
	default:
		return z.collectGeneral(ctx, source, config, textChan)
	}
}

// collectQuestions 采集知乎问题
func (z *ZhihuCollector) collectQuestions(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	collector := z.createCollector()
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 1000
	}

	// 设置问题页面处理
	collector.OnHTML(".QuestionHeader-title", func(e *colly.HTMLElement) {
		if collected >= maxCount {
			return
		}

		title := strings.TrimSpace(e.Text)
		if title == "" {
			return
		}

		// 获取问题详情
		detail := ""
		e.DOM.Parents().Find(".QuestionRichText").Each(func(i int, s *goquery.Selection) {
			detail = strings.TrimSpace(s.Text())
		})

		// 创建原始文本
		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   fmt.Sprintf("问题: %s\n详情: %s", title, detail),
			Source:    "zhihu:question",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":         e.Request.URL.String(),
				"title":       title,
				"detail":      detail,
				"type":        "question",
				"platform":    "zhihu",
			},
		}

		select {
		case textChan <- rawText:
			collected++
			logrus.WithFields(logrus.Fields{
				"collected": collected,
				"title":     title,
			}).Debug("Collected Zhihu question")
		case <-ctx.Done():
			return
		}
	})

	// 设置答案处理
	collector.OnHTML(".RichContent-inner", func(e *colly.HTMLElement) {
		if collected >= maxCount {
			return
		}

		content := strings.TrimSpace(e.Text)
		if len(content) < 50 { // 过滤太短的内容
			return
		}

		// 获取作者信息
		author := ""
		e.DOM.Parents().Find(".AuthorInfo-name").Each(func(i int, s *goquery.Selection) {
			author = strings.TrimSpace(s.Text())
		})

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   content,
			Source:    "zhihu:answer",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":      e.Request.URL.String(),
				"author":   author,
				"type":     "answer",
				"platform": "zhihu",
			},
		}

		select {
		case textChan <- rawText:
			collected++
			logrus.WithFields(logrus.Fields{
				"collected": collected,
				"author":    author,
				"length":    len(content),
			}).Debug("Collected Zhihu answer")
		case <-ctx.Done():
			return
		}
	})

	// 处理分页
	collector.OnHTML(".Pagination-next", func(e *colly.HTMLElement) {
		if collected < maxCount {
			nextURL := e.Attr("href")
			if nextURL != "" {
				// 添加延迟避免被封
				time.Sleep(time.Duration(rand.Intn(3)+2) * time.Second)
				e.Request.Visit(nextURL)
			}
		}
	})

	return z.startCrawling(ctx, collector, source.Url)
}

// collectAnswers 采集知乎回答
func (z *ZhihuCollector) collectAnswers(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	collector := z.createCollector()
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 1000
	}

	// 处理回答内容
	collector.OnHTML(".RichContent-inner", func(e *colly.HTMLElement) {
		if collected >= maxCount {
			return
		}

		content := z.cleanContent(e.Text)
		if len(content) < 100 { // 过滤太短的回答
			return
		}

		// 获取点赞数
		voteCount := 0
		e.DOM.Parents().Find(".VoteButton--up .Button-label").Each(func(i int, s *goquery.Selection) {
			if count, err := strconv.Atoi(strings.TrimSpace(s.Text())); err == nil {
				voteCount = count
			}
		})

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   content,
			Source:    "zhihu:answer",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":        e.Request.URL.String(),
				"vote_count": strconv.Itoa(voteCount),
				"type":       "answer",
				"platform":   "zhihu",
			},
		}

		select {
		case textChan <- rawText:
			collected++
			logrus.WithFields(logrus.Fields{
				"collected":  collected,
				"vote_count": voteCount,
				"length":     len(content),
			}).Debug("Collected Zhihu answer")
		case <-ctx.Done():
			return
		}
	})

	return z.startCrawling(ctx, collector, source.Url)
}

// collectSearchResults 采集搜索结果
func (z *ZhihuCollector) collectSearchResults(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	keyword := z.getSearchKeyword(source.Parameters)
	if keyword == "" {
		return fmt.Errorf("search keyword is required")
	}

	// 构建搜索URL
	searchURL := fmt.Sprintf("https://www.zhihu.com/search?type=content&q=%s", url.QueryEscape(keyword))
	
	collector := z.createCollector()
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 500
	}

	// 处理搜索结果
	collector.OnHTML(".SearchResult-Card", func(e *colly.HTMLElement) {
		if collected >= maxCount {
			return
		}

		title := strings.TrimSpace(e.ChildText(".SearchResult-title"))
		content := strings.TrimSpace(e.ChildText(".SearchResult-excerpt"))
		
		if title == "" && content == "" {
			return
		}

		fullContent := fmt.Sprintf("%s\n%s", title, content)
		
		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   z.cleanContent(fullContent),
			Source:    "zhihu:search",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":      e.Request.URL.String(),
				"keyword":  keyword,
				"title":    title,
				"type":     "search_result",
				"platform": "zhihu",
			},
		}

		select {
		case textChan <- rawText:
			collected++
			logrus.WithFields(logrus.Fields{
				"collected": collected,
				"keyword":   keyword,
				"title":     title,
			}).Debug("Collected Zhihu search result")
		case <-ctx.Done():
			return
		}
	})

	return z.startCrawling(ctx, collector, searchURL)
}

// collectTopicContent 采集话题内容
func (z *ZhihuCollector) collectTopicContent(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	collector := z.createCollector()
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 1000
	}

	// 处理话题下的问题和回答
	collector.OnHTML(".ContentItem", func(e *colly.HTMLElement) {
		if collected >= maxCount {
			return
		}

		title := strings.TrimSpace(e.ChildText(".ContentItem-title"))
		content := strings.TrimSpace(e.ChildText(".RichContent-inner"))
		
		if content == "" {
			return
		}

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   z.cleanContent(content),
			Source:    "zhihu:topic",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":      e.Request.URL.String(),
				"title":    title,
				"type":     "topic_content",
				"platform": "zhihu",
			},
		}

		select {
		case textChan <- rawText:
			collected++
			logrus.WithFields(logrus.Fields{
				"collected": collected,
				"title":     title,
			}).Debug("Collected Zhihu topic content")
		case <-ctx.Done():
			return
		}
	})

	return z.startCrawling(ctx, collector, source.Url)
}

// collectGeneral 通用采集方法
func (z *ZhihuCollector) collectGeneral(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	collector := z.createCollector()
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 500
	}

	// 通用内容选择器
	selectors := []string{
		".RichContent-inner",
		".QuestionHeader-title",
		".SearchResult-excerpt",
		".ContentItem-title",
	}

	for _, selector := range selectors {
		collector.OnHTML(selector, func(e *colly.HTMLElement) {
			if collected >= maxCount {
				return
			}

			content := z.cleanContent(e.Text)
			if len(content) < 20 {
				return
			}

			rawText := &pb.RawText{
				Id:        uuid.New().String(),
				Content:   content,
				Source:    "zhihu:general",
				Timestamp: time.Now().UnixMilli(),
				Metadata: map[string]string{
					"url":      e.Request.URL.String(),
					"selector": selector,
					"type":     "general",
					"platform": "zhihu",
				},
			}

			select {
			case textChan <- rawText:
				collected++
				logrus.WithFields(logrus.Fields{
					"collected": collected,
					"selector":  selector,
				}).Debug("Collected Zhihu general content")
			case <-ctx.Done():
				return
			}
		})
	}

	return z.startCrawling(ctx, collector, source.Url)
}

// createCollector 创建配置好的爬虫实例
func (z *ZhihuCollector) createCollector() *colly.Collector {
	c := colly.NewCollector(
		colly.Debugger(&debug.LogDebugger{}),
		colly.UserAgent(z.getRandomUserAgent()),
	)

	// 设置限制
	c.Limit(&colly.LimitRule{
		DomainGlob:  "*zhihu.com*",
		Parallelism: 2, // 知乎限制并发数
		Delay:       3 * time.Second, // 增加延迟
	})

	// 设置请求回调 - 反爬虫处理
	c.OnRequest(func(r *colly.Request) {
		// 速率限制
		z.limiter.Wait(context.Background())

		// 设置随机User-Agent
		r.Headers.Set("User-Agent", z.getRandomUserAgent())
		
		// 设置必要的头部信息
		r.Headers.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
		r.Headers.Set("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
		r.Headers.Set("Accept-Encoding", "gzip, deflate, br")
		r.Headers.Set("Connection", "keep-alive")
		r.Headers.Set("Upgrade-Insecure-Requests", "1")
		r.Headers.Set("Sec-Fetch-Dest", "document")
		r.Headers.Set("Sec-Fetch-Mode", "navigate")
		r.Headers.Set("Sec-Fetch-Site", "none")
		r.Headers.Set("Cache-Control", "max-age=0")

		// 设置Referer
		if r.URL.Host == "www.zhihu.com" {
			r.Headers.Set("Referer", "https://www.zhihu.com/")
		}

		// 添加cookies
		for name, value := range z.cookies {
			r.Headers.Set("Cookie", fmt.Sprintf("%s=%s", name, value))
		}

		logrus.WithField("url", r.URL.String()).Debug("Visiting Zhihu URL")
	})

	// 响应处理
	c.OnResponse(func(r *colly.Response) {
		logrus.WithFields(logrus.Fields{
			"url":    r.Request.URL.String(),
			"status": r.StatusCode,
			"size":   len(r.Body),
		}).Debug("Received Zhihu response")

		// 检查是否被反爬虫拦截
		if r.StatusCode == 429 || r.StatusCode == 403 {
			logrus.Warn("Rate limited or blocked by Zhihu, increasing delay")
			time.Sleep(10 * time.Second)
		}
	})

	// 错误处理
	c.OnError(func(r *colly.Response, err error) {
		logrus.WithFields(logrus.Fields{
			"url":   r.Request.URL.String(),
			"error": err.Error(),
			"status": r.StatusCode,
		}).Error("Zhihu crawling error")

		// 如果是429错误，增加延迟
		if r.StatusCode == 429 {
			time.Sleep(30 * time.Second)
		}
	})

	return c
}

// startCrawling 开始爬取
func (z *ZhihuCollector) startCrawling(ctx context.Context, collector *colly.Collector, startURL string) error {
	// 在goroutine中执行爬取，以便可以被context取消
	errChan := make(chan error, 1)
	
	go func() {
		defer close(errChan)
		if err := collector.Visit(startURL); err != nil {
			errChan <- fmt.Errorf("failed to start crawling: %w", err)
			return
		}
		collector.Wait()
		errChan <- nil
	}()

	// 等待完成或取消
	select {
	case err := <-errChan:
		return err
	case <-ctx.Done():
		return ctx.Err()
	}
}

// 辅助方法
func (z *ZhihuCollector) getRandomUserAgent() string {
	return z.userAgent[rand.Intn(len(z.userAgent))]
}

func (z *ZhihuCollector) getCollectType(params map[string]string) string {
	if params == nil {
		return "general"
	}
	if collectType, ok := params["type"]; ok {
		return collectType
	}
	return "general"
}

func (z *ZhihuCollector) getSearchKeyword(params map[string]string) string {
	if params == nil {
		return ""
	}
	if keyword, ok := params["keyword"]; ok {
		return keyword
	}
	if q, ok := params["q"]; ok {
		return q
	}
	return ""
}

func (z *ZhihuCollector) cleanContent(content string) string {
	// 清理HTML标签
	re := regexp.MustCompile(`<[^>]*>`)
	content = re.ReplaceAllString(content, "")
	
	// 清理多余的空白字符
	content = regexp.MustCompile(`\s+`).ReplaceAllString(content, " ")
	
	// 去除首尾空白
	content = strings.TrimSpace(content)
	
	return content
}

// SetCookies 设置登录cookies
func (z *ZhihuCollector) SetCookies(cookies map[string]string) {
	z.cookies = cookies
}

// SetProxies 设置代理列表
func (z *ZhihuCollector) SetProxies(proxies []string) {
	z.proxies = proxies
}