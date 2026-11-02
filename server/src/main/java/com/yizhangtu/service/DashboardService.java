package com.yizhangtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yizhangtu.entity.PurchaseOrder;
import com.yizhangtu.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板服务：今日开单金额/单数/净重、全部未付欠款、近 7 天金额趋势。
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final PurchaseOrderMapper orderMapper;

    public DashboardService(PurchaseOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();

        // 今日开单统计
        List<PurchaseOrder> todayOrders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getOrderDate, today));
        BigDecimal todayAmount = sum(todayOrders, PurchaseOrder::getAmount);
        BigDecimal todayNetWeight = sum(todayOrders, PurchaseOrder::getNetWeight);

        // 全部未付欠款
        List<PurchaseOrder> unpaid = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getPayStatus, "UNPAID"));
        BigDecimal unpaidAmount = sum(unpaid, PurchaseOrder::getAmount);

        // 近 7 天金额趋势（无单日期补 0）
        LocalDate start = today.minusDays(6);
        List<PurchaseOrder> week = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .ge(PurchaseOrder::getOrderDate, start)
                .le(PurchaseOrder::getOrderDate, today));
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        for (PurchaseOrder o : week) {
            byDate.merge(o.getOrderDate(), nvl(o.getAmount()), BigDecimal::add);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.format(MD));
            item.put("amount", byDate.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            trend.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayAmount", todayAmount);
        data.put("todayCount", (long) todayOrders.size());
        data.put("todayNetWeight", todayNetWeight);
        data.put("unpaidAmount", unpaidAmount);
        data.put("unpaidCount", (long) unpaid.size());
        data.put("trend", trend);
        return data;
    }

    private BigDecimal sum(List<PurchaseOrder> list, java.util.function.Function<PurchaseOrder, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrder o : list) {
            total = total.add(nvl(getter.apply(o)));
        }
        return total;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
