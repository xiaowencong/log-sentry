-- ============================================================
-- Log-Sentry 数据库初始化脚本
-- MySQL 8.0+
-- 编码：utf8mb4，排序：utf8mb4_general_ci
-- JPA 自动驼峰转下划线命名（如 createTime → create_time）
-- ============================================================

CREATE
DATABASE IF NOT EXISTS `log_sentry`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE
`log_sentry`;

-- -----------------------------------------------------------
-- 1. 日志源配置表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `log_source`;
CREATE TABLE `log_source`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `name`              VARCHAR(100) NOT NULL COMMENT '日志源名称',
    `path`              VARCHAR(500) NOT NULL COMMENT '日志文件路径',
    `source_type`       VARCHAR(20)  DEFAULT 'FILE' COMMENT '来源类型: FILE/SFTP/HTTP',
    `format_type`       VARCHAR(20)  DEFAULT 'PLAIN_TEXT' COMMENT '日志格式: PLAIN_TEXT/JSON/SYSLOG/LOGBACK',
    `scan_mode`         VARCHAR(20)  DEFAULT 'INCREMENTAL' COMMENT '扫描模式: INCREMENTAL/FULL_SCAN',
    `enabled`           TINYINT(1)      DEFAULT 1               COMMENT '是否启用',
    `batch_size`        INT          DEFAULT 10000 COMMENT '全量扫描批次大小',
    `last_offset`       BIGINT       DEFAULT 0 COMMENT '上次读取偏移量',
    `last_inode`        VARCHAR(100) DEFAULT NULL COMMENT '文件inode（Linux下用于轮转检测）',
    `last_collect_time` DATETIME     DEFAULT NULL COMMENT '最后一次采集时间',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                 `idx_enabled` (`enabled`),
    KEY                 `idx_source_type` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志源配置';


-- -----------------------------------------------------------
-- 2. 日志记录表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `log_entry`;
CREATE TABLE `log_entry`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `source_id`   BIGINT       DEFAULT NULL COMMENT '关联日志源ID',
    `source_name` VARCHAR(100) DEFAULT NULL COMMENT '日志源名称（冗余）',
    `level`       VARCHAR(10) NOT NULL COMMENT '日志级别: ERROR/WARN',
    `message`     TEXT        NOT NULL COMMENT '日志消息内容',
    `timestamp`   VARCHAR(30)  DEFAULT NULL COMMENT '日志时间戳（原始字符串）',
    `raw_content` MEDIUMTEXT   DEFAULT NULL COMMENT '原始日志行',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY           `idx_source_id` (`source_id`),
    KEY           `idx_level` (`level`),
    KEY           `idx_timestamp` (`timestamp`),
    KEY           `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志记录';


-- -----------------------------------------------------------
-- 3. 问题/告警表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `issue`;
CREATE TABLE `issue`
(
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `summary`            VARCHAR(500) DEFAULT NULL COMMENT '问题摘要（指纹自动生成）',
    `risk_level`         VARCHAR(20) NOT NULL COMMENT '风险等级: CRITICAL/HIGH/MEDIUM/LOW',
    `risk_score`         INT          DEFAULT 0 COMMENT '风险评分 0-100',
    `category`           VARCHAR(50)  DEFAULT NULL COMMENT '问题分类: 系统资源/网络通信/数据库/代码缺陷/性能问题/服务异常',
    `service_name`       VARCHAR(100) DEFAULT NULL COMMENT '来源服务名称',
    `source`             VARCHAR(500) DEFAULT NULL COMMENT '日志来源路径',
    `status`             VARCHAR(20)  DEFAULT 'OPEN' COMMENT '处理状态: OPEN/ACKNOWLEDGED/RESOLVED/CLOSED',
    `ai_analysis_status` VARCHAR(20)  DEFAULT NULL COMMENT 'AI分析状态: PENDING/ANALYZING/COMPLETED/FAILED',
    `occurrence_count`   INT          DEFAULT 1 COMMENT '出现次数',
    `first_seen`         DATETIME     DEFAULT NULL COMMENT '首次出现时间',
    `last_seen`          DATETIME     DEFAULT NULL COMMENT '最近出现时间',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                  `idx_risk_level` (`risk_level`),
    KEY                  `idx_status` (`status`),
    KEY                  `idx_ai_status` (`ai_analysis_status`),
    KEY                  `idx_service` (`service_name`),
    KEY                  `idx_first_seen` (`first_seen`),
    KEY                  `idx_risk_score` (`risk_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题/告警';


-- -----------------------------------------------------------
-- 4. Issue ↔ LogEntry 关联表（多对多，通过指纹聚合）
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `log_fingerprint`;
CREATE TABLE `log_fingerprint`
(
    `id`           BIGINT NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `issue_id`     BIGINT NOT NULL COMMENT '关联 Issue ID',
    `log_entry_id` BIGINT NOT NULL COMMENT '关联 LogEntry ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_issue_log` (`issue_id`, `log_entry_id`),
    KEY            `idx_log_entry_id` (`log_entry_id`),
    CONSTRAINT `fk_fp_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_fp_log` FOREIGN KEY (`log_entry_id`) REFERENCES `log_entry` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Issue与日志多对多关联（指纹聚合）';


-- -----------------------------------------------------------
-- 5. 分析规则表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `rule`;
CREATE TABLE `rule`
(
    `id`          BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `name`        VARCHAR(100)  NOT NULL COMMENT '规则名称',
    `pattern`     VARCHAR(1000) NOT NULL COMMENT '匹配正则表达式',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '规则描述',
    `risk_level`  VARCHAR(20)   NOT NULL COMMENT '命中后风险等级: CRITICAL/HIGH/MEDIUM/LOW',
    `risk_score`  INT          DEFAULT 70 COMMENT '命中后风险评分 0-100',
    `category`    VARCHAR(50)  DEFAULT NULL COMMENT '问题分类',
    `enabled`     TINYINT(1)      DEFAULT 1               COMMENT '是否启用',
    `priority`    INT          DEFAULT 0 COMMENT '优先级（越大越优先）',
    `is_builtin`  TINYINT(1)      DEFAULT 0               COMMENT '是否为内置规则（内置规则不可删除）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY           `idx_enabled` (`enabled`),
    KEY           `idx_risk_level` (`risk_level`),
    KEY           `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析规则';


-- -----------------------------------------------------------
-- 6. AI 分析结果表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `ai_analysis`;
CREATE TABLE `ai_analysis`
(
    `id`                    BIGINT NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `issue_id`              BIGINT NOT NULL COMMENT '关联 Issue ID',
    `summary`               VARCHAR(500) DEFAULT NULL COMMENT 'AI 生成的摘要',
    `root_cause`            TEXT         DEFAULT NULL COMMENT '根因分析',
    `suggestions`           TEXT         DEFAULT NULL COMMENT '处理建议（JSON数组字符串）',
    `risk_level_override`   VARCHAR(20)  DEFAULT NULL COMMENT 'AI 修正的风险等级',
    `need_immediate_action` TINYINT(1)    DEFAULT 0               COMMENT '是否需要立即处理',
    `related_knowledge`     TEXT         DEFAULT NULL COMMENT '相关知识点',
    `deepseek_model_used`   VARCHAR(50)  DEFAULT NULL COMMENT '使用的模型名称',
    `api_cost_ms`           INT          DEFAULT NULL COMMENT 'API 调用耗时（毫秒）',
    `token_usage`           VARCHAR(200) DEFAULT NULL COMMENT 'Token 用量信息',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY                     `idx_issue_id` (`issue_id`),
    CONSTRAINT `fk_ai_issue` FOREIGN KEY (`issue_id`) REFERENCES `issue` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析结果';


-- -----------------------------------------------------------
-- 7. 报告表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `report`;
CREATE TABLE `report`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `type`           VARCHAR(20) NOT NULL COMMENT '报告类型: DAILY/WEEKLY/MONTHLY/CUSTOM',
    `title`          VARCHAR(200) DEFAULT NULL COMMENT '报告标题',
    `start_time`     DATETIME    NOT NULL COMMENT '统计起始时间',
    `end_time`       DATETIME    NOT NULL COMMENT '统计结束时间',
    `total_issues`   INT          DEFAULT 0 COMMENT '总问题数',
    `critical_count` INT          DEFAULT 0 COMMENT '严重级别数量',
    `high_count`     INT          DEFAULT 0 COMMENT '高级别数量',
    `medium_count`   INT          DEFAULT 0 COMMENT '中等级别数量',
    `low_count`      INT          DEFAULT 0 COMMENT '低级别数量',
    `content`        MEDIUMTEXT   DEFAULT NULL COMMENT '报告内容（Markdown格式）',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY              `idx_type` (`type`),
    KEY              `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析报告';


-- ============================================================
-- 8. 初始化 10 条内置规则
-- ============================================================
INSERT INTO `rule` (`name`, `pattern`, `description`, `risk_level`, `risk_score`, `category`, `enabled`, `priority`,
                    `is_builtin`)
VALUES ('OutOfMemoryError', 'java\\.lang\\.OutOfMemoryError', 'JVM 内存溢出错误', 'CRITICAL', 90, '系统资源', 1, 100,
        1),
       ('ConnectionTimeout', '(?i)(connection.*tim(?:e)?out|connect.*refused)', '数据库/网络连接超时或拒绝', 'HIGH', 75,
        '网络通信', 1, 90, 1),
       ('NullPointerException', 'java\\.lang\\.NullPointerException', '空指针异常', 'HIGH', 65, '代码缺陷', 1, 80, 1),
       ('DeadlockDetected', '(?i)(deadlock|dead lock) found', '数据库死锁检测', 'CRITICAL', 85, '数据库', 1, 95, 1),
       ('DiskFull', '(?i)(no space left|disk.*full|insufficient disk)', '磁盘空间不足', 'CRITICAL', 88, '系统资源', 1,
        98, 1),
       ('SlowQuery', '(?i)(slow.*quer|long.*query|query.*time.*exceed)', '慢查询告警', 'HIGH', 70, '数据库', 1, 75, 1),
       ('CircuitBreakerOpen', '(?i)(circuit.*breaker.*open|service.*unavailable)', '熔断器打开/服务不可用', 'HIGH', 80,
        '服务异常', 1, 85, 1),
       ('HighCPU', '(?i)(cpu.*(?:usage|utilization).*high|cpu.*exceed)', 'CPU使用率过高', 'HIGH', 72, '系统资源', 1, 82,
        1),
       ('404NotFound', '(?i)(404|not found).*(?:resource|page|endpoint|url|api)', '资源/接口404不存在', 'MEDIUM', 40,
        '服务异常', 1, 55, 1),
       ('RetryExhausted', '(?i)(retry.*exhaust|max.*retr(y|ies).*exceed)', '重试次数耗尽', 'HIGH', 75, '服务异常', 1,
        88, 1);
