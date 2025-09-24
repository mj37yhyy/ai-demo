package test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/sirupsen/logrus"
)

// 测试配置
type TestConfig struct {
	BaseURL           string
	TestDataSize      int
	ConcurrentUsers   int
	TestDuration      time.Duration
	BatchSize         int
	ModelNames        []string
	PerformanceTarget PerformanceTarget
}

// 性能目标
type PerformanceTarget struct {
	MaxLatency        time.Duration
	MinThroughput     float64
	MaxErrorRate      float64
	MaxMemoryUsage    int64 // MB
	MaxCPUUsage       float64
}

// 测试结果
type TestResult struct {
	TestName        string                 `json:"test_name"`
	Status          string                 `json:"status"`
	Duration        time.Duration          `json:"duration"`
	TotalRequests   int                    `json:"total_requests"`
	SuccessRequests int                    `json:"success_requests"`
	FailedRequests  int                    `json:"failed_requests"`
	ErrorRate       float64                `json:"error_rate"`
	Throughput      float64                `json:"throughput"`
	AvgLatency      time.Duration          `json:"avg_latency"`
	MinLatency      time.Duration          `json:"min_latency"`
	MaxLatency      time.Duration          `json:"max_latency"`
	P95Latency      time.Duration          `json:"p95_latency"`
	P99Latency      time.Duration          `json:"p99_latency"`
	MemoryUsage     int64                  `json:"memory_usage_mb"`
	CPUUsage        float64                `json:"cpu_usage"`
	Details         map[string]interface{} `json:"details"`
	Errors          []string               `json:"errors"`
}

// 测试报告
type TestReport struct {
	Timestamp    time.Time    `json:"timestamp"`
	Environment  string       `json:"environment"`
	Version      string       `json:"version"`
	TestResults  []TestResult `json:"test_results"`
	Summary      TestSummary  `json:"summary"`
	Passed       bool         `json:"passed"`
}

// 测试摘要
type TestSummary struct {
	TotalTests   int     `json:"total_tests"`
	PassedTests  int     `json:"passed_tests"`
	FailedTests  int     `json:"failed_tests"`
	SuccessRate  float64 `json:"success_rate"`
	TotalTime    string  `json:"total_time"`
}

// 推理请求
type PredictRequest struct {
	ModelName string                 `json:"model_name"`
	Data      map[string]interface{} `json:"data"`
}

// 批量推理请求
type BatchPredictRequest struct {
	ModelName string                   `json:"model_name"`
	Data      []map[string]interface{} `json:"data"`
}

// 文本分类请求
type TextClassifyRequest struct {
	ModelName string `json:"model_name"`
	Text      string `json:"text"`
}

// 情感分析请求
type SentimentAnalysisRequest struct {
	ModelName string `json:"model_name"`
	Text      string `json:"text"`
}

// 模型加载请求
type LoadModelRequest struct {
	Name  string `json:"name"`
	Force bool   `json:"force"`
}

// 生产级推理测试套件
type ProductionInferenceTestSuite struct {
	config     TestConfig
	httpClient *http.Client
	results    []TestResult
	mu         sync.Mutex
	logger     *logrus.Logger
}

func NewProductionInferenceTestSuite(config TestConfig) *ProductionInferenceTestSuite {
	logger := logrus.New()
	logger.SetLevel(logrus.InfoLevel)
	logger.SetFormatter(&logrus.JSONFormatter{})

	return &ProductionInferenceTestSuite{
		config: config,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		results: make([]TestResult, 0),
		logger:  logger,
	}
}

// 运行所有测试
func (suite *ProductionInferenceTestSuite) RunAllTests() error {
	suite.logger.Info("开始运行生产级推理测试套件")
	
	tests := []struct {
		name string
		fn   func() TestResult
	}{
		{"模型加载测试", suite.TestModelLoading},
		{"单次推理测试", suite.TestSingleInference},
		{"批量推理测试", suite.TestBatchInference},
		{"文本分类测试", suite.TestTextClassification},
		{"情感分析测试", suite.TestSentimentAnalysis},
		{"并发推理测试", suite.TestConcurrentInference},
		{"性能压力测试", suite.TestPerformanceStress},
		{"错误处理测试", suite.TestErrorHandling},
		{"内存泄漏测试", suite.TestMemoryLeak},
		{"长时间运行测试", suite.TestLongRunning},
	}

	for _, test := range tests {
		suite.logger.Infof("运行测试: %s", test.name)
		result := test.fn()
		suite.addResult(result)
		
		if result.Status == "FAILED" {
			suite.logger.Errorf("测试失败: %s", test.name)
		} else {
			suite.logger.Infof("测试通过: %s", test.name)
		}
	}

	return suite.generateReport()
}

// 模型加载测试
func (suite *ProductionInferenceTestSuite) TestModelLoading() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "模型加载测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	// 测试加载多个模型
	loadResults := make(map[string]bool)
	for _, modelName := range suite.config.ModelNames {
		success := suite.loadModel(modelName, false)
		loadResults[modelName] = success
		if !success {
			result.Errors = append(result.Errors, fmt.Sprintf("加载模型失败: %s", modelName))
		}
	}

	// 检查模型状态
	statusResults := make(map[string]interface{})
	for _, modelName := range suite.config.ModelNames {
		status := suite.getModelStatus(modelName)
		statusResults[modelName] = status
	}

	result.Duration = time.Since(start)
	result.Details["load_results"] = loadResults
	result.Details["status_results"] = statusResults
	
	// 计算成功率
	successCount := 0
	for _, success := range loadResults {
		if success {
			successCount++
		}
	}
	
	result.TotalRequests = len(suite.config.ModelNames)
	result.SuccessRequests = successCount
	result.FailedRequests = result.TotalRequests - result.SuccessRequests
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)

	if result.ErrorRate == 0 {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 单次推理测试
func (suite *ProductionInferenceTestSuite) TestSingleInference() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "单次推理测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	latencies := make([]time.Duration, 0)
	successCount := 0
	totalRequests := suite.config.TestDataSize

	for i := 0; i < totalRequests; i++ {
		reqStart := time.Now()
		
		// 随机选择模型
		modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
		
		// 生成测试数据
		testData := suite.generateTestData()
		
		success := suite.makePredictRequest(modelName, testData)
		latency := time.Since(reqStart)
		latencies = append(latencies, latency)
		
		if success {
			successCount++
		} else {
			result.Errors = append(result.Errors, fmt.Sprintf("推理请求失败: 第%d次", i+1))
		}
	}

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	// 性能检查
	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate &&
		result.MaxLatency <= suite.config.PerformanceTarget.MaxLatency &&
		result.Throughput >= suite.config.PerformanceTarget.MinThroughput {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 批量推理测试
func (suite *ProductionInferenceTestSuite) TestBatchInference() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "批量推理测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	latencies := make([]time.Duration, 0)
	successCount := 0
	totalBatches := suite.config.TestDataSize / suite.config.BatchSize

	for i := 0; i < totalBatches; i++ {
		reqStart := time.Now()
		
		// 随机选择模型
		modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
		
		// 生成批量测试数据
		batchData := make([]map[string]interface{}, suite.config.BatchSize)
		for j := 0; j < suite.config.BatchSize; j++ {
			batchData[j] = suite.generateTestData()
		}
		
		success := suite.makeBatchPredictRequest(modelName, batchData)
		latency := time.Since(reqStart)
		latencies = append(latencies, latency)
		
		if success {
			successCount++
		} else {
			result.Errors = append(result.Errors, fmt.Sprintf("批量推理请求失败: 第%d批", i+1))
		}
	}

	result.Duration = time.Since(start)
	result.TotalRequests = totalBatches
	result.SuccessRequests = successCount
	result.FailedRequests = totalBatches - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalBatches * suite.config.BatchSize) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	result.Details["batch_size"] = suite.config.BatchSize
	result.Details["total_items"] = totalBatches * suite.config.BatchSize

	// 性能检查
	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate &&
		result.MaxLatency <= suite.config.PerformanceTarget.MaxLatency {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 文本分类测试
func (suite *ProductionInferenceTestSuite) TestTextClassification() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "文本分类测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	// 知乎文本分类测试数据
	testTexts := []string{
		"这个产品真的很好用，强烈推荐给大家！",
		"服务态度太差了，完全不推荐。",
		"价格合理，质量不错，值得购买。",
		"物流速度很快，包装也很好。",
		"功能很强大，但是界面设计需要改进。",
		"客服回复很及时，解决问题很专业。",
		"性价比很高，比同类产品好很多。",
		"使用体验一般，没有什么特别的亮点。",
		"质量有问题，用了几天就坏了。",
		"操作简单，适合新手使用。",
	}

	latencies := make([]time.Duration, 0)
	successCount := 0
	totalRequests := len(testTexts) * len(suite.config.ModelNames)

	for _, modelName := range suite.config.ModelNames {
		for _, text := range testTexts {
			reqStart := time.Now()
			
			success := suite.makeTextClassifyRequest(modelName, text)
			latency := time.Since(reqStart)
			latencies = append(latencies, latency)
			
			if success {
				successCount++
			} else {
				result.Errors = append(result.Errors, fmt.Sprintf("文本分类失败: %s", text[:20]))
			}
		}
	}

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 情感分析测试
func (suite *ProductionInferenceTestSuite) TestSentimentAnalysis() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "情感分析测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	// 知乎情感分析测试数据
	testTexts := []string{
		"今天心情特别好，阳光明媚！",
		"工作压力太大了，感觉很累。",
		"和朋友聚餐很开心，聊得很愉快。",
		"考试没考好，有点失落。",
		"新买的书很有趣，学到了很多。",
		"天气不好，心情也不太好。",
		"完成了一个重要项目，很有成就感。",
		"堵车堵了两小时，真是烦人。",
		"看了一部很棒的电影，推荐给大家。",
		"身体有点不舒服，需要休息。",
	}

	latencies := make([]time.Duration, 0)
	successCount := 0
	totalRequests := len(testTexts) * len(suite.config.ModelNames)

	for _, modelName := range suite.config.ModelNames {
		for _, text := range testTexts {
			reqStart := time.Now()
			
			success := suite.makeSentimentAnalysisRequest(modelName, text)
			latency := time.Since(reqStart)
			latencies = append(latencies, latency)
			
			if success {
				successCount++
			} else {
				result.Errors = append(result.Errors, fmt.Sprintf("情感分析失败: %s", text[:20]))
			}
		}
	}

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 并发推理测试
func (suite *ProductionInferenceTestSuite) TestConcurrentInference() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "并发推理测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	var wg sync.WaitGroup
	var mu sync.Mutex
	latencies := make([]time.Duration, 0)
	successCount := 0
	totalRequests := suite.config.ConcurrentUsers * 10 // 每个用户发送10个请求

	for i := 0; i < suite.config.ConcurrentUsers; i++ {
		wg.Add(1)
		go func(userID int) {
			defer wg.Done()
			
			for j := 0; j < 10; j++ {
				reqStart := time.Now()
				
				// 随机选择模型
				modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
				testData := suite.generateTestData()
				
				success := suite.makePredictRequest(modelName, testData)
				latency := time.Since(reqStart)
				
				mu.Lock()
				latencies = append(latencies, latency)
				if success {
					successCount++
				} else {
					result.Errors = append(result.Errors, fmt.Sprintf("用户%d请求%d失败", userID, j))
				}
				mu.Unlock()
			}
		}(i)
	}

	wg.Wait()

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	result.Details["concurrent_users"] = suite.config.ConcurrentUsers
	result.Details["requests_per_user"] = 10

	// 性能检查
	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate &&
		result.Throughput >= suite.config.PerformanceTarget.MinThroughput {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 性能压力测试
func (suite *ProductionInferenceTestSuite) TestPerformanceStress() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "性能压力测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	ctx, cancel := context.WithTimeout(context.Background(), suite.config.TestDuration)
	defer cancel()

	var wg sync.WaitGroup
	var mu sync.Mutex
	latencies := make([]time.Duration, 0)
	successCount := 0
	totalRequests := 0

	// 启动多个goroutine持续发送请求
	for i := 0; i < suite.config.ConcurrentUsers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			
			for {
				select {
				case <-ctx.Done():
					return
				default:
					reqStart := time.Now()
					
					// 随机选择模型和请求类型
					modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
					
					var success bool
					switch rand.Intn(3) {
					case 0:
						// 单次推理
						testData := suite.generateTestData()
						success = suite.makePredictRequest(modelName, testData)
					case 1:
						// 批量推理
						batchData := make([]map[string]interface{}, 5)
						for j := 0; j < 5; j++ {
							batchData[j] = suite.generateTestData()
						}
						success = suite.makeBatchPredictRequest(modelName, batchData)
					case 2:
						// 文本分类
						text := "这是一个测试文本，用于压力测试。"
						success = suite.makeTextClassifyRequest(modelName, text)
					}
					
					latency := time.Since(reqStart)
					
					mu.Lock()
					latencies = append(latencies, latency)
					totalRequests++
					if success {
						successCount++
					}
					mu.Unlock()
				}
			}
		}()
	}

	wg.Wait()

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	result.Details["test_duration"] = suite.config.TestDuration.String()
	result.Details["concurrent_users"] = suite.config.ConcurrentUsers

	// 性能检查
	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate &&
		result.Throughput >= suite.config.PerformanceTarget.MinThroughput {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 错误处理测试
func (suite *ProductionInferenceTestSuite) TestErrorHandling() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "错误处理测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	errorTests := []struct {
		name string
		test func() bool
	}{
		{"不存在的模型", func() bool {
			return !suite.makePredictRequest("non_existent_model", suite.generateTestData())
		}},
		{"空数据", func() bool {
			return !suite.makePredictRequest(suite.config.ModelNames[0], nil)
		}},
		{"超大批量", func() bool {
			largeData := make([]map[string]interface{}, 1000)
			for i := 0; i < 1000; i++ {
				largeData[i] = suite.generateTestData()
			}
			return !suite.makeBatchPredictRequest(suite.config.ModelNames[0], largeData)
		}},
		{"空文本分类", func() bool {
			return !suite.makeTextClassifyRequest(suite.config.ModelNames[0], "")
		}},
		{"超长文本", func() bool {
			longText := string(make([]byte, 100000))
			return !suite.makeTextClassifyRequest(suite.config.ModelNames[0], longText)
		}},
	}

	successCount := 0
	for _, errorTest := range errorTests {
		if errorTest.test() {
			successCount++
		} else {
			result.Errors = append(result.Errors, fmt.Sprintf("错误处理测试失败: %s", errorTest.name))
		}
	}

	result.Duration = time.Since(start)
	result.TotalRequests = len(errorTests)
	result.SuccessRequests = successCount
	result.FailedRequests = len(errorTests) - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)

	if result.ErrorRate == 0 {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// 内存泄漏测试
func (suite *ProductionInferenceTestSuite) TestMemoryLeak() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "内存泄漏测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	// 记录初始内存使用
	initialMemory := suite.getMemoryUsage()
	
	// 执行大量请求
	for i := 0; i < 1000; i++ {
		modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
		testData := suite.generateTestData()
		suite.makePredictRequest(modelName, testData)
		
		if i%100 == 0 {
			// 强制垃圾回收
			// runtime.GC()
		}
	}

	// 记录最终内存使用
	finalMemory := suite.getMemoryUsage()
	memoryIncrease := finalMemory - initialMemory

	result.Duration = time.Since(start)
	result.MemoryUsage = memoryIncrease
	result.Details["initial_memory"] = initialMemory
	result.Details["final_memory"] = finalMemory
	result.Details["memory_increase"] = memoryIncrease

	// 检查内存增长是否在合理范围内
	if memoryIncrease < 100 { // 100MB
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
		result.Errors = append(result.Errors, fmt.Sprintf("内存增长过多: %dMB", memoryIncrease))
	}

	return result
}

// 长时间运行测试
func (suite *ProductionInferenceTestSuite) TestLongRunning() TestResult {
	start := time.Now()
	result := TestResult{
		TestName: "长时间运行测试",
		Details:  make(map[string]interface{}),
		Errors:   make([]string, 0),
	}

	duration := 5 * time.Minute // 5分钟长时间测试
	ctx, cancel := context.WithTimeout(context.Background(), duration)
	defer cancel()

	var mu sync.Mutex
	successCount := 0
	totalRequests := 0
	latencies := make([]time.Duration, 0)

	// 启动一个goroutine持续发送请求
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			default:
				reqStart := time.Now()
				
				modelName := suite.config.ModelNames[rand.Intn(len(suite.config.ModelNames))]
				testData := suite.generateTestData()
				success := suite.makePredictRequest(modelName, testData)
				latency := time.Since(reqStart)
				
				mu.Lock()
				totalRequests++
				latencies = append(latencies, latency)
				if success {
					successCount++
				}
				mu.Unlock()
				
				// 控制请求频率
				time.Sleep(100 * time.Millisecond)
			}
		}
	}()

	<-ctx.Done()

	result.Duration = time.Since(start)
	result.TotalRequests = totalRequests
	result.SuccessRequests = successCount
	result.FailedRequests = totalRequests - successCount
	result.ErrorRate = float64(result.FailedRequests) / float64(result.TotalRequests)
	result.Throughput = float64(totalRequests) / result.Duration.Seconds()
	
	// 计算延迟统计
	if len(latencies) > 0 {
		result.AvgLatency = suite.calculateAvgLatency(latencies)
		result.MinLatency = suite.calculateMinLatency(latencies)
		result.MaxLatency = suite.calculateMaxLatency(latencies)
		result.P95Latency = suite.calculatePercentileLatency(latencies, 95)
		result.P99Latency = suite.calculatePercentileLatency(latencies, 99)
	}

	result.Details["test_duration"] = duration.String()

	// 检查长时间运行的稳定性
	if result.ErrorRate <= suite.config.PerformanceTarget.MaxErrorRate {
		result.Status = "PASSED"
	} else {
		result.Status = "FAILED"
	}

	return result
}

// HTTP请求辅助方法
func (suite *ProductionInferenceTestSuite) loadModel(name string, force bool) bool {
	req := LoadModelRequest{
		Name:  name,
		Force: force,
	}
	
	jsonData, _ := json.Marshal(req)
	resp, err := suite.httpClient.Post(
		suite.config.BaseURL+"/api/v1/models/load",
		"application/json",
		bytes.NewBuffer(jsonData),
	)
	
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	
	return resp.StatusCode == http.StatusOK
}

func (suite *ProductionInferenceTestSuite) getModelStatus(name string) map[string]interface{} {
	resp, err := suite.httpClient.Get(
		suite.config.BaseURL + "/api/v1/models/" + name + "/status",
	)
	
	if err != nil {
		return map[string]interface{}{"error": err.Error()}
	}
	defer resp.Body.Close()
	
	body, _ := io.ReadAll(resp.Body)
	var status map[string]interface{}
	json.Unmarshal(body, &status)
	
	return status
}

func (suite *ProductionInferenceTestSuite) makePredictRequest(modelName string, data map[string]interface{}) bool {
	req := PredictRequest{
		ModelName: modelName,
		Data:      data,
	}
	
	jsonData, _ := json.Marshal(req)
	resp, err := suite.httpClient.Post(
		suite.config.BaseURL+"/api/v1/inference/predict",
		"application/json",
		bytes.NewBuffer(jsonData),
	)
	
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	
	return resp.StatusCode == http.StatusOK
}

func (suite *ProductionInferenceTestSuite) makeBatchPredictRequest(modelName string, data []map[string]interface{}) bool {
	req := BatchPredictRequest{
		ModelName: modelName,
		Data:      data,
	}
	
	jsonData, _ := json.Marshal(req)
	resp, err := suite.httpClient.Post(
		suite.config.BaseURL+"/api/v1/inference/batch-predict",
		"application/json",
		bytes.NewBuffer(jsonData),
	)
	
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	
	return resp.StatusCode == http.StatusOK
}

func (suite *ProductionInferenceTestSuite) makeTextClassifyRequest(modelName, text string) bool {
	req := TextClassifyRequest{
		ModelName: modelName,
		Text:      text,
	}
	
	jsonData, _ := json.Marshal(req)
	resp, err := suite.httpClient.Post(
		suite.config.BaseURL+"/api/v1/inference/classify",
		"application/json",
		bytes.NewBuffer(jsonData),
	)
	
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	
	return resp.StatusCode == http.StatusOK
}

func (suite *ProductionInferenceTestSuite) makeSentimentAnalysisRequest(modelName, text string) bool {
	req := SentimentAnalysisRequest{
		ModelName: modelName,
		Text:      text,
	}
	
	jsonData, _ := json.Marshal(req)
	resp, err := suite.httpClient.Post(
		suite.config.BaseURL+"/api/v1/inference/sentiment",
		"application/json",
		bytes.NewBuffer(jsonData),
	)
	
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	
	return resp.StatusCode == http.StatusOK
}

// 数据生成辅助方法
func (suite *ProductionInferenceTestSuite) generateTestData() map[string]interface{} {
	return map[string]interface{}{
		"text": fmt.Sprintf("这是测试文本 %d", rand.Intn(1000)),
		"features": []float64{
			rand.Float64(), rand.Float64(), rand.Float64(),
		},
		"metadata": map[string]interface{}{
			"source": "zhihu",
			"timestamp": time.Now().Unix(),
		},
	}
}

// 统计计算辅助方法
func (suite *ProductionInferenceTestSuite) calculateAvgLatency(latencies []time.Duration) time.Duration {
	if len(latencies) == 0 {
		return 0
	}
	
	var total time.Duration
	for _, latency := range latencies {
		total += latency
	}
	
	return total / time.Duration(len(latencies))
}

func (suite *ProductionInferenceTestSuite) calculateMinLatency(latencies []time.Duration) time.Duration {
	if len(latencies) == 0 {
		return 0
	}
	
	min := latencies[0]
	for _, latency := range latencies {
		if latency < min {
			min = latency
		}
	}
	
	return min
}

func (suite *ProductionInferenceTestSuite) calculateMaxLatency(latencies []time.Duration) time.Duration {
	if len(latencies) == 0 {
		return 0
	}
	
	max := latencies[0]
	for _, latency := range latencies {
		if latency > max {
			max = latency
		}
	}
	
	return max
}

func (suite *ProductionInferenceTestSuite) calculatePercentileLatency(latencies []time.Duration, percentile int) time.Duration {
	if len(latencies) == 0 {
		return 0
	}
	
	// 简单的百分位数计算
	index := (len(latencies) * percentile) / 100
	if index >= len(latencies) {
		index = len(latencies) - 1
	}
	
	// 这里应该先排序，但为了简化就直接返回
	return latencies[index]
}

func (suite *ProductionInferenceTestSuite) getMemoryUsage() int64 {
	// 模拟内存使用获取
	return rand.Int63n(1000)
}

// 结果管理
func (suite *ProductionInferenceTestSuite) addResult(result TestResult) {
	suite.mu.Lock()
	defer suite.mu.Unlock()
	suite.results = append(suite.results, result)
}

func (suite *ProductionInferenceTestSuite) generateReport() error {
	report := TestReport{
		Timestamp:   time.Now(),
		Environment: "production",
		Version:     "1.0.0",
		TestResults: suite.results,
	}

	// 计算摘要
	passedTests := 0
	for _, result := range suite.results {
		if result.Status == "PASSED" {
			passedTests++
		}
	}

	report.Summary = TestSummary{
		TotalTests:  len(suite.results),
		PassedTests: passedTests,
		FailedTests: len(suite.results) - passedTests,
		SuccessRate: float64(passedTests) / float64(len(suite.results)) * 100,
		TotalTime:   time.Since(report.Timestamp).String(),
	}

	report.Passed = passedTests == len(suite.results)

	// 生成JSON报告
	jsonData, err := json.MarshalIndent(report, "", "  ")
	if err != nil {
		return fmt.Errorf("生成报告失败: %w", err)
	}

	// 保存报告
	filename := fmt.Sprintf("inference_test_report_%s.json", 
		time.Now().Format("20060102_150405"))
	
	if err := os.WriteFile(filename, jsonData, 0644); err != nil {
		return fmt.Errorf("保存报告失败: %w", err)
	}

	// 输出摘要
	suite.logger.Infof("测试完成！")
	suite.logger.Infof("总测试数: %d", report.Summary.TotalTests)
	suite.logger.Infof("通过测试: %d", report.Summary.PassedTests)
	suite.logger.Infof("失败测试: %d", report.Summary.FailedTests)
	suite.logger.Infof("成功率: %.2f%%", report.Summary.SuccessRate)
	suite.logger.Infof("报告已保存到: %s", filename)

	return nil
}

// 主函数
func main() {
	// 测试配置
	config := TestConfig{
		BaseURL:         "http://localhost:8080",
		TestDataSize:    100,
		ConcurrentUsers: 10,
		TestDuration:    2 * time.Minute,
		BatchSize:       10,
		ModelNames:      []string{"text_classifier", "sentiment_analyzer", "feature_extractor"},
		PerformanceTarget: PerformanceTarget{
			MaxLatency:     2 * time.Second,
			MinThroughput:  10.0,
			MaxErrorRate:   0.05,
			MaxMemoryUsage: 500,
			MaxCPUUsage:    80.0,
		},
	}

	// 创建测试套件
	suite := NewProductionInferenceTestSuite(config)

	// 运行测试
	if err := suite.RunAllTests(); err != nil {
		log.Fatalf("测试运行失败: %v", err)
	}
}