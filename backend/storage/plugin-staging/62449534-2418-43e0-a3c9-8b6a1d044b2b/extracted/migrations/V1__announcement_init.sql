CREATE TABLE IF NOT EXISTS plugin_announcement_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    published_flag TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plugin_announcement_notice_title (tenant_id, title)
);

INSERT INTO plugin_announcement_notice (tenant_id, title, content, published_flag)
SELECT 1001, '欢迎使用公告插件', '这是热安装示例插件安装后的首条公告。', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM plugin_announcement_notice
    WHERE tenant_id = 1001
      AND title = '欢迎使用公告插件'
);
