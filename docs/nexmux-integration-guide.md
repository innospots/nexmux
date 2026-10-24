# Nexmux 模型接入指南（接入方视角）

> 面向**接入方项目**的最小接入说明。内部设计/运维文档见 `nexmux-design-catalog.md` /
> `nexmux-deployment.md`。本文只描述稳定契约，不暴露内部实现。

## 1. 接入模型

Nexmux 是 OpenAI 兼容的模型网关：你的项目不直接对接各模型供应商，而是以
**AppKey + 逻辑模型别名** 调用 Nexmux，路由、配额、限流、计量由网关托管。

- **Base URL**：`http(s)://<nexmux-runtime-host>:<port>`（默认 Runtime 端口 8083）
- **认证**：`Authorization: Bearer <appKey>` 或 `X-API-Key: <appKey>`（二选一，Bearer 优先）
- **model 字段**：填**路由别名**（如 `general`），不是供应商模型 ID
- **兼容 OpenAI 官方 SDK**：直接改 `base_url` 指向 Nexmux 即可

```python
from openai import OpenAI
client = OpenAI(base_url="http://nexmux-runtime:8083/v1", api_key="<appKey>")
resp = client.chat.completions.create(model="general",
    messages=[{"role": "user", "content": "你好"}])
```

## 2. 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/models` | 列出**本 AppKey 已授权**的可用模型别名（OpenAI list 形状） |
| `POST` | `/v1/chat/completions` | 对话调用，支持非流式与 `"stream": true`（SSE） |

机器可读契约：构建产物 `META-INF/nexus-openapi/nexmux-kernel.yaml` 中含上述两个端点
（operationId：`nexmuxRuntimeChatCompletions` / `nexmuxRuntimeModels`）。

## 3. 响应与排障

- **`x-request-id` 响应头**：非流式响应均携带，与网关调用日志一一对应。
  报障时请提供该 ID + 时间点。
- **流式**：SSE chunk 原样透传上游；上游在写出字节前失败时，以一条
  `data: {"error": ...}` 事件收尾并以 `data: [DONE]` 结束。

## 4. 错误语义

错误体为 OpenAI 形状：`{"error": {"message", "type", "code"}}`。

| HTTP | 含义 | 处理建议 |
|------|------|----------|
| **4xx（上游透传）** | 你的请求被上游判定为输入类错误（如上下文超长、参数非法）；错误体来自上游 | 修正请求，**不要**无脑重试 |
| 400 `NMX010001` | 请求体缺失或缺 `model` 字段 | 检查请求 |
| 401 `NMX040002` | AppKey 缺失/无效/停用/过期 | 联系管理员 |
| 403 `NMX040003` | 该路由别名未授权给你的 AppKey | 申请授权 |
| 404 `NMX130002` | 别名无匹配的已发布路由 | 先 `GET /v1/models` 核对 |
| 429 `NMX100001` / `NMX100002` | 配额超限 / 触发限流 | **限流响应带 `Retry-After`（秒）**，按其退避；配额超限等下一周期或申请调高 |
| 502 `NMX060001` | 上游供应商故障（5xx/网络），网关侧问题 | 可重试；持续出现请报障（附 x-request-id） |
| 503 `NMX020005` | 路由当前无可用候选模型 | 报障 |

## 5. 配额与限流

- 配额（日/月窗口累计）与限流（RPM/TPM/并发）由管理员按 AppKey 配置；
  被任一关口拒绝的请求**不消耗**请求数配额。
- 当前限制（P0）：无接入方自助用量查询、AppKey 轮换旧 key 立即失效
  （轮换请安排在低峰并快速切换配置）。

## 6. 数据合规提示

网关默认不落请求/响应原文（`promptPolicy=masked`）；若管理员开启 `full` 留存，
接入方会被告知其数据处理方式。请勿在 prompt 中放置超出约定等级的敏感数据。
