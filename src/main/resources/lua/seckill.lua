---@diagnostic disable: undefined-global

-- KEYS[1] = 库存 key
-- KEYS[2] = 已下单用户 set key
-- KEYS[3] = Stream key
-- ARGV[1] = userId
-- ARGV[2] = voucherId
-- ARGV[3] = orderId

-- 1. 判断库存
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock <= 0 then
    return 1   -- 库存不足
end

-- 2. 判断是否已下单
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2   -- 重复下单
end

-- 3. 扣库存 + 记录用户
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

-- 4. 写入 Stream
redis.call('XADD', KEYS[3], '*',
    'orderId', ARGV[3],
    'userId', ARGV[1],
    'voucherId', ARGV[2]
)

return 0       -- 成功

-- 0 成功；1 库存不足；2 重复下单
