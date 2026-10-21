package com.excelai.usage;

import com.excelai.common.ApiException;
import com.excelai.plan.PlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
/**
 * 中文：按用户和月份在 PostgreSQL 中原子检查并记录任务额度。
 * English: Atomically checks and records monthly task quota in PostgreSQL.
 */
public class UsageService {
    private final JdbcTemplate jdbc;
    private final PlanRepository plans;

    public UsageService(JdbcTemplate j, PlanRepository p) {
        jdbc = j;
        plans = p;
    }

    @Transactional
    public void consume(Long uid, Long taskId) {
        // 中文：锁定用户行串行化该用户的额度检查，避免并发提交超发。
        // English: Lock the user row to serialize quota checks and prevent concurrent over-allocation.
        String month = YearMonth.now().toString();
        String planCode = jdbc.queryForObject("SELECT plan_code FROM user_account WHERE id=? FOR UPDATE", String.class, uid);
        var plan = plans.findByCode(planCode);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usage_record WHERE user_id=? AND usage_month=? AND usage_type='TASK'", Integer.class, uid, month);
        if (plan == null) throw new ApiException(HttpStatus.FORBIDDEN, "user plan unavailable");
        if (count != null && count >= plan.monthlyTasks())
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "monthly task limit exceeded");
        jdbc.update("INSERT INTO usage_record(user_id,usage_type,usage_month,task_id) VALUES(?,?,?,?)", uid, "TASK", month, taskId);
    }
}
