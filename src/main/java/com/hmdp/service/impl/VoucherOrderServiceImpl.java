package com.hmdp.service.impl;

import java.util.Arrays;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Lua return 的结果 → Java 里的 Long
    @Resource
    private DefaultRedisScript<Long> seckillScript;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //  查询 userId ，生成 orderId
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 运行 Redis Lua 原子脚本
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
        String streamKey = RedisConstants.SECKILL_STREAM_KEY;

        Long luaResult = stringRedisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, orderKey, streamKey),
                userId.toString(),
                voucherId.toString(),
                String.valueOf(orderId));

        // 返回错误值
        if (luaResult == null) {
            return Result.fail("系统异常");
        }
        if (luaResult.intValue() == 1) {
            return Result.fail("库存不足");
        }
        if (luaResult.intValue() == 2) {
            return Result.fail("重复下单");
        }

        // Lua 校验通过 —— 返回 orderId（这里不写 DB）
        return Result.ok(orderId);
    }

}
