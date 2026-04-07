# 外部系统换取当前系统 Token 接口说明

## 目标

原系统后端在确认用户已登录后，调用当前系统身份交换接口，把用户身份安全传递给当前系统。当前系统完成验签和本地用户匹配后，直接返回当前系统自己的 `accessToken + refreshToken`。

## 接口

```http
POST /api/user/sso/exchange-token
Content-Type: application/json
```

## 请求体

```json
{
  "sourceSystem": "oa",
  "externalUserId": "550e8400-e29b-41d4-a716-446655440000",
  "phone": "13800138000",
  "timestamp": 1760000000,
  "nonce": "abc123xyz",
  "sign": "xxxx"
}
```

字段说明：

* `sourceSystem`：来源系统标识
* `externalUserId`：外部系统用户唯一 ID，建议 UUID
* `phone`：用户手机号，当前版本用于匹配本地用户
* `timestamp`：Unix 秒级时间戳
* `nonce`：随机串，每次请求必须唯一
* `sign`：签名值

## 签名规则

签名算法：

* `HMAC-SHA256`

签名原文：

```text
sourceSystem={sourceSystem}&externalUserId={externalUserId}&phone={phone}&timestamp={timestamp}&nonce={nonce}
```

示例：

```text
sourceSystem=oa&externalUserId=550e8400-e29b-41d4-a716-446655440000&phone=13800138000&timestamp=1760000000&nonce=abc123xyz
```

签名生成：

```text
sign = HMAC_SHA256_HEX(payload, sharedSecret)
```

要求：

* 只能由原系统后端生成签名
* 当前系统后端按相同规则验签

## 成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "xxx",
    "refreshToken": "yyy",
    "expiresIn": 7200,
    "refreshExpiresIn": 86400,
    "userId": 1,
    "userCode": "admin"
  }
}
```

## 失败响应

```json
{
  "code": 401001,
  "message": "手机号未匹配到本地用户"
}
```

## 错误码

* `401001`：手机号未匹配到本地用户
* `401002`：手机号匹配到多个本地用户
* `401003`：手机号格式不合法
* `401004`：签名校验失败
* `401005`：请求已过期
* `401006`：请求已失效
* `401007`：请求参数不完整
* `401008`：本地用户已禁用
* `401010`：来源系统不允许

## 安全约束

* 必须使用 HTTPS
* 必须配置共享密钥
* 必须校验时间戳与 nonce
* 建议配置来源系统 IP 白名单
* 当前版本按手机号匹配本地用户，请保证 `t_user.mobile` 数据质量
