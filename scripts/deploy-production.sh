#!/bin/bash

# AI Demo 生产环境部署脚本
# 支持完整的服务编排、监控和调试功能

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 配置变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.production.yml"
LOG_DIR="$PROJECT_ROOT/logs"
DATA_DIR="$PROJECT_ROOT/data"

# 服务列表
SERVICES=(
    "mysql"
    "redis" 
    "zookeeper"
    "kafka"
    "minio"
    "data-collector"
    "data-preprocessor"
    "model-trainer"
    "model-inference"
    "prometheus"
    "grafana"
    "nginx"
)

# 核心服务（必须成功启动）
CORE_SERVICES=(
    "mysql"
    "redis"
    "kafka"
    "data-collector"
    "data-preprocessor"
    "model-trainer"
    "model-inference"
)

# 打印带颜色的消息
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}[$(date '+%Y-%m-%d %H:%M:%S')] ${message}${NC}"
}

print_info() {
    print_message "$BLUE" "ℹ️  $1"
}

print_success() {
    print_message "$GREEN" "✅ $1"
}

print_warning() {
    print_message "$YELLOW" "⚠️  $1"
}

print_error() {
    print_message "$RED" "❌ $1"
}

print_header() {
    echo
    print_message "$PURPLE" "🚀 $1"
    echo "=================================================="
}

# 检查依赖
check_dependencies() {
    print_header "检查系统依赖"
    
    local missing_deps=()
    
    # 检查Docker
    if ! command -v docker &> /dev/null; then
        missing_deps+=("docker")
    else
        print_success "Docker 已安装: $(docker --version)"
    fi
    
    # 检查Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        missing_deps+=("docker-compose")
    else
        if command -v docker-compose &> /dev/null; then
            print_success "Docker Compose 已安装: $(docker-compose --version)"
        else
            print_success "Docker Compose 已安装: $(docker compose version)"
        fi
    fi
    
    # 检查curl
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    else
        print_success "curl 已安装"
    fi
    
    # 检查jq（用于JSON处理）
    if ! command -v jq &> /dev/null; then
        print_warning "jq 未安装，某些功能可能受限"
    else
        print_success "jq 已安装"
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        print_error "缺少以下依赖: ${missing_deps[*]}"
        print_info "请先安装缺少的依赖，然后重新运行脚本"
        exit 1
    fi
}

# 检查Docker服务状态
check_docker_service() {
    print_header "检查Docker服务状态"
    
    if ! docker info &> /dev/null; then
        print_error "Docker 服务未运行，请启动Docker服务"
        exit 1
    fi
    
    print_success "Docker 服务运行正常"
}

# 创建必要的目录
create_directories() {
    print_header "创建必要的目录结构"
    
    local dirs=(
        "$LOG_DIR"
        "$DATA_DIR"
        "$PROJECT_ROOT/models"
        "$PROJECT_ROOT/checkpoints"
        "$PROJECT_ROOT/monitoring/grafana/dashboards"
        "$PROJECT_ROOT/monitoring/grafana/datasources"
    )
    
    for dir in "${dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            print_success "创建目录: $dir"
        else
            print_info "目录已存在: $dir"
        fi
    done
}

# 检查配置文件
check_config_files() {
    print_header "检查配置文件"
    
    local config_files=(
        "$COMPOSE_FILE"
        "$PROJECT_ROOT/nginx/nginx.conf"
        "$PROJECT_ROOT/monitoring/prometheus.yml"
        "$PROJECT_ROOT/docker/mysql/init.sql"
        "$PROJECT_ROOT/docker/mysql/my.cnf"
    )
    
    local missing_files=()
    
    for file in "${config_files[@]}"; do
        if [ ! -f "$file" ]; then
            missing_files+=("$file")
        else
            print_success "配置文件存在: $(basename "$file")"
        fi
    done
    
    if [ ${#missing_files[@]} -ne 0 ]; then
        print_error "缺少以下配置文件:"
        for file in "${missing_files[@]}"; do
            print_error "  - $file"
        done
        exit 1
    fi
}

# 清理旧的容器和网络
cleanup_old_deployment() {
    print_header "清理旧的部署"
    
    print_info "停止并删除旧的容器..."
    docker-compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
    
    print_info "清理未使用的Docker资源..."
    docker system prune -f --volumes 2>/dev/null || true
    
    print_success "清理完成"
}

# 拉取最新镜像
pull_images() {
    print_header "拉取最新镜像"
    
    print_info "拉取基础镜像..."
    docker-compose -f "$COMPOSE_FILE" pull --ignore-pull-failures
    
    print_success "镜像拉取完成"
}

# 构建自定义镜像
build_images() {
    print_header "构建自定义镜像"
    
    print_info "构建应用镜像..."
    docker-compose -f "$COMPOSE_FILE" build --no-cache --parallel
    
    print_success "镜像构建完成"
}

# 启动基础设施服务
start_infrastructure() {
    print_header "启动基础设施服务"
    
    local infra_services=("mysql" "redis" "zookeeper" "kafka" "minio")
    
    for service in "${infra_services[@]}"; do
        print_info "启动服务: $service"
        docker-compose -f "$COMPOSE_FILE" up -d "$service"
        
        # 等待服务健康检查通过
        wait_for_service_health "$service"
    done
    
    print_success "基础设施服务启动完成"
}

# 启动应用服务
start_applications() {
    print_header "启动应用服务"
    
    local app_services=("data-collector" "data-preprocessor" "model-trainer" "model-inference")
    
    for service in "${app_services[@]}"; do
        print_info "启动服务: $service"
        docker-compose -f "$COMPOSE_FILE" up -d "$service"
        
        # 等待服务启动
        sleep 10
        wait_for_service_health "$service"
    done
    
    print_success "应用服务启动完成"
}

# 启动监控服务
start_monitoring() {
    print_header "启动监控服务"
    
    local monitoring_services=("prometheus" "grafana" "nginx")
    
    for service in "${monitoring_services[@]}"; do
        print_info "启动服务: $service"
        docker-compose -f "$COMPOSE_FILE" up -d "$service"
        sleep 5
    done
    
    print_success "监控服务启动完成"
}

# 等待服务健康检查
wait_for_service_health() {
    local service=$1
    local max_attempts=30
    local attempt=1
    
    print_info "等待服务 $service 健康检查通过..."
    
    while [ $attempt -le $max_attempts ]; do
        local health_status=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" | xargs docker inspect --format='{{.State.Health.Status}}' 2>/dev/null || echo "no-health-check")
        
        if [ "$health_status" = "healthy" ] || [ "$health_status" = "no-health-check" ]; then
            # 如果没有健康检查，检查容器是否运行
            if [ "$health_status" = "no-health-check" ]; then
                local container_status=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" | xargs docker inspect --format='{{.State.Status}}' 2>/dev/null || echo "not-running")
                if [ "$container_status" = "running" ]; then
                    print_success "服务 $service 运行正常（无健康检查）"
                    return 0
                fi
            else
                print_success "服务 $service 健康检查通过"
                return 0
            fi
        fi
        
        if [ "$health_status" = "unhealthy" ]; then
            print_warning "服务 $service 健康检查失败，尝试 $attempt/$max_attempts"
        else
            print_info "等待服务 $service 启动... ($attempt/$max_attempts)"
        fi
        
        sleep 10
        ((attempt++))
    done
    
    print_error "服务 $service 健康检查超时"
    show_service_logs "$service"
    return 1
}

# 显示服务日志
show_service_logs() {
    local service=$1
    print_warning "显示服务 $service 的最近日志:"
    docker-compose -f "$COMPOSE_FILE" logs --tail=20 "$service" || true
}

# 验证服务状态
verify_services() {
    print_header "验证服务状态"
    
    local failed_services=()
    
    for service in "${CORE_SERVICES[@]}"; do
        local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
        
        if [ -z "$container_id" ]; then
            print_error "服务 $service 未运行"
            failed_services+=("$service")
            continue
        fi
        
        local status=$(docker inspect --format='{{.State.Status}}' "$container_id" 2>/dev/null || echo "unknown")
        
        if [ "$status" = "running" ]; then
            print_success "服务 $service 运行正常"
        else
            print_error "服务 $service 状态异常: $status"
            failed_services+=("$service")
        fi
    done
    
    if [ ${#failed_services[@]} -ne 0 ]; then
        print_error "以下核心服务启动失败: ${failed_services[*]}"
        return 1
    fi
    
    print_success "所有核心服务运行正常"
}

# 检查服务端点
check_service_endpoints() {
    print_header "检查服务端点"
    
    local endpoints=(
        "http://localhost:8081/health:数据采集服务"
        "http://localhost:8082/actuator/health:数据预处理服务"
        "http://localhost:8084/actuator/health:模型训练服务"
        "http://localhost:8083/health:模型推理服务"
        "http://localhost:9090:Prometheus监控"
        "http://localhost:3000:Grafana仪表板"
        "http://localhost:9000:MinIO控制台"
    )
    
    for endpoint_info in "${endpoints[@]}"; do
        local url=$(echo "$endpoint_info" | cut -d':' -f1-2)
        local name=$(echo "$endpoint_info" | cut -d':' -f3-)
        
        print_info "检查 $name: $url"
        
        if curl -f -s --connect-timeout 5 "$url" > /dev/null; then
            print_success "$name 可访问"
        else
            print_warning "$name 暂时不可访问"
        fi
    done
}

# 显示部署信息
show_deployment_info() {
    print_header "部署信息"
    
    echo
    print_info "🌐 服务访问地址:"
    echo "  📊 Grafana仪表板:     http://localhost:3000 (admin/admin123)"
    echo "  📈 Prometheus监控:    http://localhost:9090"
    echo "  💾 MinIO控制台:       http://localhost:9000 (minioadmin/minioadmin123)"
    echo "  🔍 数据采集API:       http://localhost:8081"
    echo "  🔧 数据预处理API:     http://localhost:8082"
    echo "  🤖 模型训练API:       http://localhost:8084"
    echo "  🧠 模型推理API:       http://localhost:8083"
    echo
    
    print_info "📁 重要目录:"
    echo "  📋 日志目录:          $LOG_DIR"
    echo "  💿 数据目录:          $DATA_DIR"
    echo "  🤖 模型目录:          $PROJECT_ROOT/models"
    echo "  💾 检查点目录:        $PROJECT_ROOT/checkpoints"
    echo
    
    print_info "🛠️  管理命令:"
    echo "  查看所有服务状态:     docker-compose -f $COMPOSE_FILE ps"
    echo "  查看服务日志:         docker-compose -f $COMPOSE_FILE logs -f [服务名]"
    echo "  重启服务:             docker-compose -f $COMPOSE_FILE restart [服务名]"
    echo "  停止所有服务:         docker-compose -f $COMPOSE_FILE down"
    echo "  查看资源使用:         docker stats"
    echo
    
    print_info "🔧 调试脚本:"
    echo "  实时监控:             $SCRIPT_DIR/monitor-services.sh"
    echo "  日志查看:             $SCRIPT_DIR/view-logs.sh"
    echo "  健康检查:             $SCRIPT_DIR/health-check.sh"
    echo
}

# 主函数
main() {
    print_header "AI Demo 生产环境部署"
    
    # 检查依赖
    check_dependencies
    check_docker_service
    
    # 准备环境
    create_directories
    check_config_files
    
    # 部署流程
    cleanup_old_deployment
    pull_images
    build_images
    
    # 分阶段启动服务
    start_infrastructure
    start_applications
    start_monitoring
    
    # 验证部署
    verify_services
    check_service_endpoints
    
    # 显示部署信息
    show_deployment_info
    
    print_success "🎉 AI Demo 生产环境部署完成！"
    print_info "💡 使用 '$SCRIPT_DIR/monitor-services.sh' 监控服务状态"
}

# 信号处理
trap 'print_error "部署被中断"; exit 1' INT TERM

# 执行主函数
main "$@"