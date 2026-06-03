/**
 * 格式化日期字符串，将 ISO 格式 (2026-06-03T10:52:59) 转为可读格式 (2026-06-03 10:52:59)
 * @param {string} dateStr - 日期字符串（支持 ISO 8601 或后端返回的时间格式）
 * @returns {string} 格式化后的日期字符串，无效输入返回 '-'
 */
export function formatDateTime(dateStr) {
    if (!dateStr) return '-'
    // 替换 T 为空格，截取到秒（去掉毫秒和时区部分）
    return dateStr.replace('T', ' ').substring(0, 19)
}
