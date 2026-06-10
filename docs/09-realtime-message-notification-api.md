# 实时站内信接口文档

## 1. 文档目标

本文档定义平台的站内信、消息中心和 WebSocket 实时通道，供前端、运维操作和平台服务统一调用。

当前系统只保留一条发信路径：站内信。

## 2. 基础约定

- 内部接口前缀：`/api/v1/message/*`
- WebSocket 地址：`/ws/message`
- 统一响应结构遵循平台标准 `ApiResponse`
- 租户上下文由服务端校验，不由前端自行决定

### 2.1 目标范围

- `TENANT`：当前租户全部用户可见
- `USER`：仅指定用户可见
- `ROLE`：仅指定角色分组可见

### 2.2 发布来源

- `MANUAL`：后台人工发布

## 3. 内部管理接口

### 3.1 查询消息列表

- `GET /api/v1/message/messages?pageNo=1&pageSize=10`

返回当前用户可见的站内信列表。

### 3.2 发送站内信

- `POST /api/v1/message/messages`

请求体：

```json
{
  "title": "审批提醒",
  "content": "您有一条待处理审批，请尽快查看。",
  "targetScope": "USER",
  "targetUserId": 10001
}
```

`targetScope` 也可以取值 `TENANT`，表示租户广播站内信。

当 `targetScope` 为 `ROLE` 时，需要传入 `targetRoleId`，表示发送给对应角色分组的所有用户。

### 3.3 撤回站内信

- `POST /api/v1/message/messages/{id}/retract`

### 3.4 站内信已读

- `POST /api/v1/message/messages/{id}/read`

### 3.5 全部已读

- `POST /api/v1/message/read-all`

返回当前用户剩余的未读数，适合消息中心“全部标为已读”按钮调用。

### 3.6 未读数

- `GET /api/v1/message/unread-count`

返回示例：

```json
{
  "unreadCount": 3
}
```

### 3.7 查询站内信归档

- `GET /api/v1/message/archive?pageNo=1&pageSize=10`

返回当前租户的站内信归档记录，支持按关键字、目标范围、状态和发布时间范围筛选。

## 4. WebSocket 实时通道

### 4.1 连接方式

浏览器或前端可通过以下方式连接：

```text
ws://localhost:8080/ws/message?accessToken=YOUR_ACCESS_TOKEN
```

也支持在握手请求中带 `Authorization: Bearer <token>`。

### 4.2 认证规则

- 握手时必须携带当前登录态 `accessToken`
- 服务端会校验会话、租户、权限快照和过期状态
- 认证失败时握手直接拒绝

### 4.3 事件格式

推送消息统一使用 JSON 文本：

```json
{
  "eventType": "NOTICE_CREATED",
  "tenantId": 1001,
  "userId": 10001,
  "unreadCount": 2,
  "message": "消息已发布",
  "notice": {
    "id": 9001,
    "tenantId": 1001,
    "messageType": "MESSAGE",
    "targetScope": "TENANT",
    "title": "系统维护提醒",
    "content": "今晚 23:00-24:00 进行系统维护。",
    "sourceType": "MANUAL",
    "publishStatus": "PUBLISHED",
    "publishedAt": "2026-04-19T10:00:00"
  },
  "timestamp": "2026-04-19T10:00:01"
}
```

### 4.4 事件类型

- `CONNECTED`：连接成功
- `HEARTBEAT`：心跳
- `NOTICE_CREATED`：新站内信发布
- `NOTICE_RETRACTED`：站内信已撤回
- `NOTICE_READ`：站内信已读
- `UNREAD_COUNT`：未读数更新

## 5. 兼容说明

- 现有 `/api/v1/system/notifications` 仍保留，作为消息归档兼容入口
- 站内信能力统一走 `/api/v1/message/*`
- 新能力落库表为 `msg_notice` 与 `msg_notice_read`

## 6. 权限说明

消息中心使用以下权限：

- `message:message:view`
- `message:message:write`
- `message:message:read`
- `message:message:retract`

消息归档页面使用以下权限：

- `system:notification:view`
- `system:notification:write`

## 7. 返回与错误

- 所有 HTTP 接口统一返回 `ApiResponse`
- WebSocket 连接失败直接返回 HTTP 401/403
