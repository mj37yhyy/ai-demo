package collector

import (
	"bufio"
	"context"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"

	"github.com/text-audit/data-collector/internal/config"
	pb "github.com/text-audit/data-collector/proto"
)

type FileCollector struct {
	config *config.Config
}

type JSONTextItem struct {
	Content string            `json:"content"`
	Source  string            `json:"source,omitempty"`
	Meta    map[string]string `json:"meta,omitempty"`
}

func NewFileCollector(cfg *config.Config) (*FileCollector, error) {
	return &FileCollector{
		config: cfg,
	}, nil
}

func (c *FileCollector) Collect(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	filePath := source.FilePath
	logrus.WithField("file_path", filePath).Info("Starting file collection")

	// 检查文件是否存在
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		return fmt.Errorf("file does not exist: %s", filePath)
	}

	// 根据文件扩展名选择处理方法
	ext := strings.ToLower(filepath.Ext(filePath))
	
	var err error
	switch ext {
	case ".txt":
		err = c.collectFromTXT(ctx, filePath, config, textChan)
	case ".csv":
		err = c.collectFromCSV(ctx, filePath, source.Parameters, config, textChan)
	case ".json":
		err = c.collectFromJSON(ctx, filePath, config, textChan)
	case ".jsonl":
		err = c.collectFromJSONL(ctx, filePath, config, textChan)
	default:
		// 默认按文本文件处理
		err = c.collectFromTXT(ctx, filePath, config, textChan)
	}

	if err != nil {
		return fmt.Errorf("failed to collect from file: %w", err)
	}

	logrus.WithField("file_path", filePath).Info("File collection completed")
	return nil
}

func (c *FileCollector) collectFromTXT(ctx context.Context, filePath string, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 10000 // 默认最大采集数量
	}

	for scanner.Scan() && collected < maxCount {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		line := strings.TrimSpace(scanner.Text())
		if !c.applyFilters(line, config.Filters) {
			continue
		}

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   line,
			Source:    fmt.Sprintf("file:%s", filepath.Base(filePath)),
			Timestamp: time.Now().UnixMilli(),
			Metadata: map[string]string{
				"file_path": filePath,
				"line_num":  fmt.Sprintf("%d", collected+1),
			},
		}

		select {
		case textChan <- rawText:
			collected++
			if collected%100 == 0 {
				logrus.WithField("collected", collected).Debug("Progress update")
			}
		case <-ctx.Done():
			return ctx.Err()
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("error reading file: %w", err)
	}

	logrus.WithField("total_collected", collected).Info("TXT file processing completed")
	return nil
}

func (c *FileCollector) collectFromCSV(ctx context.Context, filePath string, params map[string]string, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	reader := csv.NewReader(file)
	
	// 设置CSV参数
	if delimiter, exists := params["delimiter"]; exists && len(delimiter) > 0 {
		reader.Comma = rune(delimiter[0])
	}
	
	// 读取表头
	headers, err := reader.Read()
	if err != nil {
		return fmt.Errorf("failed to read CSV headers: %w", err)
	}

	// 确定文本列索引
	textColumnIndex := c.findTextColumn(headers, params)
	if textColumnIndex == -1 {
		return fmt.Errorf("no text column found in CSV")
	}

	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 10000
	}

	for collected < maxCount {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		record, err := reader.Read()
		if err == io.EOF {
			break
		}
		if err != nil {
			logrus.WithError(err).Warn("Error reading CSV record, skipping")
			continue
		}

		if textColumnIndex >= len(record) {
			continue
		}

		content := strings.TrimSpace(record[textColumnIndex])
		if !c.applyFilters(content, config.Filters) {
			continue
		}

		// 构建元数据
		metadata := map[string]string{
			"file_path": filePath,
			"row_num":   fmt.Sprintf("%d", collected+2), // +2 因为有表头且从1开始计数
		}

		// 添加其他列作为元数据
		for i, header := range headers {
			if i != textColumnIndex && i < len(record) {
				metadata[header] = record[i]
			}
		}

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   content,
			Source:    fmt.Sprintf("csv:%s", filepath.Base(filePath)),
			Timestamp: time.Now().UnixMilli(),
			Metadata:  metadata,
		}

		select {
		case textChan <- rawText:
			collected++
			if collected%100 == 0 {
				logrus.WithField("collected", collected).Debug("Progress update")
			}
		case <-ctx.Done():
			return ctx.Err()
		}
	}

	logrus.WithField("total_collected", collected).Info("CSV file processing completed")
	return nil
}

func (c *FileCollector) collectFromJSON(ctx context.Context, filePath string, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	var data []JSONTextItem
	decoder := json.NewDecoder(file)
	if err := decoder.Decode(&data); err != nil {
		return fmt.Errorf("failed to decode JSON: %w", err)
	}

	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = int32(len(data))
	}

	for i, item := range data {
		if collected >= maxCount {
			break
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		if !c.applyFilters(item.Content, config.Filters) {
			continue
		}

		metadata := map[string]string{
			"file_path": filePath,
			"index":     fmt.Sprintf("%d", i),
		}

		// 添加item中的元数据
		for k, v := range item.Meta {
			metadata[k] = v
		}

		source := item.Source
		if source == "" {
			source = fmt.Sprintf("json:%s", filepath.Base(filePath))
		}

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   item.Content,
			Source:    source,
			Timestamp: time.Now().UnixMilli(),
			Metadata:  metadata,
		}

		select {
		case textChan <- rawText:
			collected++
		case <-ctx.Done():
			return ctx.Err()
		}
	}

	logrus.WithField("total_collected", collected).Info("JSON file processing completed")
	return nil
}

func (c *FileCollector) collectFromJSONL(ctx context.Context, filePath string, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	collected := int32(0)
	maxCount := config.MaxCount
	if maxCount <= 0 {
		maxCount = 10000
	}

	lineNum := 0
	for scanner.Scan() && collected < maxCount {
		lineNum++
		
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		var item JSONTextItem
		if err := json.Unmarshal([]byte(line), &item); err != nil {
			logrus.WithError(err).WithField("line", lineNum).Warn("Failed to parse JSON line, skipping")
			continue
		}

		if !c.applyFilters(item.Content, config.Filters) {
			continue
		}

		metadata := map[string]string{
			"file_path": filePath,
			"line_num":  fmt.Sprintf("%d", lineNum),
		}

		// 添加item中的元数据
		for k, v := range item.Meta {
			metadata[k] = v
		}

		source := item.Source
		if source == "" {
			source = fmt.Sprintf("jsonl:%s", filepath.Base(filePath))
		}

		rawText := &pb.RawText{
			Id:        uuid.New().String(),
			Content:   item.Content,
			Source:    source,
			Timestamp: time.Now().UnixMilli(),
			Metadata:  metadata,
		}

		select {
		case textChan <- rawText:
			collected++
			if collected%100 == 0 {
				logrus.WithField("collected", collected).Debug("Progress update")
			}
		case <-ctx.Done():
			return ctx.Err()
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("error reading file: %w", err)
	}

	logrus.WithField("total_collected", collected).Info("JSONL file processing completed")
	return nil
}

func (c *FileCollector) findTextColumn(headers []string, params map[string]string) int {
	// 如果参数中指定了文本列
	if textColumn, exists := params["text_column"]; exists {
		for i, header := range headers {
			if header == textColumn {
				return i
			}
		}
	}

	// 自动检测文本列
	textColumnNames := []string{"content", "text", "message", "comment", "description", "body"}
	for _, name := range textColumnNames {
		for i, header := range headers {
			if strings.ToLower(header) == name {
				return i
			}
		}
	}

	// 如果没找到，使用第一列
	if len(headers) > 0 {
		return 0
	}

	return -1
}

func (c *FileCollector) applyFilters(content string, filters []string) bool {
	if len(filters) == 0 {
		return true
	}

	content = strings.TrimSpace(content)
	
	// 基本长度过滤
	if len(content) < 5 || len(content) > 2000 {
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