package com.excelai.plan;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.excelai.persistence.entity.PlanEntity;
import com.excelai.persistence.mapper.PlanMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** 中文：读取套餐限制和工具许可；数据库配置是授权决策的来源。English: Reads plan limits and tool grants; database rows are the source of authorization decisions. */
public class PlanRepository {
    private final PlanMapper planMapper;
    private final JdbcTemplate j;

    public PlanRepository(PlanMapper planMapper, JdbcTemplate j) {
        this.planMapper = planMapper;
        this.j = j;
    }

    public record Plan(String code, String name, int monthlyTasks, long maxFileSize, int maxToolCalls,
                       long toolTimeoutMs) {
    }

    public Plan findByCode(String c) {
        var entity = planMapper.selectById(c);
        return entity == null ? null : new Plan(entity.getPlanCode(), entity.getPlanName(), entity.getMonthlyTasks(),
                entity.getMaxFileSize(), entity.getMaxToolCalls(), entity.getToolTimeoutMs());
    }

    public boolean toolEnabled(String p, String t) {
        // 中文：权限表是复合键关系表；保留简单只读 SQL，避免为单次 COUNT 引入额外映射层。
        // English: This is a composite-key relation table; keep the simple read-only SQL instead of adding mapping overhead for one COUNT.
        Integer n = j.queryForObject("SELECT COUNT(*) FROM plan_tool_permission WHERE plan_code=? AND tool_name=? AND enabled=true", Integer.class, p, t);
        return n != null && n > 0;
    }
}
