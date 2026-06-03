import {BrowserRouter, Route, Routes, useLocation, useNavigate} from 'react-router-dom'
import {Layout, Menu, Tooltip} from 'antd'
import {
    AlertOutlined,
    CloudServerOutlined,
    DashboardOutlined,
    FileTextOutlined,
    GithubOutlined,
    SafetyOutlined,
    SettingOutlined
} from '@ant-design/icons'
import Dashboard from './pages/Dashboard/index.jsx'
import IssueList from './pages/IssueList/index.jsx'
import IssueDetail from './pages/IssueDetail/index.jsx'
import Reports from './pages/Reports/index.jsx'
import LogSources from './pages/LogSources/index.jsx'
import Rules from './pages/Rules/index.jsx'
import Settings from './pages/Settings/index.jsx'

const {Sider, Content} = Layout

const GITHUB_URL = 'https://github.com/xiaowencong/log-sentry'

const menuItems = [
    {key: '/', icon: <DashboardOutlined/>, label: '总览面板'},
    {key: '/issues', icon: <AlertOutlined/>, label: '问题列表'},
    {key: '/sources', icon: <CloudServerOutlined/>, label: '日志源管理'},
    {key: '/rules', icon: <SafetyOutlined/>, label: '规则配置'},
    {key: '/reports', icon: <FileTextOutlined/>, label: '报表中心'},
    {key: '/settings', icon: <SettingOutlined/>, label: '系统设置'},
]

function AppLayout() {
    const navigate = useNavigate()
    const location = useLocation()

    const selectedKey = '/' + location.pathname.split('/')[1]

    return (
        <Layout style={{minHeight: '100vh'}}>
            <Sider
                breakpoint="lg"
                collapsedWidth="64"
                style={{
                    background: '#001529',
                    display: 'flex', flexDirection: 'column',
                    position: 'sticky', top: 0, height: '100vh',
                }}>
                <div style={{
                    height: 48, margin: 16,
                    color: '#fff', fontSize: 18, fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    whiteSpace: 'nowrap', overflow: 'hidden',
                }}>
                    <SafetyOutlined style={{marginRight: 8, fontSize: 22}}/>
                    Log-Sentry
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[selectedKey]}
                    items={menuItems}
                    onClick={({key}) => navigate(key)}
                    style={{flex: 1, borderRight: 0}}
                />
                <Tooltip title="在 GitHub 查看源码" placement="right">
                    <a
                        href={GITHUB_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            gap: 8, padding: '12px 16px',
                            color: 'rgba(255,255,255,0.65)',
                            borderTop: '1px solid rgba(255,255,255,0.08)',
                            fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.color = '#fff')}
                        onMouseLeave={(e) => (e.currentTarget.style.color = 'rgba(255,255,255,0.65)')}
                    >
                        <GithubOutlined style={{fontSize: 18}}/>
                        <span>GitHub</span>
                    </a>
                </Tooltip>
            </Sider>
            <Layout>
                <Content style={{background: '#f5f5f5'}}>
                    <Routes>
                        <Route path="/" element={<Dashboard/>}/>
                        <Route path="/issues" element={<IssueList/>}/>
                        <Route path="/issues/:id" element={<IssueDetail/>}/>
                        <Route path="/reports" element={<Reports/>}/>
                        <Route path="/sources" element={<LogSources/>}/>
                        <Route path="/rules" element={<Rules/>}/>
                        <Route path="/settings" element={<Settings/>}/>
                    </Routes>
                </Content>
            </Layout>
        </Layout>
    )
}

function App() {
    return (
        <BrowserRouter>
            <AppLayout/>
        </BrowserRouter>
    )
}

export default App
