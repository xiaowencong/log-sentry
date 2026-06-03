import {useEffect, useState} from 'react'
import {useParams} from 'react-router-dom'
import {Button, Card, Descriptions, List, message, Select, Space, Spin, Tag, Timeline, Typography} from 'antd'
import {ClockCircleOutlined} from '@ant-design/icons'
import RiskBadge from '../../components/RiskBadge.jsx'
import {getIssueDetail, getIssueLogs, updateIssueStatus} from '../../api/issue.js'
import {formatDateTime} from '../../utils/format.js'

const {Title, Text, Paragraph} = Typography

const statusMap = {
    OPEN: {color: 'red', text: '未处理'},
    ACKNOWLEDGED: {color: 'blue', text: '处理中'},
    RESOLVED: {color: 'green', text: '已解决'},
    CLOSED: {color: 'default', text: '已关闭'},
}

const aiStatusMap = {
    PENDING: {color: 'default', text: '等待分析'},
    ANALYZING: {color: 'processing', text: '分析中...'},
    COMPLETED: {color: 'green', text: '已完成'},
    FAILED: {color: 'red', text: '失败'},
}

export default function IssueDetail() {
    const {id} = useParams()
    const [issue, setIssue] = useState(null)
    const [aiAnalysis, setAiAnalysis] = useState(null)
    const [logs, setLogs] = useState([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        loadDetail()
    }, [id])

    const loadDetail = async () => {
        setLoading(true)
        try {
            const res = await getIssueDetail(id)
            if (res?.code === 0) setIssue(res.data)

            // Also load AI analysis and logs
            try {
                const aiRes = await (await fetch(`/api/issues/${id}/ai-analysis`)).json()
                if (aiRes?.code === 0) setAiAnalysis(aiRes.data)
            } catch (_) {
            }

            try {
                const logRes = await getIssueLogs(id)
                if (logRes?.code === 0) setLogs(logRes.data || [])
            } catch (_) {
            }
        } catch (e) {
            console.error('IssueDetail load error:', e)
        } finally {
            setLoading(false)
        }
    }

    const handleStatusChange = async (newStatus) => {
        try {
            const res = await updateIssueStatus(id, newStatus)
            if (res?.code === 0) {
                setIssue(res.data)
                message.success('状态更新成功')
            }
        } catch (e) {
            message.error('状态更新失败')
        }
    }

    const parseSuggestions = (suggestionsStr) => {
        if (!suggestionsStr) return []
        try {
            const arr = JSON.parse(suggestionsStr)
            return Array.isArray(arr) ? arr : []
        } catch {
            return [suggestionsStr]
        }
    }

    if (loading) return <div style={{padding: 48, textAlign: 'center'}}><Spin size="large"/></div>
    if (!issue) return <div style={{padding: 24}}><Title level={4}>问题不存在</Title></div>

    return (
        <div style={{padding: 24}}>
            <Title level={3}>问题详情 #{id}</Title>

            <Card style={{marginBottom: 16}}>
                <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label="风险等级">
                        <RiskBadge level={issue.riskLevel}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="风险评分">{issue.riskScore}/100</Descriptions.Item>
                    <Descriptions.Item label="状态">
                        <Space>
                            <Tag color={statusMap[issue.status]?.color}>{statusMap[issue.status]?.text}</Tag>
                            <Select
                                size="small"
                                style={{width: 100}}
                                value={issue.status}
                                onChange={handleStatusChange}>
                                <Select.Option value="OPEN">未处理</Select.Option>
                                <Select.Option value="ACKNOWLEDGED">处理中</Select.Option>
                                <Select.Option value="RESOLVED">已解决</Select.Option>
                                <Select.Option value="CLOSED">已关闭</Select.Option>
                            </Select>
                        </Space>
                    </Descriptions.Item>
                    <Descriptions.Item label="AI 分析">
                        <Tag color={aiStatusMap[issue.aiAnalysisStatus]?.color}>
                            {aiStatusMap[issue.aiAnalysisStatus]?.text || issue.aiAnalysisStatus}
                        </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="问题摘要" span={2}>
                        {issue.summary || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="分类">{issue.category || '-'}</Descriptions.Item>
                    <Descriptions.Item label="来源服务">{issue.serviceName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="来源路径">{issue.source || '-'}</Descriptions.Item>
                    <Descriptions.Item label="首次出现">{formatDateTime(issue.firstSeen)}</Descriptions.Item>
                    <Descriptions.Item label="最近出现">{formatDateTime(issue.lastSeen)}</Descriptions.Item>
                    <Descriptions.Item label="出现次数">{issue.occurrenceCount}</Descriptions.Item>
                </Descriptions>
            </Card>

            <Card
                title={
                    <Space>
                        <span>AI 分析结果</span>
                        <Button size="small" onClick={loadDetail}>刷新</Button>
                    </Space>
                }
                style={{marginBottom: 16}}>
                {aiAnalysis ? (
                    <>
                        <Descriptions column={1} size="small">
                            <Descriptions.Item label="AI 摘要">{aiAnalysis.summary || '-'}</Descriptions.Item>
                            <Descriptions.Item label="根因分析">
                                <Paragraph>{aiAnalysis.rootCause || '-'}</Paragraph>
                            </Descriptions.Item>
                            <Descriptions.Item label="报错代码位置">
                                <Text code style={{fontSize: 13}}>{aiAnalysis.errorLocation || '-'}</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="风险等级修正">
                                {aiAnalysis.riskLevelOverride ?
                                    <RiskBadge level={aiAnalysis.riskLevelOverride}/> : '无修正'}
                            </Descriptions.Item>
                            <Descriptions.Item label="是否需要立即处理">
                                <Tag color={aiAnalysis.needImmediateAction ? 'red' : 'green'}>
                                    {aiAnalysis.needImmediateAction ? '是' : '否'}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="相关知识">
                                {aiAnalysis.relatedKnowledge || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item
                                label="使用模型">{aiAnalysis.deepseekModelUsed || '-'}</Descriptions.Item>
                            <Descriptions.Item
                                label="API 耗时">{aiAnalysis.apiCostMs ? `${aiAnalysis.apiCostMs}ms` : '-'}</Descriptions.Item>
                        </Descriptions>
                        {parseSuggestions(aiAnalysis.suggestions).length > 0 && (
                            <Card title="处理建议" size="small" style={{marginTop: 12}}>
                                <List
                                    size="small"
                                    dataSource={parseSuggestions(aiAnalysis.suggestions)}
                                    renderItem={(item, idx) => (
                                        <List.Item>
                                            <Text strong>{idx + 1}.</Text> {item}
                                        </List.Item>
                                    )}
                                />
                            </Card>
                        )}
                    </>
                ) : (
                    <Text type="secondary">
                        {issue.aiAnalysisStatus === 'PENDING' ? '等待 AI 分析中...（系统每30秒自动处理）' :
                            issue.aiAnalysisStatus === 'ANALYZING' ? 'AI 正在分析中...' :
                                '暂无 AI 分析结果'}
                    </Text>
                )}
            </Card>

            <Card title={`关联日志 (${logs.length})`}>
                {logs.length > 0 ? (
                    <Timeline
                        items={logs.map((log) => ({
                            color: log.level === 'ERROR' ? 'red' : 'orange',
                            dot: <ClockCircleOutlined/>,
                            children: (
                                <div>
                                    <Text type="secondary" style={{fontSize: 12}}>{formatDateTime(log.timestamp)}</Text>
                                    <br/>
                                    <Text
                                        style={{
                                            fontFamily: 'monospace',
                                            fontSize: 13,
                                            color: log.level === 'ERROR' ? '#cf1322' : '#d48806',
                                        }}>
                                        [{log.level}] {log.message}
                                    </Text>
                                </div>
                            ),
                        }))}
                    />
                ) : (
                    <Text type="secondary">暂无关联日志</Text>
                )}
            </Card>
        </div>
    )
}
