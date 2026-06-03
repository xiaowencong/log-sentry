import {useEffect, useState} from 'react'
import {Button, Card, DatePicker, Input, message, Modal, Popconfirm, Select, Space, Table, Typography} from 'antd'
import {DeleteOutlined, DownloadOutlined, EyeOutlined, FilterOutlined, PlusOutlined} from '@ant-design/icons'
import {
  deleteReport,
  generateCustom,
  generateDaily,
  generateMonthly,
  generateWeekly,
  getDownloadUrl,
  getReports
} from '../../api/report.js'
import {formatDateTime} from '../../utils/format.js'

const {Title, Paragraph} = Typography
const {RangePicker} = DatePicker

export default function Reports() {
    const [reports, setReports] = useState([])
    const [loading, setLoading] = useState(false)
    const [viewContent, setViewContent] = useState(null)
    const [genType, setGenType] = useState('DAILY')
    const [customRange, setCustomRange] = useState(null)
    const [filters, setFilters] = useState({
        riskLevel: undefined,
        status: undefined,
        category: undefined,
        keyword: undefined,
    })

    useEffect(() => {
        loadReports()
    }, [])

    const loadReports = async () => {
        setLoading(true)
        try {
            const res = await getReports()
            if (res?.code === 0) setReports(res.data || [])
        } catch (e) {
            message.error('加载报告列表失败')
        } finally {
            setLoading(false)
        }
    }

    const buildFilterParams = () => {
        const params = {}
        if (filters.riskLevel) params.riskLevel = filters.riskLevel
        if (filters.status) params.status = filters.status
        if (filters.category) params.category = filters.category
        if (filters.keyword) params.keyword = filters.keyword
        return params
    }

    const handleGenerate = async () => {
        try {
            const filterParams = buildFilterParams()
            let res
            if (genType === 'DAILY') res = await generateDaily(filterParams)
            else if (genType === 'WEEKLY') res = await generateWeekly(filterParams)
            else if (genType === 'MONTHLY') res = await generateMonthly(filterParams)
            else if (genType === 'CUSTOM' && customRange) {
                res = await generateCustom(
                    customRange[0].format('YYYY-MM-DD HH:mm:ss'),
                    customRange[1].format('YYYY-MM-DD HH:mm:ss'),
                    filterParams
                )
            }
            if (res?.code === 0) {
                message.success('报告生成成功')
                loadReports()
            }
        } catch (e) {
            message.error('报告生成失败')
        }
    }

    const handleDelete = async (id) => {
        try {
            await deleteReport(id)
            message.success('删除成功')
            loadReports()
        } catch (e) {
            message.error('删除失败')
        }
    }

    const columns = [
        {title: '标题', dataIndex: 'title', ellipsis: true},
        {
            title: '类型', dataIndex: 'type', width: 90,
            render: (v) => {
                const map = {DAILY: '日报', WEEKLY: '周报', MONTHLY: '月报', CUSTOM: '自定义'}
                return map[v] || v
            }
        },
        {title: '总问题数', dataIndex: 'totalIssues', width: 90},
        {title: '严重', dataIndex: 'criticalCount', width: 60, render: (v) => <span style={{color: 'red'}}>{v}</span>},
        {title: '高危', dataIndex: 'highCount', width: 60, render: (v) => <span style={{color: 'orange'}}>{v}</span>},
        {title: '中等', dataIndex: 'mediumCount', width: 60},
        {title: '低', dataIndex: 'lowCount', width: 60},
        {
            title: '时间范围', width: 300,
            render: (_, r) => `${formatDateTime(r.startTime)} ~ ${formatDateTime(r.endTime)}`
        },
        {title: '创建时间', dataIndex: 'createTime', width: 170, render: (v) => formatDateTime(v)},
        {
            title: '操作', width: 200,
            render: (_, r) => (
                <Space>
                    <Button size="small" icon={<EyeOutlined/>} onClick={() => setViewContent(r)}>查看</Button>
                    <Button size="small" icon={<DownloadOutlined/>}
                            onClick={() => window.open(getDownloadUrl(r.id), '_blank')}>下载</Button>
                    <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id)}>
                        <Button size="small" danger icon={<DeleteOutlined/>}/>
                    </Popconfirm>
                </Space>
            )
        },
    ]

    return (
        <div style={{padding: 24}}>
            <Title level={3}>报表中心</Title>

            <Card style={{marginBottom: 16}}>
                <Space wrap>
                    <Select value={genType} onChange={setGenType} style={{width: 120}}>
                        <Select.Option value="DAILY">日报</Select.Option>
                        <Select.Option value="WEEKLY">周报</Select.Option>
                        <Select.Option value="MONTHLY">月报</Select.Option>
                        <Select.Option value="CUSTOM">自定义</Select.Option>
                    </Select>
                    {genType === 'CUSTOM' && (
                        <RangePicker showTime onChange={(dates) => setCustomRange(dates)}/>
                    )}
                    <FilterOutlined style={{color: '#999'}}/>
                    <Select
                        placeholder="风险等级"
                        allowClear
                        style={{width: 120}}
                        value={filters.riskLevel}
                        onChange={(v) => setFilters({...filters, riskLevel: v})}>
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
                        onChange={(v) => setFilters({...filters, status: v})}>
                        <Select.Option value="OPEN">未处理</Select.Option>
                        <Select.Option value="ACKNOWLEDGED">处理中</Select.Option>
                        <Select.Option value="RESOLVED">已解决</Select.Option>
                        <Select.Option value="CLOSED">已关闭</Select.Option>
                    </Select>
                    <Select
                        placeholder="分类"
                        allowClear
                        style={{width: 120}}
                        value={filters.category}
                        onChange={(v) => setFilters({...filters, category: v})}>
                        <Select.Option value="系统资源">系统资源</Select.Option>
                        <Select.Option value="网络通信">网络通信</Select.Option>
                        <Select.Option value="数据库">数据库</Select.Option>
                        <Select.Option value="代码缺陷">代码缺陷</Select.Option>
                        <Select.Option value="性能问题">性能问题</Select.Option>
                        <Select.Option value="服务异常">服务异常</Select.Option>
                        <Select.Option value="其他">其他</Select.Option>
                    </Select>
                    <Input
                        placeholder="搜索摘要/来源"
                        allowClear
                        style={{width: 180}}
                        value={filters.keyword}
                        onChange={(e) => setFilters({...filters, keyword: e.target.value || undefined})}
                    />
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleGenerate}>
                        生成报告
                    </Button>
                </Space>
            </Card>

            <Table
                columns={columns}
                dataSource={reports}
                rowKey="id"
                loading={loading}
                pagination={{pageSize: 10}}
            />

            <Modal
                title={viewContent?.title}
                open={!!viewContent}
                onCancel={() => setViewContent(null)}
                footer={[
                    <Button key="download" type="primary" icon={<DownloadOutlined/>}
                            onClick={() => window.open(getDownloadUrl(viewContent?.id), '_blank')}>下载 .md</Button>,
                    <Button key="close" onClick={() => setViewContent(null)}>关闭</Button>
                ]}
                width={900}>
                <div style={{
                    whiteSpace: 'pre-wrap',
                    fontFamily: 'Consolas, "Courier New", monospace',
                    fontSize: 13,
                    maxHeight: 600,
                    overflow: 'auto',
                    background: '#fafafa',
                    padding: 16,
                    borderRadius: 8,
                    lineHeight: 1.7
                }}>
                    {viewContent?.content || '无内容'}
                </div>
            </Modal>
        </div>
    )
}
