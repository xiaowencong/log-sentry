import {useCallback, useEffect, useState} from 'react'
import {Button, Col, DatePicker, Input, message, Popconfirm, Row, Select, Space, Table, Tag, Typography} from 'antd'
import {ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {useNavigate} from 'react-router-dom'
import RiskBadge from '../../components/RiskBadge.jsx'
import {getIssues, updateIssueStatus} from '../../api/issue.js'
import {formatDateTime} from '../../utils/format.js'

const {Title} = Typography
const {RangePicker} = DatePicker

const statusMap = {
    OPEN: {color: 'red', text: '未处理'},
    ACKNOWLEDGED: {color: 'blue', text: '处理中'},
    RESOLVED: {color: 'green', text: '已解决'},
    CLOSED: {color: 'default', text: '已关闭'},
}

const aiStatusMap = {
    PENDING: {color: 'default', text: '待分析'},
    ANALYZING: {color: 'processing', text: '分析中'},
    COMPLETED: {color: 'green', text: '已分析'},
    FAILED: {color: 'red', text: '失败'},
}

export default function IssueList() {
    const navigate = useNavigate()
    const [data, setData] = useState([])
    const [loading, setLoading] = useState(false)
    const [total, setTotal] = useState(0)
    const [selectedRowKeys, setSelectedRowKeys] = useState([])
    const [filters, setFilters] = useState({
        riskLevel: undefined,
        status: undefined,
        keyword: undefined,
        startTime: undefined,
        endTime: undefined,
        page: 1,
        size: 20,
    })

    const loadData = useCallback(async () => {
        setLoading(true)
        try {
            const params = {}
            if (filters.riskLevel) params.riskLevel = filters.riskLevel
            if (filters.status) params.status = filters.status
            if (filters.keyword) params.keyword = filters.keyword
            if (filters.startTime) params.startTime = filters.startTime
            if (filters.endTime) params.endTime = filters.endTime
            params.page = filters.page
            params.size = filters.size

            const res = await getIssues(params)
            if (res?.code === 0) {
                setData(res.data.items || [])
                setTotal(res.data.total || 0)
            }
        } catch (e) {
            console.error('IssueList load error:', e)
        } finally {
            setLoading(false)
        }
    }, [filters])

    useEffect(() => {
        loadData()
    }, [loadData])

    const handleBatchStatus = async (status) => {
        if (!selectedRowKeys.length) {
            message.warning('请先选择问题');
            return
        }
        try {
            await Promise.all(selectedRowKeys.map((id) => updateIssueStatus(id, status)))
            message.success(`已将 ${selectedRowKeys.length} 个问题标记为「${statusMap[status]?.text}」`)
            setSelectedRowKeys([])
            loadData()
        } catch (e) {
            message.error('批量操作失败')
        }
    }

    const rowSelection = {
        selectedRowKeys,
        onChange: setSelectedRowKeys,
        selections: [
            Table.SELECTION_ALL,
            Table.SELECTION_INVERT,
            Table.SELECTION_NONE,
        ],
    }

    const columns = [
        {title: '风险等级', dataIndex: 'riskLevel', render: (v) => <RiskBadge level={v}/>, width: 100},
        {
            title: '问题摘要', dataIndex: 'summary', ellipsis: true,
            render: (text, record) => <a onClick={() => navigate(`/issues/${record.id}`)}>{text}</a>
        },
        {title: '分类', dataIndex: 'category', width: 100},
        {title: '来源服务', dataIndex: 'serviceName', width: 140},
        {title: '出现次数', dataIndex: 'occurrenceCount', width: 90},
        {title: '首次出现', dataIndex: 'firstSeen', width: 170, render: (v) => formatDateTime(v)},
        {title: '最近出现', dataIndex: 'lastSeen', width: 170, render: (v) => formatDateTime(v)},
        {
            title: '状态', dataIndex: 'status', width: 90,
            render: (v) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>
        },
        {
            title: 'AI分析', dataIndex: 'aiAnalysisStatus', width: 100,
            render: (v) => <Tag color={aiStatusMap[v]?.color}>{aiStatusMap[v]?.text || v}</Tag>
        },
    ]

    return (
        <div style={{padding: 24}}>
            <Row justify="space-between" align="middle" style={{marginBottom: 16}}>
                <Col>
                    <Title level={3} style={{margin: 0}}>问题列表</Title>
                </Col>
                <Col>
                    <Button icon={<ReloadOutlined/>} onClick={loadData}>刷新</Button>
                </Col>
            </Row>

            {/* 筛选条 */}
            <Space style={{marginBottom: 8}} wrap>
                <Select
                    placeholder="风险等级"
                    allowClear
                    style={{width: 140}}
                    value={filters.riskLevel}
                    onChange={(v) => setFilters({...filters, riskLevel: v, page: 1})}>
                    <Select.Option value="CRITICAL">严重</Select.Option>
                    <Select.Option value="HIGH">高危</Select.Option>
                    <Select.Option value="MEDIUM">中等</Select.Option>
                    <Select.Option value="LOW">低</Select.Option>
                </Select>
                <Select
                    placeholder="状态"
                    allowClear
                    style={{width: 120}}
                    value={filters.status}
                    onChange={(v) => setFilters({...filters, status: v, page: 1})}>
                    <Select.Option value="OPEN">未处理</Select.Option>
                    <Select.Option value="ACKNOWLEDGED">处理中</Select.Option>
                    <Select.Option value="RESOLVED">已解决</Select.Option>
                    <Select.Option value="CLOSED">已关闭</Select.Option>
                </Select>
                <Input
                    placeholder="搜索摘要/来源"
                    allowClear
                    style={{width: 200}}
                    prefix={<SearchOutlined/>}
                    value={filters.keyword}
                    onChange={(e) => setFilters({...filters, keyword: e.target.value || undefined})}
                    onPressEnter={() => setFilters({...filters, page: 1})}
                />
                <RangePicker
                    onChange={(dates) => {
                        setFilters({
                            ...filters,
                            startTime: dates?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
                            endTime: dates?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
                            page: 1,
                        })
                    }}
                />
                <Button type="primary" icon={<SearchOutlined/>}
                        onClick={() => setFilters({...filters, page: 1})}>查询</Button>
            </Space>

            {/* 批量操作栏 */}
            {selectedRowKeys.length > 0 && (
                <div style={{
                    marginBottom: 8, padding: '8px 16px',
                    background: '#e6f7ff', borderRadius: 4,
                    display: 'flex', alignItems: 'center', gap: 12,
                }}>
                    <span>已选 <strong>{selectedRowKeys.length}</strong> 项</span>
                    <Popconfirm title={`批量标记为「已解决」？`} onConfirm={() => handleBatchStatus('RESOLVED')}>
                        <Button size="small" type="primary" ghost>标记已解决</Button>
                    </Popconfirm>
                    <Popconfirm title={`批量标记为「已关闭」？`} onConfirm={() => handleBatchStatus('CLOSED')}>
                        <Button size="small">标记已关闭</Button>
                    </Popconfirm>
                    <Button size="small" onClick={() => setSelectedRowKeys([])}>取消选择</Button>
                </div>
            )}

            <Table
                rowSelection={rowSelection}
                columns={columns}
                dataSource={data}
                rowKey="id"
                loading={loading}
                pagination={{
                    total, current: filters.page, pageSize: filters.size,
                    showSizeChanger: true, showTotal: (t) => `共 ${t} 条`,
                    onChange: (page, size) => setFilters({...filters, page, size})
                }}
            />
        </div>
    )
}
