这是黑马点评（后端）个人跟练项目
- 在 b 站黑马程序员“黑马点评”课程的基础上，跟练手敲学习，并对部分功能根据自己的理解做出一些改进
- 这是一个主要针对 Redis 的实战项目


### 技术栈
采用 JDK 1.8 + SpringBoot 2.x + Redis + RocketMQ + MyBaitPlus


### Redis + “Session”(非 JWT) 维持用户登录状态
1. 短信登录
采用 `string` 类型存储
- `key` 是“前缀 + 手机号”，`value` 是验证码

2. 维护用户登录状态
采用 `hash` 类型存储
- `key` 是“前缀 + UUID”，`field-value` 用于存储用户相关的信息
    - 这里的 UUID，用来作为用户登录标识，实际上就是 SessionID 的变种
    - 相较于 JWT，这种方式把用户信息全部存储到 Redis 当中（JWT 只需要在 Redis 存储 `(jti,userId)`）
    
### Redis 缓存

1. 商铺信息缓存
- Cache Aside ——旁路缓存
- 设置超时剔除(TTL)

商铺信息更新，造成的 **一致性问题** 解决办法：确保最终一致性（而非强一致性）
- 缓存数据设置 TTL ，并且在 update 时先写 DB 再删缓存
- 延迟双删（结合 MQ + Outbox）把脏数据概率从“必然出现”压缩到“极小概率”。让脏数据在更短时间内删除，而非等到 TTL 结束

#### 缓存三大问题与解决办法
