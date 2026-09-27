package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.DrainWorkOrderException;
import com.fc.v2.mapper.auto.TDrainStationMapper;
import com.fc.v2.mapper.auto.TDrainWorkOrderMapper;
import com.fc.v2.mapper.auto.TDrainWorkOrderTrailMapper;
import com.fc.v2.model.auto.TDrainStation;
import com.fc.v2.model.auto.TDrainWorkOrder;
import com.fc.v2.model.auto.TDrainWorkOrderTrail;
import com.fc.v2.model.custom.WorkStage;
import com.fc.v2.service.ITDrainWorkOrderService;

/**
 * 排涝抢险作业单 Service业务层处理（state-machine 形状：五档单据逐档流转）
 *
 * 口径（与 t_drain_work_order / t_drain_work_order_trail 列字典一致）：
 * 五档固定 接单(0)-&gt;到场布防(1)-&gt;处置(2)-&gt;撤场(3)-&gt;办结销号(4)。
 * 一档一档往前挪，推进只 +1 不跳档；同一档不许挪第二回（有效流水里该档已有推进笔即拒）。
 * 每挪一档把经办人与时刻逐笔落进流水表，一级级倒着盘得出来。
 * 走到办结销号(4)即终态关死：不许再推进/撤回，内容不许改，整单也不许抹；
 * 确有错漏只能另开补单，不在本单上动。
 * 中途发现上一档办砸了，允许撤回前一档重办：撤回到的那一档起到撤回处这一趟往前
 * 挪过的推进旧记一律 valid_flag=0 作废（行留库倒账），绝不与重办后的新记录并排
 * 挂在同一张单上——单据 content 只镜像当前有效流水。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TDrainWorkOrderServiceImpl implements ITDrainWorkOrderService {

    /** 末环节：办结销号，走到此档即终态关死（环节取值 0..4） */
    private static final int MAX_STAGE = 4;

    /** 作业单状态 0在办 1办结确认 2退回重办 */
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_CLOSED = 1;
    private static final int STATUS_RETURNED = 2;

    /** 流水动作 1推进 2撤回 3开单 */
    private static final int ACTION_ADVANCE = 1;
    private static final int ACTION_ROLLBACK = 2;
    private static final int ACTION_OPEN = 3;

    /** 流水有效标记 1有效 */
    private static final int VALID = 1;

    /** 泵站档案状态 0在用 */
    private static final int SITE_IN_USE = 0;

    /** content 列为流水镜像 varchar(2000)，超长丢最旧的头部，真账在流水表不丢 */
    private static final int CONTENT_MAX_LEN = 1900;

    @Resource
    private TDrainWorkOrderMapper drainWorkOrderMapper;

    @Resource
    private TDrainWorkOrderTrailMapper drainWorkOrderTrailMapper;

    @Resource
    private TDrainStationMapper drainStationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TDrainWorkOrder open(String siteNo, String operator, String opinion) {
        String op = requireOperator(operator);
        String site = normalizeSiteNo(siteNo);
        TDrainStation station = this.drainStationMapper.selectOne(new QueryWrapper<TDrainStation>()
                .eq("site_no", site).eq("del_flag", 0));
        if (station == null) {
            throw new DrainWorkOrderException("泵站档案不存在，开不了单：" + site);
        }
        if (station.getStatus() != null && station.getStatus() != SITE_IN_USE) {
            throw new DrainWorkOrderException("泵站档案已停用，开不了单：" + site);
        }
        // 一座站同时只压一张没销的作业单：在办/退回重办都挡，销了号才许再开
        if (this.drainWorkOrderMapper.countOpenBySite(site) > 0) {
            throw new DrainWorkOrderException("该站压着未销作业单，先销号或另开补单，不许重复开单：" + site);
        }

        TDrainWorkOrder order = new TDrainWorkOrder();
        order.setSiteNo(site);
        order.setStage(WorkStage.TAKE.getStageNo());
        order.setStatus(STATUS_ACTIVE);
        order.setDelFlag(0);
        order.setLastAction(String.valueOf(ACTION_OPEN));
        this.drainWorkOrderMapper.insert(order);
        order.setBizNo("WX" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", order.getId() % 10000));

        // 开单即接单落名：单从接单档起，这一笔是接单事实，撤回永不作废它
        appendTrail(order, WorkStage.TAKE.getStageNo(), ACTION_OPEN, op, opinion);
        rebuildContent(order);
        this.drainWorkOrderMapper.updateById(order);
        return order;
    }

    @Override
    public TDrainWorkOrder selectTDrainWorkOrderById(Long id) {
        TDrainWorkOrder r = this.drainWorkOrderMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return null;
        }
        return r;
    }

    @Override
    public List<TDrainWorkOrder> selectTDrainWorkOrderList(QueryWrapper<TDrainWorkOrder> queryWrapper) {
        QueryWrapper<TDrainWorkOrder> wrapper = queryWrapper == null
                ? new QueryWrapper<TDrainWorkOrder>() : queryWrapper;
        // 台账只摆正常单，逻辑删除的不进台账
        wrapper.eq("del_flag", 0);
        return this.drainWorkOrderMapper.selectList(wrapper);
    }

    @Override
    public List<TDrainWorkOrderTrail> trail(Long id) {
        // seq_no 倒序：最近一笔在最前；作废笔也翻得到，valid_flag 标明哪笔已废
        return this.drainWorkOrderTrailMapper.listTrail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TDrainWorkOrder advance(Long id, String operator, String opinion) {
        TDrainWorkOrder r = lock(id);
        if (isClosed(r)) {
            throw new DrainWorkOrderException("已办结销号，终态关死，不许再挪");
        }
        String op = requireOperator(operator);
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st >= MAX_STAGE) {
            // 已在末档却不是办结态属于异常数据，不再往前推，交人工核对
            throw new DrainWorkOrderException("单子已在办结销号档，无处可推");
        }
        int next = st + 1;
        // 同一档不许挪第二回：有效流水里该档已有推进笔，说明这一档挪过了，
        // 撤回作废后才允许重办时再挪进——作废笔不算数
        for (TDrainWorkOrderTrail t : this.drainWorkOrderTrailMapper.listValid(id)) {
            if (t.getAction() != null && t.getAction() == ACTION_ADVANCE
                    && t.getStageNo() != null && t.getStageNo() == next) {
                throw new DrainWorkOrderException(WorkStage.of(next).getStageName()
                        + "档已挪过，同一档不许再挪第二回");
            }
        }

        // 只进一档，不跳档；撤回重办后重新提交也只从撤回档进一档，旧进度不随行
        appendTrail(r, next, ACTION_ADVANCE, op, opinion);
        r.setStage(next);
        // 走到办结销号才"办结确认"，中间任何一档都是"在办"
        r.setStatus(next >= MAX_STAGE ? STATUS_CLOSED : STATUS_ACTIVE);
        r.setLastAction(String.valueOf(ACTION_ADVANCE));
        rebuildContent(r);
        this.drainWorkOrderMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TDrainWorkOrder rollback(Long id, String operator, String opinion) {
        TDrainWorkOrder r = lock(id);
        if (isClosed(r)) {
            throw new DrainWorkOrderException("已办结销号，终态关死，不许撤回翻案");
        }
        String op = requireOperator(operator);
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st <= 0) {
            throw new DrainWorkOrderException("单子还在接单档，无处可撤");
        }

        int back = st - 1;
        // 撤回到的那一档起到撤回处，这一趟往前挪过的推进旧记一律作废：
        // 行留库(倒账翻得到上一档怎么办砸的)，但不再有效，也不与重办新记并排。
        this.drainWorkOrderTrailMapper.invalidateRange(id, back, st);
        // 撤回决策本身落一笔有效痕：谁在什么时刻把单子撤回到哪档、缘由是什么
        appendTrail(r, back, ACTION_ROLLBACK, op, opinion);

        // 只回一档，置"退回重办"停在该处——撤回即停，后面不许自己往下溜
        r.setStage(back);
        r.setStatus(STATUS_RETURNED);
        r.setLastAction(String.valueOf(ACTION_ROLLBACK));
        rebuildContent(r);
        this.drainWorkOrderMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TDrainWorkOrder r = lock(id);
        // 办结销号后整单不许抹（结案留档，倒账要翻得到）；其余做逻辑删除，不物理抹除
        if (isClosed(r)) {
            throw new DrainWorkOrderException("已办结销号，整单留档，不许抹除");
        }
        r.setDelFlag(1);
        return this.drainWorkOrderMapper.updateById(r) > 0;
    }

    /** 事务内锁单：不存在或已删除直接抛错，推进/撤回都先锁单再动状态，堵住并发抢挪。 */
    private TDrainWorkOrder lock(Long id) {
        if (id == null) {
            throw new DrainWorkOrderException("作业单主键为空");
        }
        TDrainWorkOrder r = this.drainWorkOrderMapper.selectByIdForUpdate(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            throw new DrainWorkOrderException("作业单不存在或已删除");
        }
        return r;
    }

    private boolean isClosed(TDrainWorkOrder r) {
        return r.getStatus() != null && r.getStatus() == STATUS_CLOSED;
    }

    private String requireOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new DrainWorkOrderException("经办人为空，不许落无名单");
        }
        return operator.trim();
    }

    private String normalizeSiteNo(String siteNo) {
        if (siteNo == null || siteNo.trim().isEmpty()) {
            throw new DrainWorkOrderException("泵站编号为空，不知道往哪座站开单");
        }
        return siteNo.trim();
    }

    /** 落一笔逐档流水，seq_no 在单据全局递增（含作废笔连续），倒查排序不断档。 */
    private void appendTrail(TDrainWorkOrder order, int stageNo, int action,
                             String operator, String opinion) {
        int total = this.drainWorkOrderTrailMapper.countByOrder(order.getId());
        TDrainWorkOrderTrail t = new TDrainWorkOrderTrail();
        t.setOrderId(order.getId());
        t.setBizNo(order.getBizNo());
        t.setStageNo(stageNo);
        t.setAction(action);
        t.setOperator(operator);
        t.setOpinion(opinion == null ? null : opinion.trim());
        t.setHappenTime(new Date());
        t.setValidFlag(VALID);
        t.setSeqNo(total + 1);
        this.drainWorkOrderTrailMapper.insert(t);
    }

    /**
     * 按当前有效流水在单据 content 上重建镜像：只照 valid_flag=1 的笔按序拼，
     * 作废旧记绝不出现在单上；超长只丢最旧头部，真账在流水表不受影响。
     */
    private void rebuildContent(TDrainWorkOrder order) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (TDrainWorkOrderTrail t : this.drainWorkOrderTrailMapper.listValid(order.getId())) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(renderLine(t, fmt));
        }
        String merged = sb.toString();
        if (merged.length() > CONTENT_MAX_LEN) {
            merged = merged.substring(merged.length() - CONTENT_MAX_LEN);
        }
        order.setContent(merged);
    }

    /** 一笔有效流水摊成台账一行：时刻 环节动作 经办人：记下的一笔。 */
    private String renderLine(TDrainWorkOrderTrail t, SimpleDateFormat fmt) {
        String head;
        if (t.getAction() != null && t.getAction() == ACTION_OPEN) {
            head = WorkStage.TAKE.getStageName() + "开单";
        } else if (t.getAction() != null && t.getAction() == ACTION_ROLLBACK) {
            head = "撤回" + WorkStage.of(t.getStageNo()).getStageName() + "重办";
        } else {
            head = "推进至" + WorkStage.of(t.getStageNo()).getStageName();
        }
        String line = fmt.format(t.getHappenTime()) + " " + head + " 经办人" + t.getOperator();
        if (t.getOpinion() != null && !t.getOpinion().isEmpty()) {
            line += "：" + t.getOpinion();
        }
        return line;
    }
}
