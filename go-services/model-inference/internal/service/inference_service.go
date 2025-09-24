package service

import (
	"context"
	"encoding/json"
	"fmt"
	"math/rand"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"

	"github.com/textaudit/model-inference/internal/config"
	"github.com/textaudit/model-inference/internal/model"
	"github.com/textaudit/model-inference/internal/repository"
)

// InferenceService 推理服务接口
type InferenceService interface {
	Predict(ctx context.Context, req *model.PredictRequest) (*model.PredictResponse, error)
	BatchPredict(ctx context.Context, req *model.BatchPredictRequest) (*model.BatchPredictResponse, error)
	ClassifyText(ctx context.Context, req *model.TextClassifyRequest) (*model.TextAnalysisResponse, error)
	AnalyzeSentiment(ctx context.Context, req *model.SentimentAnalysisRequest) (*model.TextAnalysisResponse, error)
	ExtractFeatures(ctx context.Context, req *model.FeatureExtractionRequest) (*model.TextAnalysisResponse, error)
	DetectAnomaly(ctx context.Context, req *model.AnomalyDetectionRequest) (*model.TextAnalysisResponse, error)
	GetHistory(ctx context.Context, limit, offset int) ([]*model.InferenceRequest, error)
	GetInferenceResult(ctx context.Context, requestID string) (*model.InferenceRequest, error)
	GetStatistics(ctx context.Context) (*model.InferenceStatistics, error)
}

// inferenceService 推理服务实现
type inferenceService struct {
	inferenceRepo repository.InferenceRepository
	modelService  ModelService
	cacheRepo     repository.CacheRepository
	config        config.InferenceConfig
}

// NewInferenceService 创建推理服务
func NewInferenceService(
	inferenceRepo repository.InferenceRepository,
	modelService ModelService,
	cacheRepo repository.CacheRepository,
	cfg config.InferenceConfig,
) InferenceService {
	return &inferenceService{
		inferenceRepo: inferenceRepo,
		modelService:  modelService,
		cacheRepo:     cacheRepo,
		config:        cfg,
	}
}

// Predict 单次预测
func (s *inferenceService) Predict(ctx context.Context, req *model.PredictRequest) (*model.PredictResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	// 创建推理请求记录
	inputData, _ := json.Marshal(req.Data)
	inferenceReq := &model.InferenceRequest{
		RequestID: requestID,
		ModelName: req.ModelName,
		InputData: string(inputData),
		Status:    model.InferenceStatusRunning,
		StartTime: startTime,
	}

	if err := s.inferenceRepo.Create(inferenceReq); err != nil {
		logrus.Errorf("创建推理请求记录失败: %v", err)
	}

	// 执行推理
	prediction, confidence, err := s.performInference(ctx, req.ModelName, req.Data)
	duration := time.Since(startTime).Milliseconds()

	if err != nil {
		// 更新错误状态
		s.inferenceRepo.UpdateError(requestID, err.Error(), time.Now(), duration)
		return nil, fmt.Errorf("推理失败: %w", err)
	}

	// 更新成功结果
	resultData, _ := json.Marshal(map[string]interface{}{
		"prediction": prediction,
		"confidence": confidence,
	})
	s.inferenceRepo.UpdateResult(requestID, string(resultData), time.Now(), duration)

	// 构建响应
	response := &model.PredictResponse{
		RequestID:  requestID,
		ModelName:  req.ModelName,
		Prediction: prediction,
		Confidence: confidence,
		Duration:   duration,
	}

	// 缓存结果
	cacheKey := fmt.Sprintf("inference_result:%s", requestID)
	s.cacheRepo.Set(ctx, cacheKey, response, time.Duration(s.config.ResultCacheTTL)*time.Second)

	return response, nil
}

// BatchPredict 批量预测
func (s *inferenceService) BatchPredict(ctx context.Context, req *model.BatchPredictRequest) (*model.BatchPredictResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查批量大小限制
	if len(req.Data) > s.config.MaxBatchSize {
		return nil, fmt.Errorf("批量大小超过限制 %d", s.config.MaxBatchSize)
	}

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	var predictions []model.PredictResponse

	// 批量处理
	for i, data := range req.Data {
		prediction, confidence, err := s.performInference(ctx, req.ModelName, data)
		if err != nil {
			logrus.Errorf("批量推理第 %d 项失败: %v", i, err)
			continue
		}

		predictions = append(predictions, model.PredictResponse{
			RequestID:  fmt.Sprintf("%s_%d", requestID, i),
			ModelName:  req.ModelName,
			Prediction: prediction,
			Confidence: confidence,
		})
	}

	duration := time.Since(startTime).Milliseconds()

	response := &model.BatchPredictResponse{
		RequestID:   requestID,
		ModelName:   req.ModelName,
		Predictions: predictions,
		Duration:    duration,
	}

	return response, nil
}

// ClassifyText 文本分类
func (s *inferenceService) ClassifyText(ctx context.Context, req *model.TextClassifyRequest) (*model.TextAnalysisResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	// 执行文本分类
	result, confidence, err := s.performTextClassification(ctx, req.ModelName, req.Text)
	if err != nil {
		return nil, fmt.Errorf("文本分类失败: %w", err)
	}

	duration := time.Since(startTime).Milliseconds()

	response := &model.TextAnalysisResponse{
		RequestID:  requestID,
		ModelName:  req.ModelName,
		Text:       req.Text,
		Result:     result,
		Confidence: confidence,
		Duration:   duration,
	}

	return response, nil
}

// AnalyzeSentiment 情感分析
func (s *inferenceService) AnalyzeSentiment(ctx context.Context, req *model.SentimentAnalysisRequest) (*model.TextAnalysisResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	// 执行情感分析
	result, confidence, err := s.performSentimentAnalysis(ctx, req.ModelName, req.Text)
	if err != nil {
		return nil, fmt.Errorf("情感分析失败: %w", err)
	}

	duration := time.Since(startTime).Milliseconds()

	response := &model.TextAnalysisResponse{
		RequestID:  requestID,
		ModelName:  req.ModelName,
		Text:       req.Text,
		Result:     result,
		Confidence: confidence,
		Duration:   duration,
	}

	return response, nil
}

// ExtractFeatures 特征提取
func (s *inferenceService) ExtractFeatures(ctx context.Context, req *model.FeatureExtractionRequest) (*model.TextAnalysisResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	// 执行特征提取
	features, err := s.performFeatureExtraction(ctx, req.ModelName, req.Text)
	if err != nil {
		return nil, fmt.Errorf("特征提取失败: %w", err)
	}

	duration := time.Since(startTime).Milliseconds()

	response := &model.TextAnalysisResponse{
		RequestID: requestID,
		ModelName: req.ModelName,
		Text:      req.Text,
		Result:    "features_extracted",
		Features:  features,
		Duration:  duration,
	}

	return response, nil
}

// DetectAnomaly 异常检测
func (s *inferenceService) DetectAnomaly(ctx context.Context, req *model.AnomalyDetectionRequest) (*model.TextAnalysisResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	// 检查模型是否已加载
	if !s.modelService.IsModelLoaded(req.ModelName) {
		return nil, fmt.Errorf("模型 %s 未加载", req.ModelName)
	}

	// 执行异常检测
	result, confidence, err := s.performAnomalyDetection(ctx, req.ModelName, req.Data)
	if err != nil {
		return nil, fmt.Errorf("异常检测失败: %w", err)
	}

	duration := time.Since(startTime).Milliseconds()

	response := &model.TextAnalysisResponse{
		RequestID:  requestID,
		ModelName:  req.ModelName,
		Result:     result,
		Confidence: confidence,
		Duration:   duration,
	}

	return response, nil
}

// GetHistory 获取推理历史
func (s *inferenceService) GetHistory(ctx context.Context, limit, offset int) ([]*model.InferenceRequest, error) {
	return s.inferenceRepo.List(limit, offset)
}

// GetInferenceResult 获取推理结果
func (s *inferenceService) GetInferenceResult(ctx context.Context, requestID string) (*model.InferenceRequest, error) {
	return s.inferenceRepo.GetByRequestID(requestID)
}

// GetStatistics 获取推理统计信息
func (s *inferenceService) GetStatistics(ctx context.Context) (*model.InferenceStatistics, error) {
	return s.inferenceRepo.GetStatistics()
}

// performInference 执行推理（模拟实现）
func (s *inferenceService) performInference(ctx context.Context, modelName string, data map[string]interface{}) (interface{}, float64, error) {
	// 模拟推理延迟
	time.Sleep(time.Duration(rand.Intn(100)) * time.Millisecond)

	// 模拟推理结果
	prediction := map[string]interface{}{
		"class":       "positive",
		"probability": 0.85,
		"scores": map[string]float64{
			"positive": 0.85,
			"negative": 0.15,
		},
	}

	confidence := 0.85

	return prediction, confidence, nil
}

// performTextClassification 执行文本分类（模拟实现）
func (s *inferenceService) performTextClassification(ctx context.Context, modelName string, text string) (interface{}, float64, error) {
	// 模拟文本分类
	time.Sleep(time.Duration(rand.Intn(50)) * time.Millisecond)

	classes := []string{"正常", "违规", "疑似违规"}
	selectedClass := classes[rand.Intn(len(classes))]
	confidence := 0.7 + rand.Float64()*0.3

	result := map[string]interface{}{
		"class":      selectedClass,
		"confidence": confidence,
	}

	return result, confidence, nil
}

// performSentimentAnalysis 执行情感分析（模拟实现）
func (s *inferenceService) performSentimentAnalysis(ctx context.Context, modelName string, text string) (interface{}, float64, error) {
	// 模拟情感分析
	time.Sleep(time.Duration(rand.Intn(50)) * time.Millisecond)

	sentiments := []string{"积极", "消极", "中性"}
	selectedSentiment := sentiments[rand.Intn(len(sentiments))]
	confidence := 0.6 + rand.Float64()*0.4

	result := map[string]interface{}{
		"sentiment":  selectedSentiment,
		"confidence": confidence,
		"scores": map[string]float64{
			"积极": rand.Float64(),
			"消极": rand.Float64(),
			"中性": rand.Float64(),
		},
	}

	return result, confidence, nil
}

// performFeatureExtraction 执行特征提取（模拟实现）
func (s *inferenceService) performFeatureExtraction(ctx context.Context, modelName string, text string) (map[string]interface{}, error) {
	// 模拟特征提取
	time.Sleep(time.Duration(rand.Intn(30)) * time.Millisecond)

	features := map[string]interface{}{
		"word_count":     len(text),
		"char_count":     len([]rune(text)),
		"sentence_count": 1,
		"embeddings":     make([]float64, 128), // 模拟词向量
		"keywords":       []string{"关键词1", "关键词2"},
	}

	// 填充模拟词向量
	for i := range features["embeddings"].([]float64) {
		features["embeddings"].([]float64)[i] = rand.Float64()
	}

	return features, nil
}

// performAnomalyDetection 执行异常检测（模拟实现）
func (s *inferenceService) performAnomalyDetection(ctx context.Context, modelName string, data map[string]interface{}) (interface{}, float64, error) {
	// 模拟异常检测
	time.Sleep(time.Duration(rand.Intn(80)) * time.Millisecond)

	isAnomaly := rand.Float64() > 0.8
	confidence := 0.5 + rand.Float64()*0.5

	result := map[string]interface{}{
		"is_anomaly":    isAnomaly,
		"anomaly_score": rand.Float64(),
		"confidence":    confidence,
	}

	return result, confidence, nil
}