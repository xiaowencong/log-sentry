import {useCallback, useEffect, useState} from 'react'
import {Card, Col, Empty, Progress, Row, Space, Statistic, Table, Tag, Typography} from 'antd'
import {
    AlertOutlined,
    ArrowUpOutlined,
    BugOutlined,
    CheckCircleOutlined,
    CloudServerOutlined,
    FileTextOutlined,
    SafetyOutlined,
    WarningOutlined,
} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import RiskBadge from '../../components/RiskBadge.jsx'
import {getDashboardStats, getRecentIssues} from '../../api/dashboard.js'
import {formatDateTime} from '../../utils/format.js'

const {Title, Text} = Typography

/** 风险级别分布迷你柱状图（纯 CSS） */
function RiskBar({total, critical, high, medium, low}) {
    if (!total) return <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
    const items = [
        {label: '严重', value: critical, color: '#ff4d4f', pct: Math.max(critical / total * 100, 2)},
        {label: '高危', value: high, color: '#fa8c16', pct: Math.max(high / total * 100, 2)},
        {label: '中等', value: medium, color: '#fadb14', pct: Math.max(medium / total * 100, 2)},
        {label: '低', value: low, color: '#1890ff', pct: Math.max(low / total * 100, 2)},
    ]
    return (
        <div style={{display: 'flex', alignItems: 'flex-end', gap: 16, height: 120, paddingTop: 8}}>
            {items.map((it) => (
                <div key={it.label} style={{flex: 1, textAlign: 'center'}}>
                    <div style={{fontSize: 12, marginBottom: 4, fontWeight: 600}}>{it.value}</div>
                    <div style={{
                        height: Math.max(it.pct * 0.9, 4),
                        background: it.color, borderRadius: '4px 4px 0 0',
                        transition: 'height 0.3s',
                    }}/>
                    <div style={{fontSize: 11, marginTop: 4, color: '#666'}}>{it.label}</div>
                </div>
            ))}
        </div>
    )
}

/** 服务分布 Top 5 */
function ServiceBreakdown({issues}) {
    const map = {}
    issues.forEach((i) => {
        const svc = i.serviceName || '未知服务'
        map[svc] = (map[svc] || 0) + 1
    })
    const sorted = Object.entries(map).sort((a, b) => b[1] - a[1])
    if (!sorted.length) return <Text type="secondary">暂无数据</Text>
    const maxVal = sorted[0][1]
    const colors = ['#ff4d4f', '#fa8c16', '#fadb14', '#1890ff', '#722ed1']
    return (
        <div>
            {sorted.map(([svc, cnt], idx) => (
                <div key={svc} style={{display: 'flex', alignItems: 'center', marginBottom: 8}}>
                    <Tag color={colors[idx] || 'default'}
                         style={{width: 100, textAlign: 'center', overflow: 'hidden', textOverflow: 'ellipsis'}}>
                        {svc}
                    </Tag>
                    <Progress
                        percent={Math.round(cnt / maxVal * 100)}
                        size="small"
                        strokeColor={colors[idx] || '#1890ff'}
                        style={{flex: 1, margin: 0}}
                        format={() => cnt}
                    />
                </div>
            ))}
        </div>
    )
}

export default function Dashboard() {
    const navigate = useNavigate()
    const [stats, setStats] = useState({critical: 0, high: 0, medium: 0, low: 0, total: 0})
    const [recentIssues, setRecentIssues] = useState([])
    const [loading, setLoading] = useState(false)

    const loadData = useCallback(async () => {
        setLoading(true)
        try {
            const [statsRes, recentRes] = await Promise.all([
                getDashboardStats(),
                getRecentIssues(20),
            ])
            if (statsRes?.code === 0) setStats(statsRes.data)
            if (recentRes?.code === 0) setRecentIssues(recentRes.data || [])
        } catch (e) {
            console.error('Dashboard load error:', e)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        loadData()
        const timer = setInterval(loadData, 30000)
        return () => clearInterval(timer)
    }, [loadData])

    const columns = [
        {title: '等级', dataIndex: 'riskLevel', render: (v) => <RiskBadge level={v}/>, width: 90},
        {
            title: '问题摘要', dataIndex: 'summary', ellipsis: true,
            render: (text, r) => <a onClick={() => navigate(`/issues/${r.id}`)} style={{cursor: 'pointer'}}>{text}</a>
        },
        {title: '来源服务', dataIndex: 'serviceName', width: 130, ellipsis: true},
        {title: '次数', dataIndex: 'occurrenceCount', width: 60},
        {title: '最近时间', dataIndex: 'lastSeen', width: 170, render: (v) => formatDateTime(v)},
    ]

    return (
        <div style={{padding: 24}}>
            <Row justify="space-between" align="middle" style={{marginBottom: 24}}>
                <Col>
                    <Title level={3} style={{margin: 0}}>总览面板</Title>
                    <Text type="secondary">实时监控系统日志风险，<ArrowUpOutlined style={{color: '#ff4d4f'}}/> 表示当前活跃问题</Text>
                </Col>
                <Col>
                    <Space>
                        <Tag color="green" style={{fontSize: 13, padding: '2px 12px'}}>
                            自动刷新 30s
                        </Tag>
                    </Space>
                </Col>
            </Row>

            {/* 统计卡片 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col xs={24} sm={12} lg={6}>
                    <Card hoverable onClick={() => navigate('/issues')}>
                        <Statistic
                            title="严重" value={stats.critical}
                            valueStyle={{color: '#cf1322'}}
                            prefix={<AlertOutlined/>}
                            suffix={<span style={{fontSize: 14}}>个</span>}/>
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card hoverable onClick={() => navigate('/issues')}>
                        <Statistic
                            title="高危" value={stats.high}
                            valueStyle={{color: '#d46b08'}}
                            prefix={<WarningOutlined/>}
                            suffix={<span style={{fontSize: 14}}>个</span>}/>
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card hoverable onClick={() => navigate('/issues')}>
                        <Statistic
                            title="中等" value={stats.medium}
                            valueStyle={{color: '#d48806'}}
                            prefix={<BugOutlined/>}
                            suffix={<span style={{fontSize: 14}}>个</span>}/>
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card hoverable onClick={() => navigate('/issues')}>
                        <Statistic
                            title="低" value={stats.low}
                            valueStyle={{color: '#1890ff'}}
                            prefix={<CheckCircleOutlined/>}
                            suffix={<span style={{fontSize: 14}}>个</span>}/>
                    </Card>
                </Col>
            </Row>

            {/* 分布图 + 服务分布 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col xs={24} lg={12}>
                    <Card title="风险等级分布" size="small">
                        <RiskBar {...stats} />
                    </Card>
                </Col>
                <Col xs={24} lg={12}>
                    <Card title={<><CloudServerOutlined/> 服务分布 Top 5</>} size="small">
                        <ServiceBreakdown issues={recentIssues}/>
                    </Card>
                </Col>
            </Row>

            {/* 最新问题 */}
            <Card
                title="最新问题"
                extra={<a onClick={() => navigate('/issues')}>查看全部 →</a>}
                style={{marginBottom: 24}}>
                <Table
                    columns={columns}
                    dataSource={recentIssues.slice(0, 10)}
                    rowKey="id"
                    size="small"
                    loading={loading}
                    pagination={false}
                />
            </Card>

            {/* 快速入口 */}
            <Card title="快速操作">
                <Row gutter={16}>
                    <Col span={8}>
                        <Card
                            size="small"
                            hoverable
                            onClick={() => navigate('/sources')}
                            style={{textAlign: 'center', borderColor: '#1890ff'}}>
                            <CloudServerOutlined style={{fontSize: 28, color: '#1890ff', marginBottom: 8}}/>
                            <div style={{fontWeight: 600}}>配置日志源</div>
                            <Text type="secondary" style={{fontSize: 12}}>添加日志文件路径，开始采集</Text>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card
                            size="small"
                            hoverable
                            onClick={() => navigate('/rules')}
                            style={{textAlign: 'center', borderColor: '#722ed1'}}>
                            <SafetyOutlined style={{fontSize: 28, color: '#722ed1', marginBottom: 8}}/>
                            <div style={{fontWeight: 600}}>管理规则</div>
                            <Text type="secondary" style={{fontSize: 12}}>自定义正则匹配规则及风险等级</Text>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card
                            size="small"
                            hoverable
                            onClick={() => navigate('/reports')}
                            style={{textAlign: 'center', borderColor: '#52c41a'}}>
                            <FileTextOutlined style={{fontSize: 28, color: '#52c41a', marginBottom: 8}}/>
                            <div style={{fontWeight: 600}}>生成报告</div>
                            <Text type="secondary" style={{fontSize: 12}}>一键生成日/周/月报</Text>
                        </Card>
                    </Col>
                </Row>
            </Card>
        </div>
    )
}
