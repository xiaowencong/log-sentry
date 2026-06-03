import {useEffect, useState} from 'react'
import {Alert, Button, Card, Col, Descriptions, Row, Space, Spin, Statistic, Tag, Typography} from 'antd'
import {
    ApiOutlined,
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    CloudServerOutlined,
    DatabaseOutlined,
    GithubOutlined,
    SafetyCertificateOutlined,
    StarOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons'

const {Title, Text, Paragraph, Link} = Typography

const GITHUB_URL = 'https://github.com/xiaowencong/log-sentry'

export default function Settings() {
    const [status, setStatus] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        const check = async () => {
            try {
                const res = await fetch('/api/sources')
                if (res.ok) {
                    setStatus({backend: 'connected', db: 'connected'})
                } else {
                    setStatus({backend: 'connected', db: 'error'})
                }
            } catch {
                setStatus({backend: 'disconnected', db: 'unknown'})
            } finally {
                setLoading(false)
            }
        }
        check()
    }, [])

    if (loading) return <div style={{padding: 48, textAlign: 'center'}}><Spin size="large"/></div>

    return (
        <div style={{padding: 24}}>
            <Title level={3}>系统设置</Title>
            <Text type="secondary" style={{display: 'block', marginBottom: 24}}>
                修改配置项请编辑 <Text code>backend/src/main/resources/application.yml</Text>，修改后重启服务生效
            </Text>

            {/* 服务状态 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="后端服务"
                            value={status?.backend === 'connected' ? '运行中' : '离线'}
                            valueStyle={{color: status?.backend === 'connected' ? '#52c41a' : '#ff4d4f', fontSize: 20}}
                            prefix={status?.backend === 'connected' ? <CheckCircleOutlined/> : <CloseCircleOutlined/>}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="数据库"
                            value={status?.db === 'connected' ? '已连接' : '未知'}
                            valueStyle={{color: status?.db === 'connected' ? '#52c41a' : '#faad14', fontSize: 20}}
                            prefix={<DatabaseOutlined/>}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="端口"
                            value={8080}
                            prefix={<CloudServerOutlined/>}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card>
                        <Statistic
                            title="前端代理"
                            value="/api → :8080"
                            valueStyle={{fontSize: 16}}
                            prefix={<ThunderboltOutlined/>}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={16}>
                {/* DeepSeek 配置 */}
                <Col xs={24} lg={12} style={{marginBottom: 16}}>
                    <Card title={<><SafetyCertificateOutlined/> DeepSeek AI 配置</>}>
                        <Descriptions column={1} bordered size="small">
                            <Descriptions.Item label="模型">
                                <Tag color="purple">deepseek-v4-pro</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="最大输出 Token">
                                <Tag color="blue">4096</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="温度 (Temperature)">
                                <Tag color="green">0.3</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="API 限流">
                                <Tag color="orange">10 次/分钟</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="缓存时间">
                                <Tag color="cyan">24 小时</Tag>
                            </Descriptions.Item>
                        </Descriptions>
                        <Alert
                            style={{marginTop: 12}}
                            message="API Key 已配置"
                            description="Key 存储在 yml 中，不会被前端暴露"
                            type="success"
                            showIcon
                        />
                    </Card>
                </Col>

                {/* 采集配置 */}
                <Col xs={24} lg={12} style={{marginBottom: 16}}>
                    <Card title={<><ClockCircleOutlined/> 日志采集配置</>}>
                        <Descriptions column={1} bordered size="small">
                            <Descriptions.Item label="轮询间隔">
                                <Tag color="blue">500ms</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="单次最大行数">
                                <Tag color="blue">5,000 行</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="全量扫描批次">
                                <Tag color="green">10,000 行/批</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="批次间隔">
                                <Tag color="green">100ms</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="聚合时间窗口">
                                <Tag color="orange">5 分钟</Tag>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>

                {/* 数据库配置 */}
                <Col xs={24} lg={12} style={{marginBottom: 16}}>
                    <Card title={<><DatabaseOutlined/> 数据库配置</>}>
                        <Descriptions column={1} bordered size="small">
                            <Descriptions.Item label="类型">
                                <Tag color="blue">MySQL 5.6+</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="主机">
                                <Text code>10.87.200.42:3306</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="数据库名">
                                <Text code>log_sentry</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="用户">
                                <Text code>root</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="字符集">
                                <Tag>utf8mb4</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="建表策略">
                                <Tag color="green">ddl-auto: update（自动建表）</Tag>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>

                {/* 技术栈 */}
                <Col xs={24} lg={12} style={{marginBottom: 16}}>
                    <Card title={<><ApiOutlined/> 技术栈</>}>
                        <Descriptions column={1} bordered size="small">
                            <Descriptions.Item label="后端框架">
                                <Tag color="green">Spring Boot 2.7.18</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="ORM">
                                <Tag color="blue">JPA / Hibernate</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="前端框架">
                                <Tag color="cyan">React 18 + Vite 8</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="UI 组件">
                                <Tag color="purple">Ant Design 5</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="AI 服务">
                                <Tag color="volcano">DeepSeek v4 Pro</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="调度">
                                <Tag>Quartz + @Scheduled</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="HTTP 客户端">
                                <Tag>OkHttp 4</Tag>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </Col>
            </Row>

            {/* 关于项目 */}
            <Card
                title={<><GithubOutlined/> 关于项目</>}
                style={{marginTop: 8}}
                extra={
                    <Space>
                        <Button
                            type="primary"
                            icon={<GithubOutlined/>}
                            href={GITHUB_URL}
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            查看源码
                        </Button>
                        <Button
                            icon={<StarOutlined/>}
                            href={`${GITHUB_URL}/stargazers`}
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            Star 一下
                        </Button>
                    </Space>
                }
            >
                <Paragraph style={{marginBottom: 8}}>
                    Log-Sentry 是一个开源的日志智能分析平台，结合规则引擎与 AI 大模型，
                    自动识别异常、聚合问题并生成报告。
                </Paragraph>
                <Paragraph style={{marginBottom: 0}}>
                    项目地址：
                    <Link href={GITHUB_URL} target="_blank" rel="noopener noreferrer">
                        {GITHUB_URL.replace('https://', '')}
                    </Link>
                    <Text type="secondary" style={{marginLeft: 12}}>
                        欢迎 Issue / PR / Star
                    </Text>
                </Paragraph>
            </Card>
        </div>
    )
}
