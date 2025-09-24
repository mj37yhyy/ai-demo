package collector

import (
	"context"

	pb "github.com/mj37yhyy/ai-demo/go-services/data-collector/proto"
)

// Collector 定义了文本采集器的接口
type Collector interface {
	// Collect 执行文本采集
	// ctx: 上下文，用于取消操作
	// source: 采集源配置
	// config: 采集配置
	// textChan: 文本输出通道
	Collect(ctx context.Context, source *pb.CollectionSource, config *pb.CollectionConfig, textChan chan<- *pb.RawText) error
}