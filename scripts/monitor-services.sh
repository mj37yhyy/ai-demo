#!/bin/bash

# AI Demo 服务监控脚本
# 提供实时监控、状态检查和性能分析功能

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

# 服务配置
SERVICES=(
    "mysql:3306:MySQL数据库"
    "redis:6379:Redis缓存"
    "kafka:9092:Kafka消息队列"
    "minio:9000:MinIO对象存储"
    "data-collector:8081:数据采集服务"
    "data-preprocessor:8082:数据预处理服务"
    "model-trainer:8084:模型训练服务"
    "model-inference:8083:模型推理服务"
    "prometheus:9090:Prometheus监控"
    "grafana:3000:Grafana仪表板"
    "nginx:80:Nginx反向代理"
)

# 健康检查端点
HEALTH_ENDPOINTS=(
    "http://localhost:8081/health:数据采集服务"
    "http://localhost:8082/actuator/health:数据预处理服务"
    "http://localhost:8084/actuator/health:模型训练服务"
    "http://localhost:8083/health:模型推理服务"
    "http://localhost:9090/-/healthy:Prometheus"
    "http://localhost:3000/api/health:Grafana"
)

# 打印带颜色的消息
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
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
    print_message "$PURPLE" "🔍 $1"
    echo "=================================================="
}

# 获取容器状态
get_container_status() {
    local service=$1
    local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    
    if [ -z "$container_id" ]; then
        echo "not-found"
        return
    fi
    
    local status=$(docker inspect --format='{{.State.Status}}' "$container_id" 2>/dev/null || echo "unknown")
    echo "$status"
}

# 获取容器健康状态
get_container_health() {
    local service=$1
    local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    
    if [ -z "$container_id" ]; then
        echo "no-container"
        return
    fi
    
    local health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container_id" 2>/dev/null || echo "unknown")
    echo "$health"
}

# 获取容器资源使用情况
get_container_resources() {
    local service=$1
    local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    
    if [ -z "$container_id" ]; then
        echo "N/A,N/A,N/A"
        return
    fi
    
    # 获取CPU和内存使用情况
    local stats=$(docker stats --no-stream --format "{{.CPUPerc}},{{.MemUsage}},{{.NetIO}}" "$container_id" 2>/dev/null || echo "N/A,N/A,N/A")
    echo "$stats"
}

# 检查端口连通性
check_port() {
    local host=$1
    local port=$2
    local timeout=3
    
    if command -v nc >/dev/null 2>&1; then
        nc -z -w$timeout "$host" "$port" 2>/dev/null
    elif command -v telnet >/dev/null 2>&1; then
        timeout $timeout telnet "$host" "$port" </dev/null >/dev/null 2>&1
    else
        # 使用curl作为备选
        curl -s --connect-timeout $timeout "http://$host:$port" >/dev/null 2>&1
    fi
}

# 检查HTTP端点
check_http_endpoint() {
    local url=$1
    local timeout=5
    
    if curl -f -s --connect-timeout $timeout --max-time $timeout "$url" >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 显示服务状态概览
show_services_overview() {
    print_header "服务状态概览"
    
    printf "%-20s %-12s %-15s %-12s %-15s %-20s\n" "服务名称" "容器状态" "健康状态" "端口状态" "CPU使用率" "内存使用"
    echo "--------------------------------------------------------------------------------------------------------"
    
    for service_info in "${SERVICES[@]}"; do
        local service=$(echo "$service_info" | cut -d':' -f1)
        local port=$(echo "$service_info" | cut -d':' -f2)
        local description=$(echo "$service_info" | cut -d':' -f3)
        
        # 获取状态信息
        local container_status=$(get_container_status "$service")
        local health_status=$(get_container_health "$service")
        local resources=$(get_container_resources "$service")
        
        # 解析资源信息
        local cpu_usage=$(echo "$resources" | cut -d',' -f1)
        local mem_usage=$(echo "$resources" | cut -d',' -f2 | cut -d'/' -f1)
        
        # 检查端口
        local port_status="❌ 关闭"
        if check_port "localhost" "$port"; then
            port_status="✅ 开放"
        fi
        
        # 设置状态颜色
        local status_color=""
        case "$container_status" in
            "running")
                status_color="$GREEN"
                ;;
            "exited"|"dead")
                status_color="$RED"
                ;;
            "restarting")
                status_color="$YELLOW"
                ;;
            *)
                status_color="$NC"
                ;;
        esac
        
        local health_color=""
        case "$health_status" in
            "healthy")
                health_color="$GREEN"
                ;;
            "unhealthy")
                health_color="$RED"
                ;;
            "starting")
                health_color="$YELLOW"
                ;;
            *)
                health_color="$NC"
                ;;
        esac
        
        printf "%-20s ${status_color}%-12s${NC} ${health_color}%-15s${NC} %-12s %-15s %-20s\n" \
            "$service" "$container_status" "$health_status" "$port_status" "$cpu_usage" "$mem_usage"
    done
    
    echo
}

# 显示健康检查详情
show_health_details() {
    print_header "健康检查详情"
    
    for endpoint_info in "${HEALTH_ENDPOINTS[@]}"; do
        local url=$(echo "$endpoint_info" | cut -d':' -f1-2)
        local name=$(echo "$endpoint_info" | cut -d':' -f3-)
        
        printf "%-25s " "$name:"
        
        if check_http_endpoint "$url"; then
            print_success "健康"
        else
            print_error "异常"
        fi
    done
    
    echo
}

# 显示系统资源使用情况
show_system_resources() {
    print_header "系统资源使用情况"
    
    # Docker系统信息
    print_info "Docker 系统信息:"
    docker system df 2>/dev/null || print_warning "无法获取Docker系统信息"
    
    echo
    
    # 容器资源统计
    print_info "容器资源统计:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" 2>/dev/null || print_warning "无法获取容器资源统计"
    
    echo
}

# 显示网络连接状态
show_network_status() {
    print_header "网络连接状态"
    
    # 检查Docker网络
    print_info "Docker 网络:"
    docker network ls | grep ai-demo || print_warning "未找到AI Demo网络"
    
    echo
    
    # 检查端口监听
    print_info "端口监听状态:"
    local ports=("3306" "6379" "9092" "9000" "8081" "8082" "8083" "8084" "9090" "3000" "80")
    
    for port in "${ports[@]}"; do
        if netstat -ln 2>/dev/null | grep ":$port " >/dev/null; then
            print_success "端口 $port: 监听中"
        else
            print_warning "端口 $port: 未监听"
        fi
    done
    
    echo
}

# 显示日志摘要
show_logs_summary() {
    print_header "最近日志摘要"
    
    local services_to_check=("data-collector" "data-preprocessor" "model-trainer" "model-inference")
    
    for service in "${services_to_check[@]}"; do
        print_info "服务 $service 最近日志:"
        docker-compose -f "$COMPOSE_FILE" logs --tail=3 "$service" 2>/dev/null | sed 's/^/  /' || print_warning "无法获取 $service 日志"
        echo
    done
}

# 显示Kafka主题信息
show_kafka_topics() {
    print_header "Kafka 主题信息"
    
    local kafka_container=$(docker-compose -f "$COMPOSE_FILE" ps -q kafka 2>/dev/null)
    
    if [ -n "$kafka_container" ]; then
        print_info "Kafka 主题列表:"
        docker exec "$kafka_container" kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | sed 's/^/  /' || print_warning "无法获取Kafka主题信息"
        echo
        
        print_info "主题详细信息:"
        local topics=("zhihu-raw-data" "zhihu-processed-data" "chatglm-requests" "chatglm-responses")
        for topic in "${topics[@]}"; do
            echo "  主题: $topic"
            docker exec "$kafka_container" kafka-topics --bootstrap-server localhost:9092 --describe --topic "$topic" 2>/dev/null | sed 's/^/    /' || echo "    主题不存在或无法访问"
        done
    else
        print_warning "Kafka 容器未运行"
    fi
    
    echo
}

# 显示数据库连接状态
show_database_status() {
    print_header "数据库连接状态"
    
    local mysql_container=$(docker-compose -f "$COMPOSE_FILE" ps -q mysql 2>/dev/null)
    
    if [ -n "$mysql_container" ]; then
        print_info "MySQL 连接测试:"
        if docker exec "$mysql_container" mysql -u ai_user -pai_pass123 -e "SELECT 1;" ai_demo >/dev/null 2>&1; then
            print_success "MySQL 连接正常"
        else
            print_error "MySQL 连接失败"
        fi
        
        print_info "数据库大小:"
        docker exec "$mysql_container" mysql -u ai_user -pai_pass123 -e "
            SELECT 
                table_schema AS 'Database',
                ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS 'Size (MB)'
            FROM information_schema.tables 
            WHERE table_schema = 'ai_demo'
            GROUP BY table_schema;
        " 2>/dev/null | sed 's/^/  /' || print_warning "无法获取数据库大小信息"
    else
        print_warning "MySQL 容器未运行"
    fi
    
    echo
}

# 实时监控模式
real_time_monitor() {
    print_header "实时监控模式 (按 Ctrl+C 退出)"
    
    while true; do
        clear
        echo "AI Demo 实时监控 - $(date '+%Y-%m-%d %H:%M:%S')"
        echo "========================================================"
        
        show_services_overview
        show_health_details
        
        print_info "⏱️  5秒后刷新... (按 Ctrl+C 退出)"
        sleep 5
    done
}

# 显示帮助信息
show_help() {
    echo "AI Demo 服务监控脚本"
    echo
    echo "用法: $0 [选项]"
    echo
    echo "选项:"
    echo "  -h, --help          显示帮助信息"
    echo "  -s, --status        显示服务状态概览"
    echo "  -r, --resources     显示系统资源使用情况"
    echo "  -n, --network       显示网络连接状态"
    echo "  -l, --logs          显示日志摘要"
    echo "  -k, --kafka         显示Kafka主题信息"
    echo "  -d, --database      显示数据库状态"
    echo "  -a, --all           显示所有信息"
    echo "  -w, --watch         实时监控模式"
    echo
    echo "示例:"
    echo "  $0 -s               # 显示服务状态"
    echo "  $0 -a               # 显示所有信息"
    echo "  $0 -w               # 启动实时监控"
}

# 主函数
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            ;;
        -s|--status)
            show_services_overview
            show_health_details
            ;;
        -r|--resources)
            show_system_resources
            ;;
        -n|--network)
            show_network_status
            ;;
        -l|--logs)
            show_logs_summary
            ;;
        -k|--kafka)
            show_kafka_topics
            ;;
        -d|--database)
            show_database_status
            ;;
        -a|--all)
            show_services_overview
            show_health_details
            show_system_resources
            show_network_status
            show_kafka_topics
            show_database_status
            show_logs_summary
            ;;
        -w|--watch)
            real_time_monitor
            ;;
        "")
            # 默认显示概览
            show_services_overview
            show_health_details
            ;;
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 信号处理
trap 'echo; print_info "监控已停止"; exit 0' INT TERM

# 执行主函数
main "$@"