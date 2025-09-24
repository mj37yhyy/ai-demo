#!/bin/bash

# AI Demo 服务调试脚本
# 提供全面的故障诊断、性能分析和问题排查功能

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
declare -A SERVICE_PORTS=(
    ["mysql"]="3306"
    ["redis"]="6379"
    ["kafka"]="9092"
    ["minio"]="9000"
    ["data-collector"]="8081"
    ["data-preprocessor"]="8082"
    ["model-trainer"]="9082"
    ["model-inference"]="9083"
    ["prometheus"]="9090"
    ["grafana"]="3000"
    ["nginx"]="80"
)

declare -A SERVICE_HEALTH_ENDPOINTS=(
    ["data-collector"]="http://localhost:8081/health"
    ["data-preprocessor"]="http://localhost:8082/health"
    ["model-trainer"]="http://localhost:9082/health"
    ["model-inference"]="http://localhost:9083/health"
    ["prometheus"]="http://localhost:9090/-/healthy"
    ["grafana"]="http://localhost:3000/api/health"
    ["nginx"]="http://localhost:80/health"
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

# 检查Docker环境
check_docker_environment() {
    print_header "Docker环境检查"
    
    # 检查Docker是否安装
    if ! command -v docker &> /dev/null; then
        print_error "Docker未安装"
        return 1
    fi
    print_success "Docker已安装: $(docker --version)"
    
    # 检查Docker Compose是否安装
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose未安装"
        return 1
    fi
    print_success "Docker Compose已安装: $(docker-compose --version)"
    
    # 检查Docker服务状态
    if ! docker info &> /dev/null; then
        print_error "Docker服务未运行"
        return 1
    fi
    print_success "Docker服务正常运行"
    
    # 显示Docker系统信息
    print_info "Docker系统信息:"
    docker system df | sed 's/^/  /'
    
    echo
    print_info "Docker网络列表:"
    docker network ls | sed 's/^/  /'
    
    return 0
}

# 检查服务容器状态
check_container_status() {
    print_header "容器状态检查"
    
    local all_healthy=true
    
    for service in "${!SERVICE_PORTS[@]}"; do
        local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
        
        if [ -z "$container_id" ]; then
            print_error "服务 '$service' 容器不存在"
            all_healthy=false
            continue
        fi
        
        local status=$(docker inspect --format='{{.State.Status}}' "$container_id" 2>/dev/null)
        local health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container_id" 2>/dev/null)
        local restart_count=$(docker inspect --format='{{.RestartCount}}' "$container_id" 2>/dev/null)
        local started_at=$(docker inspect --format='{{.State.StartedAt}}' "$container_id" 2>/dev/null)
        
        case "$status" in
            "running")
                if [ "$health" = "healthy" ] || [ "$health" = "no-healthcheck" ]; then
                    print_success "服务 '$service' 运行正常 (重启次数: $restart_count)"
                else
                    print_warning "服务 '$service' 运行中但健康检查失败 (状态: $health)"
                    all_healthy=false
                fi
                ;;
            "restarting")
                print_warning "服务 '$service' 正在重启 (重启次数: $restart_count)"
                all_healthy=false
                ;;
            "exited")
                local exit_code=$(docker inspect --format='{{.State.ExitCode}}' "$container_id" 2>/dev/null)
                print_error "服务 '$service' 已退出 (退出码: $exit_code, 重启次数: $restart_count)"
                all_healthy=false
                ;;
            *)
                print_error "服务 '$service' 状态异常: $status"
                all_healthy=false
                ;;
        esac
        
        print_info "  启动时间: $started_at"
    done
    
    return $all_healthy
}

# 检查端口连通性
check_port_connectivity() {
    print_header "端口连通性检查"
    
    local all_ports_ok=true
    
    for service in "${!SERVICE_PORTS[@]}"; do
        local port="${SERVICE_PORTS[$service]}"
        
        if nc -z localhost "$port" 2>/dev/null; then
            print_success "服务 '$service' 端口 $port 可访问"
        else
            print_error "服务 '$service' 端口 $port 不可访问"
            all_ports_ok=false
            
            # 检查端口是否被其他进程占用
            local process=$(lsof -ti:$port 2>/dev/null || echo "")
            if [ -n "$process" ]; then
                print_info "  端口 $port 被进程 $process 占用"
            fi
        fi
    done
    
    return $all_ports_ok
}

# 检查健康检查端点
check_health_endpoints() {
    print_header "健康检查端点测试"
    
    local all_endpoints_ok=true
    
    for service in "${!SERVICE_HEALTH_ENDPOINTS[@]}"; do
        local endpoint="${SERVICE_HEALTH_ENDPOINTS[$service]}"
        
        print_info "测试服务 '$service' 健康检查: $endpoint"
        
        local response=$(curl -s -w "%{http_code}" -o /tmp/health_response "$endpoint" 2>/dev/null || echo "000")
        
        if [ "$response" = "200" ]; then
            print_success "  健康检查通过"
            local content=$(cat /tmp/health_response 2>/dev/null || echo "")
            if [ -n "$content" ]; then
                echo "  响应内容: $content" | head -c 100
                echo
            fi
        else
            print_error "  健康检查失败 (HTTP状态码: $response)"
            all_endpoints_ok=false
        fi
    done
    
    rm -f /tmp/health_response
    return $all_endpoints_ok
}

# 检查资源使用情况
check_resource_usage() {
    print_header "资源使用情况检查"
    
    # 系统资源
    print_info "系统资源使用情况:"
    echo "  CPU使用率: $(top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//')"
    echo "  内存使用: $(vm_stat | grep "Pages free" | awk '{print $3}' | sed 's/\.//')"
    echo "  磁盘使用: $(df -h / | tail -1 | awk '{print $5}')"
    
    echo
    print_info "Docker容器资源使用:"
    docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" | sed 's/^/  /'
    
    echo
    print_info "Docker镜像占用空间:"
    docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | head -10 | sed 's/^/  /'
    
    # 检查磁盘空间警告
    local disk_usage=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')
    if [ "$disk_usage" -gt 80 ]; then
        print_warning "磁盘使用率过高: ${disk_usage}%"
    fi
}

# 检查网络连接
check_network_connectivity() {
    print_header "网络连接检查"
    
    # 检查Docker网络
    print_info "Docker网络状态:"
    local network_name="ai-demo_default"
    if docker network inspect "$network_name" &>/dev/null; then
        print_success "Docker网络 '$network_name' 存在"
        docker network inspect "$network_name" --format '{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}' | sed 's/^/  /'
    else
        print_error "Docker网络 '$network_name' 不存在"
    fi
    
    echo
    print_info "容器间网络连通性测试:"
    
    # 测试关键服务间的连接
    local test_connections=(
        "data-collector:mysql:3306"
        "data-collector:redis:6379"
        "data-collector:kafka:9092"
        "data-preprocessor:mysql:3306"
        "data-preprocessor:kafka:9092"
        "model-trainer:mysql:3306"
        "model-trainer:minio:9000"
        "model-inference:mysql:3306"
        "model-inference:redis:6379"
    )
    
    for connection in "${test_connections[@]}"; do
        local from_service=$(echo "$connection" | cut -d':' -f1)
        local to_service=$(echo "$connection" | cut -d':' -f2)
        local port=$(echo "$connection" | cut -d':' -f3)
        
        local from_container=$(docker-compose -f "$COMPOSE_FILE" ps -q "$from_service" 2>/dev/null)
        
        if [ -n "$from_container" ]; then
            if docker exec "$from_container" nc -z "$to_service" "$port" 2>/dev/null; then
                print_success "  $from_service -> $to_service:$port 连接正常"
            else
                print_error "  $from_service -> $to_service:$port 连接失败"
            fi
        else
            print_warning "  $from_service 容器不存在，跳过连接测试"
        fi
    done
}

# 检查日志中的错误
check_error_logs() {
    print_header "错误日志检查"
    
    local error_patterns=(
        "ERROR"
        "FATAL"
        "Exception"
        "Failed"
        "Timeout"
        "Connection refused"
        "Out of memory"
        "Permission denied"
    )
    
    print_info "检查最近的错误日志..."
    
    local found_errors=false
    
    for service in "${!SERVICE_PORTS[@]}"; do
        local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
        
        if [ -n "$container_id" ]; then
            local logs=$(docker logs --tail=100 "$container_id" 2>&1)
            
            for pattern in "${error_patterns[@]}"; do
                local error_count=$(echo "$logs" | grep -i "$pattern" | wc -l)
                
                if [ "$error_count" -gt 0 ]; then
                    found_errors=true
                    print_warning "服务 '$service' 发现 $error_count 个 '$pattern' 错误"
                    
                    # 显示最近的几个错误
                    echo "$logs" | grep -i "$pattern" | tail -3 | sed 's/^/    /'
                fi
            done
        fi
    done
    
    if [ "$found_errors" = false ]; then
        print_success "未发现明显的错误日志"
    fi
}

# 检查配置文件
check_configuration() {
    print_header "配置文件检查"
    
    local config_files=(
        "$COMPOSE_FILE"
        "$PROJECT_ROOT/go-services/data-collector/config/production.yaml"
        "$PROJECT_ROOT/go-services/data-preprocessor/config/production.yaml"
        "$PROJECT_ROOT/go-services/model-trainer/config/production.yaml"
        "$PROJECT_ROOT/go-services/model-inference/config/production.yaml"
    )
    
    for config_file in "${config_files[@]}"; do
        if [ -f "$config_file" ]; then
            print_success "配置文件存在: $(basename "$config_file")"
            
            # 检查文件权限
            local permissions=$(ls -l "$config_file" | awk '{print $1}')
            print_info "  权限: $permissions"
            
            # 检查文件大小
            local size=$(ls -lh "$config_file" | awk '{print $5}')
            print_info "  大小: $size"
            
        else
            print_error "配置文件缺失: $config_file"
        fi
    done
}

# 性能基准测试
run_performance_benchmark() {
    print_header "性能基准测试"
    
    print_info "执行API响应时间测试..."
    
    local endpoints=(
        "http://localhost:8081/health:数据采集服务"
        "http://localhost:8082/health:数据预处理服务"
        "http://localhost:9082/health:模型训练服务"
        "http://localhost:9083/health:模型推理服务"
    )
    
    for endpoint_info in "${endpoints[@]}"; do
        local endpoint=$(echo "$endpoint_info" | cut -d':' -f1)
        local description=$(echo "$endpoint_info" | cut -d':' -f2)
        
        print_info "测试 $description ($endpoint)"
        
        local response_time=$(curl -o /dev/null -s -w "%{time_total}" "$endpoint" 2>/dev/null || echo "timeout")
        
        if [ "$response_time" != "timeout" ]; then
            local time_ms=$(echo "$response_time * 1000" | bc -l | cut -d'.' -f1)
            
            if [ "$time_ms" -lt 100 ]; then
                print_success "  响应时间: ${time_ms}ms (优秀)"
            elif [ "$time_ms" -lt 500 ]; then
                print_warning "  响应时间: ${time_ms}ms (一般)"
            else
                print_error "  响应时间: ${time_ms}ms (较慢)"
            fi
        else
            print_error "  请求超时或失败"
        fi
    done
}

# 生成诊断报告
generate_diagnostic_report() {
    local report_file="$PROJECT_ROOT/logs/diagnostic_report_$(date +%Y%m%d_%H%M%S).txt"
    mkdir -p "$(dirname "$report_file")"
    
    print_header "生成诊断报告"
    
    {
        echo "AI Demo 系统诊断报告"
        echo "生成时间: $(date)"
        echo "========================================"
        echo
        
        echo "1. Docker环境检查"
        echo "----------------"
        check_docker_environment 2>&1
        echo
        
        echo "2. 容器状态检查"
        echo "----------------"
        check_container_status 2>&1
        echo
        
        echo "3. 端口连通性检查"
        echo "----------------"
        check_port_connectivity 2>&1
        echo
        
        echo "4. 健康检查端点测试"
        echo "----------------"
        check_health_endpoints 2>&1
        echo
        
        echo "5. 资源使用情况"
        echo "----------------"
        check_resource_usage 2>&1
        echo
        
        echo "6. 网络连接检查"
        echo "----------------"
        check_network_connectivity 2>&1
        echo
        
        echo "7. 错误日志检查"
        echo "----------------"
        check_error_logs 2>&1
        echo
        
        echo "8. 配置文件检查"
        echo "----------------"
        check_configuration 2>&1
        echo
        
        echo "9. 性能基准测试"
        echo "----------------"
        run_performance_benchmark 2>&1
        echo
        
    } > "$report_file"
    
    print_success "诊断报告已生成: $report_file"
    print_info "报告大小: $(du -h "$report_file" | cut -f1)"
}

# 快速修复常见问题
quick_fix() {
    print_header "快速修复常见问题"
    
    print_info "1. 重启失败的容器..."
    local failed_containers=$(docker-compose -f "$COMPOSE_FILE" ps --filter "status=exited" -q)
    
    if [ -n "$failed_containers" ]; then
        docker-compose -f "$COMPOSE_FILE" restart
        print_success "已重启失败的容器"
    else
        print_info "没有发现失败的容器"
    fi
    
    print_info "2. 清理未使用的Docker资源..."
    docker system prune -f > /dev/null 2>&1
    print_success "已清理未使用的Docker资源"
    
    print_info "3. 重新创建Docker网络..."
    local network_name="ai-demo_default"
    if docker network inspect "$network_name" &>/dev/null; then
        docker-compose -f "$COMPOSE_FILE" down
        docker network rm "$network_name" 2>/dev/null || true
        docker-compose -f "$COMPOSE_FILE" up -d
        print_success "已重新创建Docker网络"
    fi
    
    print_info "4. 检查并修复文件权限..."
    chmod +x "$PROJECT_ROOT/scripts/"*.sh
    print_success "已修复脚本文件权限"
}

# 交互式调试器
interactive_debugger() {
    while true; do
        clear
        echo "AI Demo 交互式调试器"
        echo "========================================"
        echo
        echo "调试选项:"
        echo "  1) Docker环境检查"
        echo "  2) 容器状态检查"
        echo "  3) 端口连通性检查"
        echo "  4) 健康检查端点测试"
        echo "  5) 资源使用情况检查"
        echo "  6) 网络连接检查"
        echo "  7) 错误日志检查"
        echo "  8) 配置文件检查"
        echo "  9) 性能基准测试"
        echo "  a) 全面诊断检查"
        echo "  r) 生成诊断报告"
        echo "  f) 快速修复"
        echo "  q) 退出"
        echo
        
        read -p "请选择调试选项: " choice
        
        case "$choice" in
            1) check_docker_environment; read -p "按回车键继续..." ;;
            2) check_container_status; read -p "按回车键继续..." ;;
            3) check_port_connectivity; read -p "按回车键继续..." ;;
            4) check_health_endpoints; read -p "按回车键继续..." ;;
            5) check_resource_usage; read -p "按回车键继续..." ;;
            6) check_network_connectivity; read -p "按回车键继续..." ;;
            7) check_error_logs; read -p "按回车键继续..." ;;
            8) check_configuration; read -p "按回车键继续..." ;;
            9) run_performance_benchmark; read -p "按回车键继续..." ;;
            a|A)
                check_docker_environment
                check_container_status
                check_port_connectivity
                check_health_endpoints
                check_resource_usage
                check_network_connectivity
                check_error_logs
                check_configuration
                run_performance_benchmark
                read -p "按回车键继续..."
                ;;
            r|R) generate_diagnostic_report; read -p "按回车键继续..." ;;
            f|F) quick_fix; read -p "按回车键继续..." ;;
            q|Q) print_info "退出调试器"; break ;;
            *) print_error "无效选择"; sleep 1 ;;
        esac
    done
}

# 显示帮助信息
show_help() {
    echo "AI Demo 服务调试脚本"
    echo
    echo "用法: $0 [选项]"
    echo
    echo "选项:"
    echo "  -h, --help          显示帮助信息"
    echo "  -d, --docker        检查Docker环境"
    echo "  -c, --containers    检查容器状态"
    echo "  -p, --ports         检查端口连通性"
    echo "  -e, --endpoints     检查健康检查端点"
    echo "  -r, --resources     检查资源使用情况"
    echo "  -n, --network       检查网络连接"
    echo "  -l, --logs          检查错误日志"
    echo "  -f, --config        检查配置文件"
    echo "  -b, --benchmark     运行性能基准测试"
    echo "  -a, --all           执行全面诊断检查"
    echo "  --report            生成诊断报告"
    echo "  --fix               快速修复常见问题"
    echo "  -i, --interactive   启动交互式调试器"
    echo
    echo "示例:"
    echo "  $0 -a               # 执行全面诊断检查"
    echo "  $0 --report         # 生成诊断报告"
    echo "  $0 -i               # 启动交互式调试器"
}

# 主函数
main() {
    case "${1:-}" in
        -h|--help) show_help ;;
        -d|--docker) check_docker_environment ;;
        -c|--containers) check_container_status ;;
        -p|--ports) check_port_connectivity ;;
        -e|--endpoints) check_health_endpoints ;;
        -r|--resources) check_resource_usage ;;
        -n|--network) check_network_connectivity ;;
        -l|--logs) check_error_logs ;;
        -f|--config) check_configuration ;;
        -b|--benchmark) run_performance_benchmark ;;
        -a|--all)
            check_docker_environment
            check_container_status
            check_port_connectivity
            check_health_endpoints
            check_resource_usage
            check_network_connectivity
            check_error_logs
            check_configuration
            run_performance_benchmark
            ;;
        --report) generate_diagnostic_report ;;
        --fix) quick_fix ;;
        -i|--interactive) interactive_debugger ;;
        "") interactive_debugger ;;
        *) print_error "未知选项: $1"; show_help; exit 1 ;;
    esac
}

# 信号处理
trap 'echo; print_info "调试已停止"; exit 0' INT TERM

# 执行主函数
main "$@"