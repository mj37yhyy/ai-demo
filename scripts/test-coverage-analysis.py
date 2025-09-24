#!/usr/bin/env python3
"""
Java服务测试覆盖率分析工具
用于分析data-preprocessor和model-trainer的测试覆盖情况
"""

import json
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any

class TestCoverageAnalyzer:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.data_preprocessor_path = self.project_root / "java-services" / "data-preprocessor"
        self.model_trainer_path = self.project_root / "java-services" / "model-trainer"
        self.results_dir = self.project_root / "test-results"
        self.results_dir.mkdir(exist_ok=True)
        
    def analyze_service_coverage(self, service_path: Path, service_name: str) -> Dict[str, Any]:
        """分析单个服务的测试覆盖率"""
        print(f"🔍 分析 {service_name} 测试覆盖率...")
        
        coverage_data = {
            "service_name": service_name,
            "timestamp": datetime.now().isoformat(),
            "source_files": [],
            "test_files": [],
            "coverage_metrics": {},
            "missing_tests": [],
            "recommendations": []
        }
        
        # 分析源代码文件
        src_path = service_path / "src" / "main" / "java"
        if src_path.exists():
            coverage_data["source_files"] = self._find_java_files(src_path)
        
        # 分析测试文件
        test_path = service_path / "src" / "test" / "java"
        if test_path.exists():
            coverage_data["test_files"] = self._find_java_files(test_path)
        
        # 计算覆盖率指标
        coverage_data["coverage_metrics"] = self._calculate_coverage_metrics(
            coverage_data["source_files"], 
            coverage_data["test_files"]
        )
        
        # 找出缺失的测试
        coverage_data["missing_tests"] = self._find_missing_tests(
            coverage_data["source_files"], 
            coverage_data["test_files"]
        )
        
        # 生成建议
        coverage_data["recommendations"] = self._generate_recommendations(coverage_data)
        
        return coverage_data
    
    def _find_java_files(self, path: Path) -> List[Dict[str, Any]]:
        """查找Java文件并分析其结构"""
        java_files = []
        
        for java_file in path.rglob("*.java"):
            file_info = {
                "path": str(java_file.relative_to(path)),
                "full_path": str(java_file),
                "classes": [],
                "methods": [],
                "lines_of_code": 0
            }
            
            try:
                with open(java_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    file_info["lines_of_code"] = len([line for line in content.split('\n') if line.strip()])
                    file_info["classes"] = self._extract_classes(content)
                    file_info["methods"] = self._extract_methods(content)
            except Exception as e:
                print(f"⚠️ 无法读取文件 {java_file}: {e}")
            
            java_files.append(file_info)
        
        return java_files
    
    def _extract_classes(self, content: str) -> List[str]:
        """提取Java类名"""
        class_pattern = r'(?:public\s+)?(?:abstract\s+)?class\s+(\w+)'
        interface_pattern = r'(?:public\s+)?interface\s+(\w+)'
        
        classes = re.findall(class_pattern, content)
        interfaces = re.findall(interface_pattern, content)
        
        return classes + interfaces
    
    def _extract_methods(self, content: str) -> List[str]:
        """提取Java方法名"""
        method_pattern = r'(?:public|private|protected)\s+(?:static\s+)?(?:\w+\s+)*(\w+)\s*\([^)]*\)\s*\{'
        return re.findall(method_pattern, content)
    
    def _calculate_coverage_metrics(self, source_files: List[Dict], test_files: List[Dict]) -> Dict[str, Any]:
        """计算覆盖率指标"""
        total_source_files = len(source_files)
        total_test_files = len(test_files)
        
        total_source_classes = sum(len(f["classes"]) for f in source_files)
        total_source_methods = sum(len(f["methods"]) for f in source_files)
        total_source_loc = sum(f["lines_of_code"] for f in source_files)
        
        total_test_classes = sum(len(f["classes"]) for f in test_files)
        total_test_methods = sum(len(f["methods"]) for f in test_files)
        total_test_loc = sum(f["lines_of_code"] for f in test_files)
        
        # 简单的覆盖率估算（基于测试文件与源文件的比例）
        file_coverage = (total_test_files / total_source_files * 100) if total_source_files > 0 else 0
        class_coverage = (total_test_classes / total_source_classes * 100) if total_source_classes > 0 else 0
        
        return {
            "total_source_files": total_source_files,
            "total_test_files": total_test_files,
            "total_source_classes": total_source_classes,
            "total_test_classes": total_test_classes,
            "total_source_methods": total_source_methods,
            "total_test_methods": total_test_methods,
            "total_source_loc": total_source_loc,
            "total_test_loc": total_test_loc,
            "estimated_file_coverage": round(file_coverage, 2),
            "estimated_class_coverage": round(class_coverage, 2),
            "test_to_source_ratio": round(total_test_loc / total_source_loc * 100, 2) if total_source_loc > 0 else 0
        }
    
    def _find_missing_tests(self, source_files: List[Dict], test_files: List[Dict]) -> List[Dict[str, str]]:
        """查找缺失的测试文件"""
        missing_tests = []
        
        # 获取所有测试类名
        test_classes = set()
        for test_file in test_files:
            for class_name in test_file["classes"]:
                test_classes.add(class_name.replace("Test", "").replace("Tests", ""))
        
        # 检查源文件是否有对应的测试
        for source_file in source_files:
            for class_name in source_file["classes"]:
                if class_name not in test_classes:
                    missing_tests.append({
                        "source_class": class_name,
                        "source_file": source_file["path"],
                        "suggested_test_file": f"{class_name}Test.java"
                    })
        
        return missing_tests
    
    def _generate_recommendations(self, coverage_data: Dict[str, Any]) -> List[str]:
        """生成测试改进建议"""
        recommendations = []
        metrics = coverage_data["coverage_metrics"]
        
        if metrics["estimated_file_coverage"] < 50:
            recommendations.append("测试文件覆盖率较低，建议增加更多测试文件")
        
        if metrics["test_to_source_ratio"] < 30:
            recommendations.append("测试代码量相对较少，建议编写更全面的测试用例")
        
        if len(coverage_data["missing_tests"]) > 0:
            recommendations.append(f"发现 {len(coverage_data['missing_tests'])} 个类缺少测试，建议补充相应的测试类")
        
        if metrics["total_test_methods"] < metrics["total_source_methods"] * 0.5:
            recommendations.append("测试方法数量相对较少，建议增加更多的测试方法")
        
        return recommendations
    
    def run_gradle_test_report(self, service_path: Path) -> Dict[str, Any]:
        """运行Gradle测试报告"""
        print(f"🧪 运行Gradle测试报告: {service_path.name}")
        
        try:
            # 运行测试并生成报告
            result = subprocess.run(
                ["./gradlew", "test", "jacocoTestReport", "--no-daemon"],
                cwd=service_path,
                capture_output=True,
                text=True,
                timeout=300
            )
            
            return {
                "success": result.returncode == 0,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "return_code": result.returncode
            }
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "error": "测试执行超时",
                "return_code": -1
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "return_code": -1
            }
    
    def generate_comprehensive_report(self) -> Dict[str, Any]:
        """生成综合测试覆盖率报告"""
        print("📊 生成综合测试覆盖率报告...")
        
        report = {
            "timestamp": datetime.now().isoformat(),
            "project_root": str(self.project_root),
            "services": {}
        }
        
        # 分析data-preprocessor
        if self.data_preprocessor_path.exists():
            report["services"]["data-preprocessor"] = self.analyze_service_coverage(
                self.data_preprocessor_path, "data-preprocessor"
            )
            
            # 运行Gradle测试报告
            gradle_result = self.run_gradle_test_report(self.data_preprocessor_path)
            report["services"]["data-preprocessor"]["gradle_test_result"] = gradle_result
        
        # 分析model-trainer
        if self.model_trainer_path.exists():
            report["services"]["model-trainer"] = self.analyze_service_coverage(
                self.model_trainer_path, "model-trainer"
            )
            
            # 运行Gradle测试报告
            gradle_result = self.run_gradle_test_report(self.model_trainer_path)
            report["services"]["model-trainer"]["gradle_test_result"] = gradle_result
        
        # 生成总体统计
        report["overall_statistics"] = self._calculate_overall_statistics(report["services"])
        
        return report
    
    def _calculate_overall_statistics(self, services: Dict[str, Any]) -> Dict[str, Any]:
        """计算总体统计信息"""
        total_source_files = 0
        total_test_files = 0
        total_missing_tests = 0
        all_recommendations = []
        
        for service_data in services.values():
            if "coverage_metrics" in service_data:
                metrics = service_data["coverage_metrics"]
                total_source_files += metrics["total_source_files"]
                total_test_files += metrics["total_test_files"]
                total_missing_tests += len(service_data["missing_tests"])
                all_recommendations.extend(service_data["recommendations"])
        
        overall_file_coverage = (total_test_files / total_source_files * 100) if total_source_files > 0 else 0
        
        return {
            "total_services": len(services),
            "total_source_files": total_source_files,
            "total_test_files": total_test_files,
            "total_missing_tests": total_missing_tests,
            "overall_file_coverage": round(overall_file_coverage, 2),
            "unique_recommendations": list(set(all_recommendations))
        }
    
    def save_report(self, report: Dict[str, Any], filename: str = "test-coverage-report.json"):
        """保存报告到文件"""
        report_path = self.results_dir / filename
        
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)
        
        print(f"📄 报告已保存到: {report_path}")
        return report_path
    
    def print_summary(self, report: Dict[str, Any]):
        """打印报告摘要"""
        print("\n" + "="*60)
        print("📊 Java服务测试覆盖率分析报告")
        print("="*60)
        
        overall = report["overall_statistics"]
        print(f"📈 总体统计:")
        print(f"   - 服务数量: {overall['total_services']}")
        print(f"   - 源文件总数: {overall['total_source_files']}")
        print(f"   - 测试文件总数: {overall['total_test_files']}")
        print(f"   - 文件覆盖率: {overall['overall_file_coverage']:.2f}%")
        print(f"   - 缺失测试: {overall['total_missing_tests']} 个类")
        
        print(f"\n📋 各服务详情:")
        for service_name, service_data in report["services"].items():
            if "coverage_metrics" in service_data:
                metrics = service_data["coverage_metrics"]
                print(f"   🔹 {service_name}:")
                print(f"      - 源文件: {metrics['total_source_files']} 个")
                print(f"      - 测试文件: {metrics['total_test_files']} 个")
                print(f"      - 文件覆盖率: {metrics['estimated_file_coverage']:.2f}%")
                print(f"      - 测试代码比例: {metrics['test_to_source_ratio']:.2f}%")
                print(f"      - 缺失测试: {len(service_data['missing_tests'])} 个类")
        
        print(f"\n💡 改进建议:")
        for i, recommendation in enumerate(overall['unique_recommendations'], 1):
            print(f"   {i}. {recommendation}")
        
        print("\n" + "="*60)

def main():
    if len(sys.argv) > 1:
        project_root = sys.argv[1]
    else:
        project_root = "/Users/miaojia/AI-Demo"
    
    analyzer = TestCoverageAnalyzer(project_root)
    
    try:
        report = analyzer.generate_comprehensive_report()
        report_path = analyzer.save_report(report)
        analyzer.print_summary(report)
        
        print(f"\n✅ 分析完成！详细报告: {report_path}")
        
    except Exception as e:
        print(f"❌ 分析失败: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()