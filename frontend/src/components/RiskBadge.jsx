import {Tag} from 'antd'

const riskConfig = {
    CRITICAL: {color: 'red', text: '严重'},
    HIGH: {color: 'orange', text: '高危'},
    MEDIUM: {color: 'gold', text: '中等'},
    LOW: {color: 'blue', text: '低'},
}

export default function RiskBadge({level}) {
    const config = riskConfig[level] || {color: 'default', text: level}
    return <Tag color={config.color}>{config.text}</Tag>
}
