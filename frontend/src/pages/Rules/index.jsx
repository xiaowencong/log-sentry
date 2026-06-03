import {useEffect, useState} from 'react'
import {
    Button,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Typography
} from 'antd'
import {DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons'
import {createRule, deleteRule, getRules, toggleRule, updateRule} from '../../api/rule.js'
import RiskBadge from '../../components/RiskBadge.jsx'

const {Title} = Typography

export default function Rules() {
    const [rules, setRules] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [form] = Form.useForm()

    useEffect(() => {
        loadRules()
    }, [])

    const loadRules = async () => {
        setLoading(true)
        try {
            const res = await getRules()
            if (res?.code === 0) setRules(res.data || [])
        } catch (e) {
            message.error('加载规则失败')
        } finally {
            setLoading(false)
        }
    }

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields()
            if (editing) {
                await updateRule(editing.id, values)
                message.success('更新成功')
            } else {
                await createRule(values)
                message.success('创建成功')
            }
            setModalOpen(false);
            setEditing(null);
            form.resetFields();
            loadRules()
        } catch (e) {
            if (e.errorFields) return
            message.error('操作失败')
        }
    }

    const handleEdit = (record) => {
        if (record.isBuiltin) {
            message.warning('内置规则不可编辑');
            return
        }
        setEditing(record)
        form.setFieldsValue(record)
        setModalOpen(true)
    }

    const handleToggle = async (id) => {
        try {
            await toggleRule(id);
            message.success('切换成功');
            loadRules()
        } catch (e) {
            message.error('操作失败')
        }
    }

    const handleDelete = async (id) => {
        try {
            await deleteRule(id);
            message.success('删除成功');
            loadRules()
        } catch (e) {
            message.error(e?.response?.data?.message || '删除失败')
        }
    }

    const columns = [
        {
            title: '规则名', dataIndex: 'name', ellipsis: true,
            render: (v, r) => <>{v} {r.isBuiltin && <Tag color="blue" style={{marginLeft: 4}}>内置</Tag>}</>
        },
        {title: '匹配模式', dataIndex: 'pattern', ellipsis: true, width: 300},
        {
            title: '风险等级', dataIndex: 'riskLevel', width: 100,
            render: (v) => <RiskBadge level={v}/>
        },
        {title: '风险评分', dataIndex: 'riskScore', width: 80},
        {title: '分类', dataIndex: 'category', width: 100},
        {
            title: '启用', dataIndex: 'enabled', width: 60,
            render: (v, r) => <Switch checked={v} onChange={() => handleToggle(r.id)}/>
        },
        {
            title: '操作', width: 180,
            render: (_, r) => (
                <Space>
                    <Button size="small" icon={<EditOutlined/>} onClick={() => handleEdit(r)}
                            disabled={r.isBuiltin}>编辑</Button>
                    <Popconfirm title="确定删除？" onConfirm={() => handleDelete(r.id)}
                                disabled={r.isBuiltin}>
                        <Button size="small" danger icon={<DeleteOutlined/>} disabled={r.isBuiltin}/>
                    </Popconfirm>
                </Space>
            )
        },
    ]

    return (
        <div style={{padding: 24}}>
            <Title level={3}>
                规则配置
                <Button type="primary" icon={<PlusOutlined/>} style={{marginLeft: 16}}
                        onClick={() => {
                            setEditing(null);
                            form.resetFields();
                            setModalOpen(true)
                        }}>
                    添加规则
                </Button>
            </Title>

            <Table columns={columns} dataSource={rules} rowKey="id" loading={loading} pagination={{pageSize: 10}}/>

            <Modal title={editing ? '编辑规则' : '添加规则'} open={modalOpen}
                   onOk={handleSubmit} onCancel={() => {
                setModalOpen(false);
                setEditing(null)
            }}
                   width={600} destroyOnClose>
                <Form form={form} layout="vertical" initialValues={{riskLevel: 'HIGH', riskScore: 70, enabled: true}}>
                    <Form.Item name="name" label="规则名称" rules={[{required: true}]}>
                        <Input placeholder="如：OutOfMemoryError"/>
                    </Form.Item>
                    <Form.Item name="pattern" label="匹配模式（正则表达式）" rules={[{required: true}]}
                               extra="支持 Java 正则语法，匹配日志内容。例：java\.lang\.OutOfMemoryError">
                        <Input.TextArea rows={3} placeholder="正则表达式"/>
                    </Form.Item>
                    <Space>
                        <Form.Item name="riskLevel" label="风险等级" rules={[{required: true}]}>
                            <Select style={{width: 140}}>
                                <Select.Option value="CRITICAL">严重</Select.Option>
                                <Select.Option value="HIGH">高危</Select.Option>
                                <Select.Option value="MEDIUM">中等</Select.Option>
                                <Select.Option value="LOW">低</Select.Option>
                            </Select>
                        </Form.Item>
                        <Form.Item name="riskScore" label="风险评分 (0-100)">
                            <InputNumber min={0} max={100}/>
                        </Form.Item>
                    </Space>
                    <Space>
                        <Form.Item name="category" label="分类">
                            <Select style={{width: 150}} allowClear placeholder="选择分类">
                                <Select.Option value="系统资源">系统资源</Select.Option>
                                <Select.Option value="网络通信">网络通信</Select.Option>
                                <Select.Option value="数据库">数据库</Select.Option>
                                <Select.Option value="代码缺陷">代码缺陷</Select.Option>
                                <Select.Option value="性能问题">性能问题</Select.Option>
                                <Select.Option value="服务异常">服务异常</Select.Option>
                            </Select>
                        </Form.Item>
                        <Form.Item name="enabled" label="启用" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                    </Space>
                </Form>
            </Modal>
        </div>
    )
}
