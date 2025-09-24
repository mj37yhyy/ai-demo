package collector

import (
	"regexp"
	"strings"
	"unicode"
)

// containsChinese 检查文本是否包含中文字符
func containsChinese(text string) bool {
	for _, r := range text {
		if r >= 0x4e00 && r <= 0x9fff {
			return true
		}
	}
	return false
}

// isValidURL 检查是否为有效的URL
func isValidURL(url string) bool {
	return strings.HasPrefix(url, "http://") || strings.HasPrefix(url, "https://")
}

// isValidEmail 检查是否为有效的邮箱地址
func isValidEmail(email string) bool {
	emailRegex := regexp.MustCompile(`^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`)
	return emailRegex.MatchString(email)
}

// cleanText 清理文本内容
func cleanText(text string) string {
	// 去除多余的空白字符
	text = strings.TrimSpace(text)
	
	// 去除连续的空格
	spaceRegex := regexp.MustCompile(`\s+`)
	text = spaceRegex.ReplaceAllString(text, " ")
	
	return text
}

// isValidTextLength 检查文本长度是否有效
func isValidTextLength(text string, minLength, maxLength int) bool {
	length := len([]rune(text)) // 使用rune计算字符数，支持中文
	return length >= minLength && length <= maxLength
}

// containsOnlyWhitespace 检查文本是否只包含空白字符
func containsOnlyWhitespace(text string) bool {
	for _, r := range text {
		if !unicode.IsSpace(r) {
			return false
		}
	}
	return true
}