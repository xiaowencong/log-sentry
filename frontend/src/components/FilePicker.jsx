import {useEffect, useState} from 'react'
import {Button, List, message, Modal, Space, Spin, Tag, Typography} from 'antd'
import {FileTextOutlined, FolderOpenOutlined, HomeOutlined, LeftOutlined,} from '@ant-design/icons'

const {Text} = Typography

/**
 * 文件选择器弹窗——浏览服务器文件系统选择日志文件
 */
export default function FilePicker({open, onSelect, onCancel, currentValue}) {
    const [path, setPath] = useState('/')
    const [items, setItems] = useState([])
    const [loading, setLoading] = useState(false)
    const [shortcuts, setShortcuts] = useState([])
    const [selectedFile, setSelectedFile] = useState(null)

    useEffect(() => {
        if (!open) return
        const initPath = currentValue || '/'
        setPath(initPath)
        setSelectedFile(currentValue || null)
        loadPath(initPath)
        loadShortcuts()
    }, [open])

    const loadPath = async (dirPath) => {
        if (!dirPath) return
        setLoading(true)
        try {
            const res = await fetch(`/api/files/browse?path=${encodeURIComponent(dirPath)}`)
            const json = await res.json()
            if (json.code === 0) {
                setItems(json.data || [])
                setPath(dirPath)
            } else {
                message.error(json.message || '无法访问该目录')
            }
        } catch (e) {
            message.error('请求失败，请检查后端服务是否启动')
        } finally {
            setLoading(false)
        }
    }

    const loadShortcuts = async () => {
        try {
            const res = await fetch('/api/files/shortcuts')
            const json = await res.json()
            if (json.code === 0) setShortcuts(json.data || [])
        } catch (_) {
        }
    }

    const handleItemClick = (item) => {
        if (item.type === 'FILE') {
            setSelectedFile(item.path)
        } else {
            loadPath(item.path)
        }
    }

    const handleConfirm = () => {
        if (selectedFile) {
            onSelect(selectedFile)
        }
    }

    const goUp = () => {
        // Win: C:\ → 父目录为 null
        const p = path.replace(/\\/g, '/').replace(/\/$/, '')
        const lastSlash = p.lastIndexOf('/')
        if (lastSlash <= 0) {
            loadPath('/')
        } else {
            loadPath(p.substring(0, lastSlash))
        }
    }

    const formatSize = (bytes) => {
        if (!bytes) return ''
        if (bytes < 1024) return bytes + ' B'
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    }

    return (
        <Modal
            title="选择日志文件"
            open={open}
            onOk={handleConfirm}
            onCancel={onCancel}
            width={680}
            okText={selectedFile ? `选择 ${selectedFile.split(/[\\/]/).pop()}` : '请先选择文件'}
            okButtonProps={{disabled: !selectedFile}}>

            {/* 快捷入口 */}
            {shortcuts.length > 0 && (
                <div style={{marginBottom: 12}}>
                    <Text type="secondary" style={{fontSize: 12, marginRight: 8}}>快速跳转：</Text>
                    <Space size={4} wrap>
                        <Button size="small" icon={<HomeOutlined/>} onClick={() => loadPath('/')}>根目录</Button>
                        {shortcuts.map((s) => (
                            <Button
                                key={s.path}
                                size="small"
                                type={path === s.path ? 'primary' : 'default'}
                                onClick={() => loadPath(s.path)}>
                                {s.name}
                            </Button>
                        ))}
                    </Space>
                </div>
            )}

            {/* 路径导航栏 */}
            <div style={{
                display: 'flex', alignItems: 'center', gap: 8,
                marginBottom: 8, padding: '6px 10px',
                background: '#fafafa', borderRadius: 4, border: '1px solid #f0f0f0',
            }}>
                <Button size="small" icon={<LeftOutlined/>} onClick={goUp} title="上级目录"/>
                <Text code style={{
                    flex: 1, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap', background: 'transparent', border: 'none',
                }}>
                    {path}
                </Text>
            </div>

            {/* 已选文件 */}
            {selectedFile && (
                <div style={{
                    marginBottom: 8, padding: '6px 12px',
                    background: '#f6ffed', borderRadius: 4,
                    display: 'flex', alignItems: 'center', gap: 8,
                }}>
                    <FileTextOutlined style={{color: '#52c41a'}}/>
                    <Text style={{flex: 1, fontSize: 13, wordBreak: 'break-all'}}>{selectedFile}</Text>
                </div>
            )}

            {/* 文件列表 */}
            <div style={{
                maxHeight: 360, overflow: 'auto',
                border: '1px solid #f0f0f0', borderRadius: 4,
            }}>
                {loading ? (
                    <div style={{padding: 48, textAlign: 'center'}}><Spin/></div>
                ) : items.length === 0 ? (
                    <div style={{padding: 48, textAlign: 'center', color: '#999'}}>
                        此目录为空或无日志文件
                    </div>
                ) : (
                    <List
                        size="small"
                        dataSource={items}
                        split={false}
                        renderItem={(item) => {
                            const isSelected = selectedFile === item.path
                            const isDir = item.type === 'DIR'
                            return (
                                <List.Item
                                    onClick={() => handleItemClick(item)}
                                    style={{
                                        cursor: 'pointer',
                                        padding: '5px 12px',
                                        background: isSelected ? '#e6f7ff' : 'transparent',
                                        borderLeft: isSelected ? '3px solid #1890ff' : '3px solid transparent',
                                        transition: 'background 0.15s',
                                    }}>
                                    <div style={{display: 'flex', alignItems: 'center', gap: 8, width: '100%'}}>
                                        {isDir ? (
                                            <FolderOpenOutlined style={{color: '#faad14', fontSize: 15}}/>
                                        ) : (
                                            <FileTextOutlined style={{color: '#1890ff', fontSize: 15}}/>
                                        )}
                                        <Text
                                            style={{
                                                flex: 1, fontSize: 13,
                                                fontWeight: isDir ? 500 : 400,
                                                color: isSelected ? '#1890ff' : (isDir ? '#333' : '#595959'),
                                            }}
                                            ellipsis>
                                            {item.name}
                                        </Text>
                                        {!isDir && (
                                            <Text type="secondary"
                                                  style={{fontSize: 11, minWidth: 55, textAlign: 'right'}}>
                                                {formatSize(item.size)}
                                            </Text>
                                        )}
                                        <Tag
                                            color={isDir ? 'gold' : 'default'}
                                            style={{fontSize: 10, margin: 0, padding: '0 4px', lineHeight: '16px'}}>
                                            {isDir ? '目录' : '日志'}
                                        </Tag>
                                    </div>
                                </List.Item>
                            )
                        }}
                    />
                )}
            </div>
        </Modal>
    )
}
