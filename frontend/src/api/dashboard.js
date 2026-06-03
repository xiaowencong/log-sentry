import api from './index'

// 大盘统计数据
export const getDashboardStats = () => api.get('/dashboard/stats')

// 最近高风险问题
export const getRecentIssues = (limit = 10) => api.get('/dashboard/recent', {params: {limit}})

// 风险趋势
export const getRiskTrend = (params) => api.get('/dashboard/trend', {params})
