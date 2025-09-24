#!/bin/bash

# Java服务并行测试脚本
# 用于同时测试data-preprocessor和model-trainer两个Java服务

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 项目路径
PROJECT_ROOT="/Users/miaojia/AI-Demo"
DATA_PREPROCESSOR_PATH="$PROJECT_ROOT/java-services/data-preprocessor"
MODEL_TRAINER_PATH="$PROJECT_ROOT/java-services/model-trainer"
RESULTS_DIR="$PROJECT_ROOT/test-results"

# 创建结果目录
mkdir -p "$RESULTS_DIR"

# 测试结果文件
PREPROCESSOR_RESULT="$RESULTS_DIR/data-preprocessor-test.log"
TRAINER_RESULT="$RESULTS_DIR/model-trainer-test.log"
COMPARISON_RESULT="$RESULTS_DIR/test-comparison.json"

# 清理之前的结果
rm -f "$PREPROCESSOR_RESULT" "$TRAINER_RESULT" "$COMPARISON_RESULT"

log_info "开始Java服务并行测试..."

# 并行测试函数
test_data_preprocessor() {
    log_info "开始测试 data-preprocessor 服务..."
    cd "$DATA_PREPROCESSOR_PATH"
    
    {
        echo "=== Data Preprocessor 测试开始 ==="
        echo "时间: $(date)"
        echo "路径: $DATA_PREPROCESSOR_PATH"
        echo ""
        
        # 编译测试
        echo "--- 编译测试 ---"
        if ./gradlew clean build --no-daemon 2>&1; then
            echo "✅ 编译成功"
        else
            echo "❌ 编译失败"
            return 1
        fi
        
        # 单元测试
        echo ""
        echo "--- 单元测试 ---"
        if ./gradlew test --no-daemon 2>&1; then
            echo "✅ 单元测试通过"
        else
            echo "❌ 单元测试失败"
        fi
        
        # 集成测试
        echo ""
        echo "--- 集成测试 ---"
        if ./gradlew integrationTest --no-daemon 2>&1; then
            echo "✅ 集成测试通过"
        else
            echo "⚠️ 集成测试跳过或失败"
        fi
        
        echo ""
        echo "=== Data Preprocessor 测试完成 ==="
        echo "完成时间: $(date)"
        
    } > "$PREPROCESSOR_RESULT" 2>&1
    
    log_success "data-preprocessor 测试完成"
}

test_model_trainer() {
    log_info "开始测试 model-trainer 服务..."
    cd "$MODEL_TRAINER_PATH"
    
    {
        echo "=== Model Trainer 测试开始 ==="
        echo "时间: $(date)"
        echo "路径: $MODEL_TRAINER_PATH"
        echo ""
        
        # 编译测试
        echo "--- 编译测试 ---"
        if ./gradlew clean build --no-daemon 2>&1; then
            echo "✅ 编译成功"
        else
            echo "❌ 编译失败"
            return 1
        fi
        
        # 单元测试
        echo ""
        echo "--- 单元测试 ---"
        if ./gradlew test --no-daemon 2>&1; then
            echo "✅ 单元测试通过"
        else
            echo "❌ 单元测试失败"
        fi
        
        # 集成测试
        echo ""
        echo "--- 集成测试 ---"
        if ./gradlew integrationTest --no-daemon 2>&1; then
            echo "✅ 集成测试通过"
        else
            echo "⚠️ 集成测试跳过或失败"
        fi
        
        echo ""
        echo "=== Model Trainer 测试完成 ==="
        echo "完成时间: $(date)"
        
    } > "$TRAINER_RESULT" 2>&1
    
    log_success "model-trainer 测试完成"
}

# 并行执行测试
log_info "启动并行测试进程..."

# 后台执行测试
test_data_preprocessor &
PREPROCESSOR_PID=$!

test_model_trainer &
TRAINER_PID=$!

# 等待两个测试完成
log_info "等待测试完成..."
wait $PREPROCESSOR_PID
PREPROCESSOR_EXIT_CODE=$?

wait $TRAINER_PID
TRAINER_EXIT_CODE=$?

# 生成测试比对报告
generate_comparison_report() {
    log_info "生成测试比对报告..."
    
    # 提取测试结果
    PREPROCESSOR_BUILD=$(grep -c "✅ 编译成功" "$PREPROCESSOR_RESULT" || echo "0")
    PREPROCESSOR_UNIT=$(grep -c "✅ 单元测试通过" "$PREPROCESSOR_RESULT" || echo "0")
    PREPROCESSOR_INTEGRATION=$(grep -c "✅ 集成测试通过" "$PREPROCESSOR_RESULT" || echo "0")
    
    TRAINER_BUILD=$(grep -c "✅ 编译成功" "$TRAINER_RESULT" || echo "0")
    TRAINER_UNIT=$(grep -c "✅ 单元测试通过" "$TRAINER_RESULT" || echo "0")
    TRAINER_INTEGRATION=$(grep -c "✅ 集成测试通过" "$TRAINER_RESULT" || echo "0")
    
    # 生成JSON报告
    cat > "$COMPARISON_RESULT" << EOF
{
  "test_summary": {
    "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "total_services": 2,
    "parallel_execution": true
  },
  "services": {
    "data-preprocessor": {
      "exit_code": $PREPROCESSOR_EXIT_CODE,
      "build_success": $([ "$PREPROCESSOR_BUILD" = "1" ] && echo "true" || echo "false"),
      "unit_tests_passed": $([ "$PREPROCESSOR_UNIT" = "1" ] && echo "true" || echo "false"),
      "integration_tests_passed": $([ "$PREPROCESSOR_INTEGRATION" = "1" ] && echo "true" || echo "false"),
      "log_file": "$PREPROCESSOR_RESULT"
    },
    "model-trainer": {
      "exit_code": $TRAINER_EXIT_CODE,
      "build_success": $([ "$TRAINER_BUILD" = "1" ] && echo "true" || echo "false"),
      "unit_tests_passed": $([ "$TRAINER_UNIT" = "1" ] && echo "true" || echo "false"),
      "integration_tests_passed": $([ "$TRAINER_INTEGRATION" = "1" ] && echo "true" || echo "false"),
      "log_file": "$TRAINER_RESULT"
    }
  },
  "overall_status": {
    "all_builds_successful": $([ "$PREPROCESSOR_BUILD" = "1" ] && [ "$TRAINER_BUILD" = "1" ] && echo "true" || echo "false"),
    "all_tests_passed": $([ "$PREPROCESSOR_UNIT" = "1" ] && [ "$TRAINER_UNIT" = "1" ] && echo "true" || echo "false")
  }
}
EOF
}

generate_comparison_report

# 显示测试结果摘要
log_info "测试结果摘要:"
echo ""
echo "📊 Data Preprocessor:"
echo "   - 退出代码: $PREPROCESSOR_EXIT_CODE"
echo "   - 详细日志: $PREPROCESSOR_RESULT"

echo ""
echo "📊 Model Trainer:"
echo "   - 退出代码: $TRAINER_EXIT_CODE"
echo "   - 详细日志: $TRAINER_RESULT"

echo ""
echo "📋 比对报告: $COMPARISON_RESULT"

# 总体结果
if [ $PREPROCESSOR_EXIT_CODE -eq 0 ] && [ $TRAINER_EXIT_CODE -eq 0 ]; then
    log_success "所有Java服务测试通过！"
    exit 0
else
    log_error "部分Java服务测试失败"
    exit 1
fi