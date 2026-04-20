# 实时消息与公告通知接口文档

## 1. 文档目标

本文档定义平台的实时消息、公告通知和 bot 开放调用方式，供前端、运维脚本和后续 bot 接入直接调用。

当前方案包含三部分：

- 内部管理接口：用于后台管理、运维操作和平台服务调用。
- WebSocket 实时通道：用于前端实时接收通知。
- bot 开放接口：用于外部机器人或第三方系统签名调用。

## 2. 基础约定

- 内部接口前缀：`/api/v1/message/*`
- WebSocket 地址：`/ws/message`
- bot 开放接口前缀：`/openapi/v1/message/*`
- 统一响应结构遵循平台标准 `ApiResponse`
- 租户上下文由服务端校验，不由前端自行决定

### 2.1 通知类型

- `ANNOUNCEMENT`：公告通知，面向整个租户广播
- `MESSAGE`：站内消息，可面向租户或指定用户

### 2.2 目标范围

- `TENANT`：当前租户全部用户可见
- `USER`：仅指定用户可见

### 2.3 发布来源

- `MANUAL`：后台人工发布
- `OPENAPI`：通过开放接口发布

## 3. 内部管理接口

### 3.1 查询公告列表

- `GET /api/v1/message/announcements?pageNo=1&pageSize=10`

返回当前租户已发布公告。

### 3.2 发布公告

- `POST /api/v1/message/announcements`

请求体：

```json
{
  "title": "系统维护公告",
  "content": "今晚 23:00-24:00 进行系统维护。"
}
```

### 3.3 撤回公告

- `POST /api/v1/message/announcements/{id}/retract`

### 3.4 公告已读

- `POST /api/v1/message/announcements/{id}/read`

### 3.5 查询消息列表

- `GET /api/v1/message/messages?pageNo=1&pageSize=10`

返回当前用户可见的站内消息。

### 3.6 发送消息

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

`targetScope` 也可以取值 `TENANT`，表示租户广播消息。

### 3.7 撤回消息

- `POST /api/v1/message/messages/{id}/retract`

### 3.8 消息已读

- `POST /api/v1/message/messages/{id}/read`

### 3.9 全部已读

- `POST /api/v1/message/read-all`

返回当前用户剩余的未读数，适合消息中心“全部标为已读”按钮调用。

### 3.10 未读数

- `GET /api/v1/message/unread-count`

返回示例：

```json
{
  "unreadCount": 3
}
```

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
    "messageType": "ANNOUNCEMENT",
    "targetScope": "TENANT",
    "title": "系统维护公告",
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
- `NOTICE_CREATED`：新公告或新消息
- `NOTICE_RETRACTED`：通知已撤回
- `NOTICE_READ`：通知已读
- `UNREAD_COUNT`：未读数更新

## 5. bot 开放接口

### 5.1 鉴权头

开放接口必须携带以下请求头：

- `X-OpenAPI-App-Id`
- `X-OpenAPI-Timestamp`
- `X-OpenAPI-Nonce`
- `X-OpenAPI-Signature`

### 5.2 签名规则

签名串格式：

```text
METHOD\nPATH\nTIMESTAMP\nNONCE\nBODY_SHA256
```

签名算法：

- `BODY_SHA256 = SHA-256(request body)`
- `SIGNATURE = HMAC-SHA256(secret, canonicalString)`
- 结果使用十六进制小写表示

### 5.3 防重放

- `timestamp` 与服务端时间偏差默认不超过 300 秒
- `nonce` 默认 10 分钟内只能使用一次

### 5.4 bot 发布公告

- `POST /openapi/v1/message/tenants/{tenantId}/announcements`

请求示例：

```bash
curl -X POST 'http://localhost:8080/openapi/v1/message/tenants/1001/announcements' \
  -H 'Content-Type: application/json' \
  -H 'X-OpenAPI-App-Id: message-bot' \
  -H 'X-OpenAPI-Timestamp: 1713499200000' \
  -H 'X-OpenAPI-Nonce: 7c8f2a0d7f6e4a70' \
  -H 'X-OpenAPI-Signature: <hex-hmac-signature>' \
  -d '{
    "title": "机器人公告",
    "content": "这是 bot 自动发布的公告。"
  }'
```

### 5.5 bot 发送消息

- `POST /openapi/v1/message/tenants/{tenantId}/messages`

请求示例：

```json
{
  "title": "机器人提醒",
  "content": "您有新的待办，请及时处理。",
  "targetScope": "USER",
  "targetUserId": 10001
}
```

## 6. 兼容说明

- 现有 `/api/v1/system/notifications` 仍保留，作为老通知中心兼容入口
- 新接口为后续消息中心和 bot 接入的主入口
- 新能力落库表为 `msg_notice` 与 `msg_notice_read`

## 7. 权限说明

内部管理接口使用以下权限：

- `message:announcement:view`
- `message:announcement:write`
- `message:announcement:read`
- `message:announcement:retract`
- `message:message:view`
- `message:message:write`
- `message:message:read`
- `message:message:retract`

## 8. 返回与错误

- 所有 HTTP 接口统一返回 `ApiResponse`
- WebSocket 连接失败直接返回 HTTP 401/403
- 开放接口签名失败、时间戳过期、nonce 重复都会返回标准错误响应
