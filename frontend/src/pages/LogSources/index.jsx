import {useEffect, useRef, useState} from 'react'
import {
  Alert,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Progress,
  Result,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography
} from 'antd'
import {
  CloudServerOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ScanOutlined
} from '@ant-design/icons'
import {
  createSource,
  deleteSource,
  getScanProgress,
  getSources,
  toggleSource,
  triggerFullScan,
  updateSource
} from '../../api/source.js'
import FilePicker from '../../components/FilePicker.jsx'
import {formatDateTime} from '../../utils/format.js'

const {Title, Text, Paragraph} = Typography

export default function LogSources() {
    const [sources, setSources] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [filePickerOpen, setFilePickerOpen] = useState(false)
    const [progressMap, setProgressMap] = useState({})
    const [form] = Form.useForm()
    const pollTimer = useRef(null)

    const loadSources = async () => {
        setLoading(true)
        try {
            const res = await getSources()
            if (res?.code === 0) setSources(res.data || [])
        } catch {
            message.error('加载日志源失败')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadSources()
    }, [])

    // 定时轮询各日志源的扫描进度（使用递归 setTimeout 防止并发竞争）
    useEffect(() => {
        let isMounted = true

        const poll = async () => {
            if (!isMounted) return
            const map = {}
            for (const s of sources) {
                try {
                    const res = await getScanProgress(s.id)
                    if (res?.data) map[s.id] = res.data
                } catch { /* 忽略单源拉取失败 */
                }
            }
            if (!isMounted) return

            setProgressMap(prev => {
                const merged = {...map}
                // 如果前一次是 SCANNING，而这次直接变成 TAILING，说明全量扫描完成后
                // 后端立即进入监听模式。此时前端短暂展示 COMPLETED 状态让用户看到完成提示
                for (const [id, prevProgress] of Object.entries(prev)) {
                    if (prevProgress?.status === 'SCANNING' && merged[id]?.status === 'TAILING') {
                        merged[id] = {...merged[id], status: 'COMPLETED'}
                    }
                }
                return merged
            })

            // 根据是否有扫描中的源动态调整下次轮询延迟（放在 state updater 外面）
            const hasScanning = Object.values(map).some(p => p?.status === 'SCANNING')
            const delay = hasScanning ? 1000 : 3000
            if (isMounted) {
                pollTimer.current = setTimeout(poll, delay)
            }
        }

        if (sources.length > 0) {
            poll()
        }
        return () => {
            isMounted = false
            if (pollTimer.current) {
                clearTimeout(pollTimer.current)
                pollTimer.current = null
            }
        }
    }, [sources])

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields()
            if (editing) {
                await updateSource(editing.id, values)
                message.success('更新成功')
            } else {
                await createSource(values)
                message.success('创建成功，日志源已开始采集')
            }
            setModalOpen(false)
            setEditing(null)
            form.resetFields()
            loadSources()
        } catch (e) {
            if (e.errorFields) return
            message.error('操作失败')
        }
    }

    const handleEdit = (record) => {
        setEditing(record)
        form.setFieldsValue(record)
        setModalOpen(true)
    }

    const handleToggle = async (id) => {
        try {
            await toggleSource(id)
            message.success('切换成功')
            loadSources()
        } catch {
            message.error('操作失败')
        }
    }

    const handleFullScan = async (id) => {
        try {
            const res = await triggerFullScan(id)
            if (res?.code === 0) {
                message.success('全量扫描已触发')
                loadSources()
                // 立即拉取进度，避免前端因轮询间隔过大错过 SCANNING 状态
                const progRes = await getScanProgress(id)
                if (progRes?.data) {
                    setProgressMap(prev => ({...prev, [id]: progRes.data}))
                }
            }
        } catch (e) {
            const msg = e?.response?.data?.message || '触发失败'
            message.error(msg)
        }
    }

    const handleDelete = async (id) => {
        try {
            await deleteSource(id)
            message.success('删除成功')
            loadSources()
        } catch {
            message.error('删除失败')
        }
    }

    const columns = [
        {title: '名称', dataIndex: 'name', ellipsis: true, width: 160},
        {
            title: '日志文件路径', dataIndex: 'path', ellipsis: true,
            render: (v) => <Text code style={{fontSize: 12}}>{v}</Text>
        },
        {
            title: '格式', dataIndex: 'formatType', width: 90,
            render: (v) => <Tag>{v}</Tag>
        },
        {
            title: '状态', dataIndex: 'enabled', width: 70, align: 'center',
            render: (v, r) => <Switch checked={v} size="small" onChange={() => handleToggle(r.id)}/>
        },
        {
            title: '最后采集', dataIndex: 'lastCollectTime', width: 160,
            render: (v) => v ? <Text style={{fontSize: 12}}>{formatDateTime(v)}</Text> :
                <Tag color="default">尚未采集</Tag>
        },
        {
            title: '进度', width: 170,
            render: (_, r) => {
                const p = progressMap[r.id]
                if (!p) return <Text type="secondary">-</Text>
                if (p.status === 'SCANNING') {
                    return (
                        <div style={{minWidth: 140}}>
                            <Progress percent={p.progressPercent || 0} size="small"
                                      format={() => `${p.readLines || 0}/${p.totalLines || '?'}`}/>
                            <Text type="secondary" style={{fontSize: 11}}>发现 {p.issuesFound || 0} 个问题</Text>
                        </div>
                    )
                }
                if (p.status === 'TAILING') {
                    const issuesText = (p.issuesFound || 0) > 0 ? `, ${p.issuesFound} 个问题` : ''
                    return <Tag color="processing">监听中 ({p.readLines || 0} 行{issuesText})</Tag>
                }
                if (p.status === 'COMPLETED') {
                    return <Tag color="success">已完成 ({p.issuesFound || 0} 个问题)</Tag>
                }
                return <Text type="secondary">-</Text>
            }
        },
        {
            title: '操作', width: 200,
            render: (_, r) => {
                const scanning = progressMap[r.id]?.status === 'SCANNING'
                return (
                    <Space size="small">
                        <Button size="small" icon={<EditOutlined/>} onClick={() => handleEdit(r)}>编辑</Button>
                        <Button size="small" icon={<ScanOutlined/>} disabled={scanning}
                                onClick={() => handleFullScan(r.id)}>{scanning ? '扫描中' : '全量'}</Button>
                        <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id)}>
                            <Button size="small" danger icon={<DeleteOutlined/>}/>
                        </Popconfirm>
                    </Space>
                )
            }
        },
    ]

    // 空状态引导
    if (!loading && sources.length === 0) {
        return (
            <div style={{padding: 24, maxWidth: 700, margin: '0 auto'}}>
                <Result
                    icon={<CloudServerOutlined style={{fontSize: 72, color: '#bfbfbf'}}/>}
                    title="还没有日志源"
                    subTitle={
                        <div style={{textAlign: 'left', maxWidth: 400, margin: '0 auto'}}>
                            <Paragraph><strong>日志源</strong> 告诉系统去哪里读取日志文件进行分析。</Paragraph>
                            <Paragraph>
                                <FolderOpenOutlined/> <strong>文件路径</strong>：填写服务器上日志文件的完整路径，如
                                <Text code>/var/log/order-service/error.log</Text> 或 Windows 下的
                                <Text code>D:\logs\app.log</Text>
                            </Paragraph>
                            <Paragraph>
                                <strong>日志格式</strong>：选对格式才能正确解析——<Tag>纯文本</Tag>适用 plain
                                log，<Tag>JSON</Tag>适用结构化日志，<Tag>Logback</Tag>适用 Spring Boot 默认格式
                            </Paragraph>
                            <Paragraph>
                                <strong>扫描模式</strong>：<Tag color="blue">增量尾随</Tag>只读新增内容（生产推荐），<Tag
                                color="purple">全量扫描</Tag>重新读取整个文件
                            </Paragraph>
                        </div>
                    }
                    extra={
                        <Button type="primary" size="large" icon={<PlusOutlined/>}
                                onClick={() => {
                                    setEditing(null);
                                    form.resetFields();
                                    setModalOpen(true)
                                }}>
                            添加第一个日志源
                        </Button>
                    }
                />

                <Modal title="添加日志源" open={modalOpen}
                       onOk={handleSubmit} onCancel={() => {
                    setModalOpen(false);
                    setEditing(null)
                }}
                       width={600} destroyOnClose>
                    <Form form={form} layout="vertical"
                          initialValues={{sourceType: 'FILE', formatType: 'PLAIN_TEXT', enabled: true}}>
                        <Alert message="路径必须是可以被本服务进程读取的文件，不支持通配符" type="info" showIcon
                               style={{marginBottom: 16}}/>
                        <Form.Item name="name" label="日志源名称" rules={[{required: true, message: '请输入名称'}]}>
                            <Input placeholder="如：order-service 错误日志" prefix={<CloudServerOutlined/>}/>
                        </Form.Item>
                        <Form.Item name="path" label="日志文件路径"
                                   rules={[{required: true, message: '请输入日志文件的完整路径'}]}
                                   extra="必填，这是日志文件在服务器上的绝对路径">
                            <Input
                                placeholder="/var/log/order-service/error.log  或  D:\logs\app.log"
                                prefix={<FolderOpenOutlined/>}
                                suffix={
                                    <Button
                                        type="link"
                                        size="small"
                                        icon={<FolderOpenOutlined/>}
                                        style={{padding: 0}}
                                        onClick={() => setFilePickerOpen(true)}>
                                        浏览...
                                    </Button>
                                }
                            />
                        </Form.Item>
                        <Space>
                            <Form.Item name="sourceType" label="来源类型" tooltip="目前仅支持本地文件">
                                <Select style={{width: 120}} disabled>
                                    <Select.Option value="FILE">📁 本地文件</Select.Option>
                                </Select>
                            </Form.Item>
                            <Form.Item name="formatType" label="日志格式" tooltip="根据日志实际格式选择">
                                <Select style={{width: 150}}>
                                    <Select.Option value="PLAIN_TEXT">纯文本</Select.Option>
                                    <Select.Option value="JSON">JSON</Select.Option>
                                    <Select.Option value="SYSLOG">Syslog</Select.Option>
                                    <Select.Option value="LOGBACK">Logback</Select.Option>
                                </Select>
                            </Form.Item>
                        </Space>
                        <Space>
                            <Form.Item name="batchSize" label="批次大小" tooltip="全量扫描时每批处理的行数">
                                <InputNumber min={100} max={50000} placeholder="10000"/>
                            </Form.Item>
                        </Space>
                        <Form.Item name="enabled" label="启用采集" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                    </Form>
                </Modal>

                {/* 文件选择器 */}
                <FilePicker
                    open={filePickerOpen}
                    currentValue={form.getFieldValue('path') || ''}
                    onSelect={(filePath) => {
                        form.setFieldsValue({path: filePath})
                        setFilePickerOpen(false)
                    }}
                    onCancel={() => setFilePickerOpen(false)}
                />
            </div>
        )
    }

    return (
        <div style={{padding: 24}}>
            <Row justify="space-between" align="middle" style={{marginBottom: 16}}>
                <Col>
                    <Title level={3} style={{margin: 0}}>日志源管理</Title>
                    <Text type="secondary">配置需要监控的日志文件，系统将自动采集并分析其中的 ERROR/WARN 日志</Text>
                </Col>
                <Col>
                    <Button type="primary" size="large" icon={<PlusOutlined/>}
                            onClick={() => {
                                setEditing(null);
                                form.resetFields();
                                setModalOpen(true)
                            }}>
                        添加日志源
                    </Button>
                </Col>
            </Row>

            <Table
                columns={columns}
                dataSource={sources}
                rowKey="id"
                loading={loading}
                pagination={{pageSize: 10}}
                locale={{emptyText: '暂无日志源，点击「添加日志源」开始'}}
            />

            <Modal title={editing ? '编辑日志源' : '添加日志源'} open={modalOpen}
                   onOk={handleSubmit} onCancel={() => {
                setModalOpen(false);
                setEditing(null)
            }}
                   width={600} destroyOnClose>
                <Form form={form} layout="vertical"
                      initialValues={{sourceType: 'FILE', formatType: 'PLAIN_TEXT', enabled: true}}>
                    <Form.Item name="name" label="日志源名称" rules={[{required: true, message: '请输入名称'}]}>
                        <Input placeholder="如：order-service 错误日志"/>
                    </Form.Item>
                    <Form.Item name="path" label="日志文件路径"
                               rules={[{required: true, message: '请输入日志文件的完整路径'}]}
                               extra="填写日志文件在服务器上的绝对路径">
                        <Input
                            placeholder="/var/log/app/error.log  或  D:\logs\app.log"
                            suffix={
                                <Button
                                    type="link"
                                    size="small"
                                    icon={<FolderOpenOutlined/>}
                                    style={{padding: 0}}
                                    onClick={() => setFilePickerOpen(true)}>
                                    浏览...
                                </Button>
                            }
                        />
                    </Form.Item>
                    <Space>
                        <Form.Item name="sourceType" label="来源类型">
                            <Select style={{width: 120}}>
                                <Select.Option value="FILE">本地文件</Select.Option>
                                <Select.Option value="SFTP">SFTP</Select.Option>
                                <Select.Option value="HTTP">HTTP</Select.Option>
                            </Select>
                        </Form.Item>
                        <Form.Item name="formatType" label="日志格式">
                            <Select style={{width: 140}}>
                                <Select.Option value="PLAIN_TEXT">纯文本</Select.Option>
                                <Select.Option value="JSON">JSON</Select.Option>
                                <Select.Option value="SYSLOG">Syslog</Select.Option>
                                <Select.Option value="LOGBACK">Logback</Select.Option>
                            </Select>
                        </Form.Item>
                    </Space>
                    <Space>
                        <Form.Item name="batchSize" label="批次大小" tooltip="全量扫描时每批处理的行数">
                            <InputNumber min={100} max={50000} placeholder="10000"/>
                        </Form.Item>
                    </Space>
                    <Form.Item name="enabled" label="启用采集" valuePropName="checked">
                        <Switch/>
                    </Form.Item>
                </Form>
            </Modal>

            {/* 文件选择器 */}
            <FilePicker
                open={filePickerOpen}
                currentValue={form.getFieldValue('path') || ''}
                onSelect={(filePath) => {
                    form.setFieldsValue({path: filePath})
                    setFilePickerOpen(false)
                }}
                onCancel={() => setFilePickerOpen(false)}
            />
        </div>
    )
}
