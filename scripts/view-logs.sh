#!/bin/bash

# AI Demo 日志查看脚本
# 提供实时日志监控、日志分析和错误诊断功能

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

# 服务列表
SERVICES=(
    "mysql:MySQL数据库"
    "redis:Redis缓存"
    "kafka:Kafka消息队列"
    "minio:MinIO对象存储"
    "data-collector:数据采集服务"
    "data-preprocessor:数据预处理服务"
    "model-trainer:模型训练服务"
    "model-inference:模型推理服务"
    "prometheus:Prometheus监控"
    "grafana:Grafana仪表板"
    "nginx:Nginx反向代理"
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
    print_message "$PURPLE" "📋 $1"
    echo "=================================================="
}

# 显示服务列表
show_services_list() {
    print_header "可用服务列表"
    
    local index=1
    for service_info in "${SERVICES[@]}"; do
        local service=$(echo "$service_info" | cut -d':' -f1)
        local description=$(echo "$service_info" | cut -d':' -f2)
        
        # 检查服务状态
        local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
        local status_icon="❌"
        
        if [ -n "$container_id" ]; then
            local status=$(docker inspect --format='{{.State.Status}}' "$container_id" 2>/dev/null || echo "unknown")
            if [ "$status" = "running" ]; then
                status_icon="✅"
            elif [ "$status" = "restarting" ]; then
                status_icon="🔄"
            fi
        fi
        
        printf "%2d. %s %-20s - %s\n" "$index" "$status_icon" "$service" "$description"
        ((index++))
    done
    
    echo
}

# 查看指定服务的日志
view_service_logs() {
    local service=$1
    local lines=${2:-100}
    local follow=${3:-false}
    
    # 验证服务是否存在
    local service_exists=false
    for service_info in "${SERVICES[@]}"; do
        local svc=$(echo "$service_info" | cut -d':' -f1)
        if [ "$svc" = "$service" ]; then
            service_exists=true
            break
        fi
    done
    
    if [ "$service_exists" = false ]; then
        print_error "服务 '$service' 不存在"
        show_services_list
        return 1
    fi
    
    # 检查容器是否运行
    local container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    if [ -z "$container_id" ]; then
        print_error "服务 '$service' 未运行"
        return 1
    fi
    
    print_header "服务 '$service' 日志"
    
    if [ "$follow" = true ]; then
        print_info "实时跟踪日志 (按 Ctrl+C 停止)..."
        docker-compose -f "$COMPOSE_FILE" logs -f --tail="$lines" "$service"
    else
        print_info "显示最近 $lines 行日志..."
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service"
    fi
}

# 查看所有服务的日志
view_all_logs() {
    local lines=${1:-50}
    local follow=${2:-false}
    
    print_header "所有服务日志"
    
    if [ "$follow" = true ]; then
        print_info "实时跟踪所有服务日志 (按 Ctrl+C 停止)..."
        docker-compose -f "$COMPOSE_FILE" logs -f --tail="$lines"
    else
        print_info "显示所有服务最近 $lines 行日志..."
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines"
    fi
}

# 搜索日志中的关键词
search_logs() {
    local service=$1
    local keyword=$2
    local lines=${3:-1000}
    
    if [ -z "$keyword" ]; then
        print_error "请提供搜索关键词"
        return 1
    fi
    
    print_header "在服务 '$service' 日志中搜索 '$keyword'"
    
    if [ "$service" = "all" ]; then
        print_info "搜索所有服务日志..."
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" | grep -i --color=always "$keyword" || print_warning "未找到匹配的日志"
    else
        # 验证服务是否存在
        local service_exists=false
        for service_info in "${SERVICES[@]}"; do
            local svc=$(echo "$service_info" | cut -d':' -f1)
            if [ "$svc" = "$service" ]; then
                service_exists=true
                break
            fi
        done
        
        if [ "$service_exists" = false ]; then
            print_error "服务 '$service' 不存在"
            return 1
        fi
        
        print_info "搜索服务 '$service' 日志..."
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service" | grep -i --color=always "$keyword" || print_warning "未找到匹配的日志"
    fi
}

# 分析错误日志
analyze_errors() {
    local service=${1:-"all"}
    local lines=${2:-1000}
    
    print_header "错误日志分析"
    
    local error_patterns=(
        "ERROR"
        "FATAL"
        "Exception"
        "Error"
        "Failed"
        "Timeout"
        "Connection refused"
        "Out of memory"
        "Disk full"
    )
    
    if [ "$service" = "all" ]; then
        print_info "分析所有服务的错误日志..."
        local logs=$(docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" 2>/dev/null)
    else
        print_info "分析服务 '$service' 的错误日志..."
        local logs=$(docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service" 2>/dev/null)
    fi
    
    local found_errors=false
    
    for pattern in "${error_patterns[@]}"; do
        local matches=$(echo "$logs" | grep -i "$pattern" | head -10)
        if [ -n "$matches" ]; then
            found_errors=true
            print_warning "发现 '$pattern' 相关错误:"
            echo "$matches" | sed 's/^/  /' | head -5
            echo
        fi
    done
    
    if [ "$found_errors" = false ]; then
        print_success "未发现明显的错误日志"
    fi
}

# 显示日志统计信息
show_log_stats() {
    local service=${1:-"all"}
    local lines=${2:-1000}
    
    print_header "日志统计信息"
    
    if [ "$service" = "all" ]; then
        local logs=$(docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" 2>/dev/null)
    else
        local logs=$(docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service" 2>/dev/null)
    fi
    
    if [ -z "$logs" ]; then
        print_warning "没有找到日志数据"
        return
    fi
    
    print_info "日志级别统计:"
    echo "$logs" | grep -i "INFO" | wc -l | xargs printf "  INFO:    %d 条\n"
    echo "$logs" | grep -i "WARN" | wc -l | xargs printf "  WARN:    %d 条\n"
    echo "$logs" | grep -i "ERROR" | wc -l | xargs printf "  ERROR:   %d 条\n"
    echo "$logs" | grep -i "DEBUG" | wc -l | xargs printf "  DEBUG:   %d 条\n"
    
    echo
    print_info "总日志行数: $(echo "$logs" | wc -l)"
    
    echo
    print_info "最近活跃的服务:"
    echo "$logs" | grep -o '^[^|]*' | sort | uniq -c | sort -nr | head -5 | sed 's/^/  /'
}

# 导出日志到文件
export_logs() {
    local service=${1:-"all"}
    local output_file=${2:-"logs_$(date +%Y%m%d_%H%M%S).txt"}
    local lines=${3:-10000}
    
    print_header "导出日志到文件"
    
    local output_path="$PROJECT_ROOT/logs/$output_file"
    mkdir -p "$(dirname "$output_path")"
    
    print_info "导出服务 '$service' 的日志到: $output_path"
    
    if [ "$service" = "all" ]; then
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" > "$output_path" 2>&1
    else
        docker-compose -f "$COMPOSE_FILE" logs --tail="$lines" "$service" > "$output_path" 2>&1
    fi
    
    if [ $? -eq 0 ]; then
        print_success "日志已导出到: $output_path"
        print_info "文件大小: $(du -h "$output_path" | cut -f1)"
    else
        print_error "日志导出失败"
    fi
}

# 清理旧日志
cleanup_logs() {
    print_header "清理容器日志"
    
    print_warning "这将清理所有Docker容器的日志，是否继续? (y/N)"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        print_info "清理容器日志..."
        
        # 获取所有容器ID
        local containers=$(docker-compose -f "$COMPOSE_FILE" ps -q 2>/dev/null)
        
        if [ -n "$containers" ]; then
            for container in $containers; do
                local log_file=$(docker inspect --format='{{.LogPath}}' "$container" 2>/dev/null)
                if [ -n "$log_file" ] && [ -f "$log_file" ]; then
                    echo -n > "$log_file" 2>/dev/null || true
                    print_info "已清理容器 $container 的日志"
                fi
            done
            print_success "日志清理完成"
        else
            print_warning "没有找到运行中的容器"
        fi
    else
        print_info "取消日志清理"
    fi
}

# 交互式日志查看器
interactive_viewer() {
    while true; do
        clear
        echo "AI Demo 交互式日志查看器"
        echo "========================================"
        show_services_list
        
        echo "操作选项:"
        echo "  1-${#SERVICES[@]}) 查看对应服务日志"
        echo "  a) 查看所有服务日志"
        echo "  f) 实时跟踪日志"
        echo "  s) 搜索日志"
        echo "  e) 分析错误日志"
        echo "  t) 显示日志统计"
        echo "  x) 导出日志"
        echo "  c) 清理日志"
        echo "  q) 退出"
        echo
        
        read -p "请选择操作: " choice
        
        case "$choice" in
            [1-9]|[1-9][0-9])
                if [ "$choice" -le "${#SERVICES[@]}" ]; then
                    local service=$(echo "${SERVICES[$((choice-1))]}" | cut -d':' -f1)
                    echo
                    read -p "显示行数 (默认100): " lines
                    lines=${lines:-100}
                    view_service_logs "$service" "$lines"
                    echo
                    read -p "按回车键继续..."
                else
                    print_error "无效选择"
                    sleep 1
                fi
                ;;
            a|A)
                echo
                read -p "显示行数 (默认50): " lines
                lines=${lines:-50}
                view_all_logs "$lines"
                echo
                read -p "按回车键继续..."
                ;;
            f|F)
                echo
                read -p "选择服务 (输入服务名或 'all'): " service
                service=${service:-all}
                read -p "显示行数 (默认100): " lines
                lines=${lines:-100}
                if [ "$service" = "all" ]; then
                    view_all_logs "$lines" true
                else
                    view_service_logs "$service" "$lines" true
                fi
                ;;
            s|S)
                echo
                read -p "选择服务 (输入服务名或 'all'): " service
                service=${service:-all}
                read -p "搜索关键词: " keyword
                if [ -n "$keyword" ]; then
                    search_logs "$service" "$keyword"
                    echo
                    read -p "按回车键继续..."
                fi
                ;;
            e|E)
                echo
                read -p "选择服务 (输入服务名或 'all'): " service
                service=${service:-all}
                analyze_errors "$service"
                echo
                read -p "按回车键继续..."
                ;;
            t|T)
                echo
                read -p "选择服务 (输入服务名或 'all'): " service
                service=${service:-all}
                show_log_stats "$service"
                echo
                read -p "按回车键继续..."
                ;;
            x|X)
                echo
                read -p "选择服务 (输入服务名或 'all'): " service
                service=${service:-all}
                read -p "输出文件名 (默认自动生成): " filename
                export_logs "$service" "$filename"
                echo
                read -p "按回车键继续..."
                ;;
            c|C)
                cleanup_logs
                echo
                read -p "按回车键继续..."
                ;;
            q|Q)
                print_info "退出日志查看器"
                break
                ;;
            *)
                print_error "无效选择"
                sleep 1
                ;;
        esac
    done
}

# 显示帮助信息
show_help() {
    echo "AI Demo 日志查看脚本"
    echo
    echo "用法: $0 [选项] [参数]"
    echo
    echo "选项:"
    echo "  -h, --help                    显示帮助信息"
    echo "  -l, --list                    显示服务列表"
    echo "  -v, --view <service> [lines]  查看指定服务日志"
    echo "  -f, --follow <service>        实时跟踪服务日志"
    echo "  -a, --all [lines]             查看所有服务日志"
    echo "  -s, --search <service> <keyword> 搜索日志关键词"
    echo "  -e, --errors [service]        分析错误日志"
    echo "  -t, --stats [service]         显示日志统计"
    echo "  -x, --export <service> [file] 导出日志到文件"
    echo "  -c, --cleanup                 清理容器日志"
    echo "  -i, --interactive             交互式日志查看器"
    echo
    echo "示例:"
    echo "  $0 -v data-collector 200      # 查看数据采集服务最近200行日志"
    echo "  $0 -f model-trainer           # 实时跟踪模型训练服务日志"
    echo "  $0 -s all \"error\"             # 在所有服务日志中搜索error"
    echo "  $0 -e data-preprocessor       # 分析数据预处理服务的错误日志"
    echo "  $0 -i                         # 启动交互式日志查看器"
}

# 主函数
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            ;;
        -l|--list)
            show_services_list
            ;;
        -v|--view)
            if [ -z "$2" ]; then
                print_error "请指定服务名"
                show_services_list
                exit 1
            fi
            view_service_logs "$2" "${3:-100}"
            ;;
        -f|--follow)
            if [ -z "$2" ]; then
                print_error "请指定服务名"
                show_services_list
                exit 1
            fi
            view_service_logs "$2" "100" true
            ;;
        -a|--all)
            view_all_logs "${2:-50}"
            ;;
        -s|--search)
            if [ -z "$2" ] || [ -z "$3" ]; then
                print_error "请指定服务名和搜索关键词"
                exit 1
            fi
            search_logs "$2" "$3"
            ;;
        -e|--errors)
            analyze_errors "${2:-all}"
            ;;
        -t|--stats)
            show_log_stats "${2:-all}"
            ;;
        -x|--export)
            if [ -z "$2" ]; then
                print_error "请指定服务名"
                exit 1
            fi
            export_logs "$2" "$3"
            ;;
        -c|--cleanup)
            cleanup_logs
            ;;
        -i|--interactive)
            interactive_viewer
            ;;
        "")
            # 默认启动交互式查看器
            interactive_viewer
            ;;
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 信号处理
trap 'echo; print_info "日志查看已停止"; exit 0' INT TERM

# 执行主函数
main "$@"