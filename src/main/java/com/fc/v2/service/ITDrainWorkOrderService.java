package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TDrainWorkOrder;
import com.fc.v2.model.auto.TDrainWorkOrderTrail;

/**
 * 排涝抢险作业单 Service接口（state-machine 形状：单据逐档流转，不提供增删改查全套）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITDrainWorkOrderService {

    /** 接点开单：落在接单档(0)，压站校验，返回已开单据 */
    TDrainWorkOrder open(String siteNo, String operator, String opinion);

    /** 按主键查询单据 */
    TDrainWorkOrder selectTDrainWorkOrderById(Long id);

    /** 列表查询（流转台账） */
    List<TDrainWorkOrder> selectTDrainWorkOrderList(QueryWrapper<TDrainWorkOrder> queryWrapper);

    /** 逐档流水倒盘：作废笔也翻得到，valid_flag 标明哪笔已作废 */
    List<TDrainWorkOrderTrail> trail(Long id);

    /** 推进一档：把经办人与时刻写进单里；跨档/同档二挪/办结后再动一律拒 */
    TDrainWorkOrder advance(Long id, String operator, String opinion);

    /** 撤回前一档重办：退回档旧记一律作废，不与重办新记并排；被拒抛业务异常 */
    TDrainWorkOrder rollback(Long id, String operator, String opinion);

    /** 删除单据：办结销号后不许整单抹掉，被拒抛业务异常 */
    boolean remove(Long id);
}
