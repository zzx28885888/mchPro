package com.excelai.usage;

import com.excelai.common.ApiException;
import com.excelai.plan.PlanRepository;
import com.excelai.user.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.Duration;

@Service
/**
 * 中文：按用户和月份统计任务额度。Redis 计数用于快速拦截，PostgreSQL usage_record 用于恢复与追溯。
 * English: Tracks monthly task quota per user. Redis provides a fast counter; PostgreSQL usage_record supports recovery and history.
 */
public class UsageService {
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PlanRepository plans;

    public UsageService(StringRedisTemplate r, JdbcTemplate j, UserRepository u, PlanRepository p) {
        redis = r;
        jdbc = j;
        users = u;
        plans = p;
    }

    public void consume(Long uid, Long taskId) {
        // 中文：首次触达月份键时从数据库回填，再原子递增；超额则立即回滚计数并拒绝任务。
        // English: On first access, seed the month counter from PostgreSQL, increment atomically, and decrement before rejecting over-quota work.
        String month = YearMonth.now().toString();
        var plan = plans.findByCode(users.findById(uid).planCode());
        String key = "excelai:usage:" + uid + ":" + month;
        if (Boolean.FALSE.equals(redis.hasKey(key))) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usage_record WHERE user_id=? AND usage_month=? AND usage_type='TASK'", Integer.class, uid, month);
            redis.opsForValue().setIfAbsent(key, String.valueOf(n == null ? 0 : n), Duration.ofDays(45));
        }
        Long after = redis.opsForValue().increment(key);
        if (after != null && after > plan.monthlyTasks()) {
            redis.opsForValue().decrement(key);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "monthly task limit exceeded");
        }
        jdbc.update("INSERT INTO usage_record(user_id,usage_type,usage_month,task_id) VALUES(?,?,?,?)", uid, "TASK", month, taskId);
    }
}
