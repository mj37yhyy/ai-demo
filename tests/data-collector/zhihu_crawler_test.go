package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/gocolly/colly/v2"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	
	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/collector"
	"github.com/mj37yhyy/ai-demo/go-services/data-collector/internal/config"
)

// ZhihuTestConfig 知乎测试配置
type ZhihuTestConfig struct {
	BaseURL     string   `json:"base_url"`
	TopicURLs   []string `json:"topic_urls"`
	UserAgent   string   `json:"user_agent"`
	RateLimit   int      `json:"rate_limit"`
	MaxTexts    int      `json:"max_texts"`
	TestTimeout int      `json:"test_timeout"`
}

// ZhihuComment 知乎评论结构
type ZhihuComment struct {
	ID        string    `json:"id"`
	Content   string    `json:"content"`
	Author    string    `json:"author"`
	CreatedAt time.Time `json:"created_at"`
	LikeCount int       `json:"like_count"`
	URL       string    `json:"url"`
}

// TestResult 测试结果
type TestResult struct {
	TestName      string        `json:"test_name"`
	Success       bool          `json:"success"`
	CollectedCount int          `json:"collected_count"`
	Duration      time.Duration `json:"duration"`
	ErrorMessage  string        `json:"error_message,omitempty"`
	SampleTexts   []string      `json:"sample_texts"`
}

// loadTestConfig 加载测试配置
func loadTestConfig() (*ZhihuTestConfig, error) {
	// 默认配置
	defaultConfig := &ZhihuTestConfig{
		BaseURL: "https://www.zhihu.com",
		TopicURLs: []string{
			"https://www.zhihu.com/topic/19551137/hot",  // 互联网话题
			"https://www.zhihu.com/topic/19550517/hot",  // 科技话题
			"https://www.zhihu.com/topic/19778317/hot",  // AI话题
		},
		UserAgent:   "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
		RateLimit:   2, // 每秒2次请求
		MaxTexts:    50,
		TestTimeout: 300, // 5分钟超时
	}

	// 尝试从文件加载配置
	configFile := "zhihu_test_config.json"
	if data, err := os.ReadFile(configFile); err == nil {
		var fileConfig ZhihuTestConfig
		if err := json.Unmarshal(data, &fileConfig); err == nil {
			return &fileConfig, nil
		}
	}

	// 保存默认配置到文件
	if data, err := json.MarshalIndent(defaultConfig, "", "  "); err == nil {
		os.WriteFile(configFile, data, 0644)
	}

	return defaultConfig, nil
}

// TestZhihuWebCrawler 测试知乎网页爬虫
func TestZhihuWebCrawler(t *testing.T) {
	testConfig, err := loadTestConfig()
	require.NoError(t, err)

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(testConfig.TestTimeout)*time.Second)
	defer cancel()

	results := make([]*TestResult, 0)

	for i, topicURL := range testConfig.TopicURLs {
		testName := fmt.Sprintf("ZhihuWebCrawler_Topic_%d", i+1)
		result := &TestResult{
			TestName:    testName,
			SampleTexts: make([]string, 0),
		}

		startTime := time.Now()

		// 创建采集器配置
		cfg := &config.Config{
			Server: config.ServerConfig{
				Port: 8080,
			},
		}

		webCollector, err := collector.NewWebCollector(cfg)
		if err != nil {
			result.Success = false
			result.ErrorMessage = fmt.Sprintf("Failed to create web collector: %v", err)
			results = append(results, result)
			continue
		}

		// 配置采集源
		source := &pb.CollectionSource{
			Type: pb.SourceType_WEB_CRAWLER,
			Url:  topicURL,
			Parameters: map[string]string{
				"selectors":    ".ContentItem-title,.RichContent-inner,.CommentContent",
				"follow_links": "true",
				"max_depth":    "2",
			},
		}

		// 配置采集参数
		collectionConfig := &pb.CollectionConfig{
			MaxCount:        int32(testConfig.MaxTexts),
			ConcurrentLimit: 3,
			RateLimit:       int32(testConfig.RateLimit),
			Filters: []*pb.TextFilter{
				{
					Type:      pb.FilterType_MIN_LENGTH,
					Parameter: "10", // 最小长度10字符
				},
				{
					Type:      pb.FilterType_MAX_LENGTH,
					Parameter: "1000", // 最大长度1000字符
				},
			},
		}

		// 创建文本通道
		textChan := make(chan *pb.RawText, 100)
		
		// 启动采集
		go func() {
			defer close(textChan)
			if err := webCollector.Collect(ctx, source, collectionConfig, textChan); err != nil {
				log.Printf("Collection error: %v", err)
			}
		}()

		// 收集结果
		collectedTexts := make([]*pb.RawText, 0)
		for text := range textChan {
			collectedTexts = append(collectedTexts, text)
			
			// 保存前5个样本
			if len(result.SampleTexts) < 5 {
				result.SampleTexts = append(result.SampleTexts, text.Content[:min(100, len(text.Content))])
			}
		}

		result.Duration = time.Since(startTime)
		result.CollectedCount = len(collectedTexts)
		result.Success = len(collectedTexts) > 0

		if !result.Success {
			result.ErrorMessage = "No texts collected"
		}

		results = append(results, result)

		// 验证采集的文本质量
		if result.Success {
			validateTextQuality(t, collectedTexts, testName)
		}

		// 避免过于频繁的请求
		time.Sleep(5 * time.Second)
	}

	// 生成测试报告
	generateTestReport(t, "zhihu_web_crawler", results)
}

// TestZhihuAPICollector 测试知乎API采集（模拟）
func TestZhihuAPICollector(t *testing.T) {
	testConfig, err := loadTestConfig()
	require.NoError(t, err)

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(testConfig.TestTimeout)*time.Second)
	defer cancel()

	// 创建模拟的知乎API服务器
	mockServer := createMockZhihuAPIServer()
	defer mockServer.Close()

	cfg := &config.Config{
		Server: config.ServerConfig{
			Port: 8080,
		},
	}

	apiCollector, err := collector.NewAPICollector(cfg)
	require.NoError(t, err)

	// 配置API采集源
	source := &pb.CollectionSource{
		Type: pb.SourceType_API,
		Url:  mockServer.URL + "/api/v1/topics/comments",
		Parameters: map[string]string{
			"topic_id": "19551137",
			"limit":    "20",
			"offset":   "0",
		},
	}

	collectionConfig := &pb.CollectionConfig{
		MaxCount:  int32(testConfig.MaxTexts),
		RateLimit: int32(testConfig.RateLimit),
		Filters: []*pb.TextFilter{
			{
				Type:      pb.FilterType_MIN_LENGTH,
				Parameter: "5",
			},
		},
	}

	textChan := make(chan *pb.RawText, 100)
	
	startTime := time.Now()
	
	go func() {
		defer close(textChan)
		if err := apiCollector.Collect(ctx, source, collectionConfig, textChan); err != nil {
			log.Printf("API collection error: %v", err)
		}
	}()

	// 收集结果
	collectedTexts := make([]*pb.RawText, 0)
	for text := range textChan {
		collectedTexts = append(collectedTexts, text)
	}

	duration := time.Since(startTime)

	// 验证结果
	assert.Greater(t, len(collectedTexts), 0, "Should collect at least one text")
	
	result := &TestResult{
		TestName:       "ZhihuAPICollector",
		Success:        len(collectedTexts) > 0,
		CollectedCount: len(collectedTexts),
		Duration:       duration,
		SampleTexts:    make([]string, 0),
	}

	for i, text := range collectedTexts {
		if i < 5 {
			result.SampleTexts = append(result.SampleTexts, text.Content[:min(100, len(text.Content))])
		}
	}

	generateTestReport(t, "zhihu_api_collector", []*TestResult{result})
}

// TestZhihuDataQuality 测试知乎数据质量
func TestZhihuDataQuality(t *testing.T) {
	// 模拟一些采集到的知乎数据
	sampleTexts := []*pb.RawText{
		{
			Id:        uuid.New().String(),
			Content:   "这是一个关于人工智能发展的深度讨论，涉及到机器学习、深度学习等多个领域。",
			Source:    "web:zhihu.com",
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"url":    "https://www.zhihu.com/question/123456",
				"author": "test_user",
			},
		},
		{
			Id:        uuid.New().String(),
			Content:   "简短回复",
			Source:    "web:zhihu.com",
			Timestamp: time.Now().UnixMilli(),
		},
		{
			Id:        uuid.New().String(),
			Content:   strings.Repeat("这是一个非常长的文本内容，用于测试长度过滤功能。", 50),
			Source:    "web:zhihu.com",
			Timestamp: time.Now().UnixMilli(),
		},
	}

	validateTextQuality(t, sampleTexts, "ZhihuDataQuality")
}

// validateTextQuality 验证文本质量
func validateTextQuality(t *testing.T, texts []*pb.RawText, testName string) {
	qualityMetrics := struct {
		TotalTexts      int
		ValidTexts      int
		AverageLength   float64
		UniqueTexts     int
		ChineseTexts    int
		QualityScore    float64
	}{}

	qualityMetrics.TotalTexts = len(texts)
	textSet := make(map[string]bool)
	totalLength := 0

	for _, text := range texts {
		// 检查文本长度
		if len(text.Content) >= 10 && len(text.Content) <= 1000 {
			qualityMetrics.ValidTexts++
		}

		// 检查唯一性
		if !textSet[text.Content] {
			textSet[text.Content] = true
			qualityMetrics.UniqueTexts++
		}

		// 检查中文内容
		if containsChinese(text.Content) {
			qualityMetrics.ChineseTexts++
		}

		totalLength += len(text.Content)
	}

	if qualityMetrics.TotalTexts > 0 {
		qualityMetrics.AverageLength = float64(totalLength) / float64(qualityMetrics.TotalTexts)
		qualityMetrics.QualityScore = float64(qualityMetrics.ValidTexts) / float64(qualityMetrics.TotalTexts) * 100
	}

	// 断言质量指标
	assert.Greater(t, qualityMetrics.QualityScore, 70.0, "Quality score should be above 70%")
	assert.Greater(t, qualityMetrics.UniqueTexts, qualityMetrics.TotalTexts/2, "Should have reasonable text diversity")
	assert.Greater(t, qualityMetrics.ChineseTexts, 0, "Should contain Chinese text")

	log.Printf("%s Quality Metrics: Total=%d, Valid=%d, Unique=%d, Chinese=%d, AvgLen=%.1f, Score=%.1f%%",
		testName,
		qualityMetrics.TotalTexts,
		qualityMetrics.ValidTexts,
		qualityMetrics.UniqueTexts,
		qualityMetrics.ChineseTexts,
		qualityMetrics.AverageLength,
		qualityMetrics.QualityScore,
	)
}

// containsChinese 检查文本是否包含中文
func containsChinese(text string) bool {
	for _, r := range text {
		if r >= 0x4e00 && r <= 0x9fff {
			return true
		}
	}
	return false
}

// createMockZhihuAPIServer 创建模拟知乎API服务器
func createMockZhihuAPIServer() *http.Server {
	mux := http.NewServeMux()
	
	mux.HandleFunc("/api/v1/topics/comments", func(w http.ResponseWriter, r *http.Request) {
		// 模拟知乎API响应
		mockResponse := map[string]interface{}{
			"data": []map[string]interface{}{
				{
					"id":      "comment_1",
					"content": "人工智能的发展确实令人瞩目，特别是在自然语言处理领域的突破。",
					"author":  "AI爱好者",
					"created_at": time.Now().Format(time.RFC3339),
				},
				{
					"id":      "comment_2", 
					"content": "ChatGPT的出现改变了我们对AI的认知，但我们也需要关注其潜在风险。",
					"author":  "技术观察者",
					"created_at": time.Now().Format(time.RFC3339),
				},
				{
					"id":      "comment_3",
					"content": "机器学习算法的优化是一个持续的过程，需要大量的数据和计算资源。",
					"author":  "算法工程师",
					"created_at": time.Now().Format(time.RFC3339),
				},
			},
			"has_more": false,
			"next_url": "",
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(mockResponse)
	})

	server := &http.Server{
		Addr:    ":0", // 随机端口
		Handler: mux,
	}

	go server.ListenAndServe()
	return server
}

// generateTestReport 生成测试报告
func generateTestReport(t *testing.T, reportName string, results []*TestResult) {
	report := struct {
		ReportName    string        `json:"report_name"`
		GeneratedAt   time.Time     `json:"generated_at"`
		TotalTests    int           `json:"total_tests"`
		PassedTests   int           `json:"passed_tests"`
		FailedTests   int           `json:"failed_tests"`
		TotalTexts    int           `json:"total_texts"`
		AverageDuration time.Duration `json:"average_duration"`
		Results       []*TestResult `json:"results"`
	}{
		ReportName:  reportName,
		GeneratedAt: time.Now(),
		TotalTests:  len(results),
		Results:     results,
	}

	var totalDuration time.Duration
	for _, result := range results {
		if result.Success {
			report.PassedTests++
		} else {
			report.FailedTests++
		}
		report.TotalTexts += result.CollectedCount
		totalDuration += result.Duration
	}

	if len(results) > 0 {
		report.AverageDuration = totalDuration / time.Duration(len(results))
	}

	// 保存报告到文件
	reportFile := fmt.Sprintf("test_reports/%s_report_%s.json", reportName, time.Now().Format("20060102_150405"))
	os.MkdirAll("test_reports", 0755)
	
	if data, err := json.MarshalIndent(report, "", "  "); err == nil {
		os.WriteFile(reportFile, data, 0644)
		log.Printf("Test report saved to: %s", reportFile)
	}

	// 输出摘要
	log.Printf("=== %s Test Summary ===", reportName)
	log.Printf("Total Tests: %d", report.TotalTests)
	log.Printf("Passed: %d", report.PassedTests)
	log.Printf("Failed: %d", report.FailedTests)
	log.Printf("Total Texts Collected: %d", report.TotalTexts)
	log.Printf("Average Duration: %v", report.AverageDuration)
}

// min 辅助函数
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// TestMain 测试主函数
func TestMain(m *testing.M) {
	log.Println("Starting Zhihu Data Collection Tests...")
	
	// 创建必要的目录
	os.MkdirAll("test_reports", 0755)
	os.MkdirAll("test_data", 0755)
	
	// 运行测试
	code := m.Run()
	
	log.Println("Zhihu Data Collection Tests completed.")
	os.Exit(code)
}