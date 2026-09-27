package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.DrainWorkOrderException;
import com.fc.v2.mapper.auto.TDrainStationMapper;
import com.fc.v2.mapper.auto.TDrainWorkOrderMapper;
import com.fc.v2.mapper.auto.TDrainWorkOrderTrailMapper;
import com.fc.v2.model.auto.TDrainStation;
import com.fc.v2.model.auto.TDrainWorkOrder;
import com.fc.v2.model.auto.TDrainWorkOrderTrail;

/**
 * 排涝抢险作业单五档流转红线测试（内存库模拟两张表，口径与生产一致）：
 * 接单/到场布防/处置/撤场/办结销号逐档走，经办人时刻逐笔落痕，
 * 同档不二挪、退回档旧记作废不并排、办结销号关死。
 *
 * @author fuce
 * @date 2026-09-27
 */
class TDrainWorkOrderServiceImplTest {

    /** 与实体字典对齐：0在办 1办结确认 2退回重办 */
    private static final int ACTIVE = 0;
    private static final int CLOSED = 1;
    private static final int RETURNED = 2;
    private static final int FINAL_STAGE = 4;

    private static final int ACT_ADVANCE = 1;
    private static final int ACT_ROLLBACK = 2;
    private static final int ACT_OPEN = 3;

    private TDrainWorkOrderServiceImpl service;
    private final Map<Long, TDrainWorkOrder> orders = new HashMap<>();
    private final Map<Long, TDrainWorkOrderTrail> trails = new HashMap<>();
    private final Map<String, TDrainStation> stations = new HashMap<>();
    private final AtomicLong orderSeq = new AtomicLong(2000);
    private final AtomicLong trailSeq = new AtomicLong(9000);

    @BeforeEach
    void setUp() {
        orders.clear();
        trails.clear();
        stations.clear();
        orderSeq.set(2000);
        trailSeq.set(9000);
        seedStation("PS00", 0);
        seedStation("PS01", 1);

        TDrainWorkOrderMapper orderMapper = mock(TDrainWorkOrderMapper.class);
        TDrainWorkOrderTrailMapper trailMapper = mock(TDrainWorkOrderTrailMapper.class);
        TDrainStationMapper stationMapper = mock(TDrainStationMapper.class);

        when(orderMapper.insert(any())).thenAnswer(inv -> {
            TDrainWorkOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(orderSeq.incrementAndGet());
            }
            orders.put(o.getId(), o);
            return 1;
        });
        when(orderMapper.updateById(any())).thenAnswer(inv -> {
            orders.put(inv.<TDrainWorkOrder>getArgument(0).getId(), inv.getArgument(0));
            return 1;
        });
        when(orderMapper.selectById(anyLong())).thenAnswer(inv -> visible(inv.getArgument(0)));
        when(orderMapper.selectByIdForUpdate(anyLong())).thenAnswer(inv -> visible(inv.getArgument(0)));
        when(orderMapper.selectList(any())).thenAnswer(inv -> {
            List<TDrainWorkOrder> hit = new ArrayList<>();
            for (TDrainWorkOrder o : orders.values()) {
                if (o.getDelFlag() == null || o.getDelFlag() == 0) {
                    hit.add(o);
                }
            }
            return hit;
        });
        when(orderMapper.countOpenBySite(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            String siteNo = inv.getArgument(0);
            int n = 0;
            for (TDrainWorkOrder o : orders.values()) {
                if (siteNo.equals(o.getSiteNo()) && (o.getDelFlag() == null || o.getDelFlag() == 0)
                        && o.getStatus() != null && (o.getStatus() == ACTIVE || o.getStatus() == RETURNED)) {
                    n++;
                }
            }
            return n;
        });

        when(trailMapper.insert(any())).thenAnswer(inv -> {
            TDrainWorkOrderTrail t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(trailSeq.incrementAndGet());
            }
            trails.put(t.getId(), t);
            return 1;
        });
        when(trailMapper.countByOrder(anyLong())).thenAnswer(inv -> {
            Long oid = inv.getArgument(0);
            int n = 0;
            for (TDrainWorkOrderTrail t : trails.values()) {
                if (oid.equals(t.getOrderId())) {
                    n++;
                }
            }
            return n;
        });
        when(trailMapper.listValid(anyLong())).thenAnswer(inv -> listTrails(inv.getArgument(0), true));
        when(trailMapper.listTrail(anyLong())).thenAnswer(inv -> listTrails(inv.getArgument(0), false));
        when(trailMapper.invalidateRange(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> {
            Long oid = inv.getArgument(0);
            int from = inv.getArgument(1);
            int to = inv.getArgument(2);
            int n = 0;
            for (TDrainWorkOrderTrail t : trails.values()) {
                if (oid.equals(t.getOrderId()) && t.getAction() != null && t.getAction() == ACT_ADVANCE
                        && t.getValidFlag() != null && t.getValidFlag() == 1
                        && t.getStageNo() != null && t.getStageNo() >= from && t.getStageNo() <= to) {
                    t.setValidFlag(0);
                    n++;
                }
            }
            return n;
        });

        when(stationMapper.selectOne(any())).thenAnswer(inv -> {
            // 测试里只按 site_no+del_flag 建档；先渲染 SQL 段把懒加载的绑定参数物化
            QueryWrapper<TDrainStation> w = inv.getArgument(0);
            w.getSqlSegment();
            String siteNo = (String) w.getParamNameValuePairs().values().stream()
                    .filter(v -> v instanceof String && stations.containsKey(v))
                    .findFirst().orElse(null);
            if (siteNo == null) {
                return null;
            }
            TDrainStation s = stations.get(siteNo);
            return (s.getDelFlag() == null || s.getDelFlag() == 0) ? s : null;
        });

        service = new TDrainWorkOrderServiceImpl();
        ReflectionTestUtils.setField(service, "drainWorkOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "drainWorkOrderTrailMapper", trailMapper);
        ReflectionTestUtils.setField(service, "drainStationMapper", stationMapper);
    }

    private TDrainWorkOrder visible(Long id) {
        TDrainWorkOrder o = orders.get(id);
        return (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) ? null : o;
    }

    private List<TDrainWorkOrderTrail> listTrails(Long orderId, boolean validOnly) {
        List<TDrainWorkOrderTrail> hit = new ArrayList<>();
        for (TDrainWorkOrderTrail t : trails.values()) {
            if (orderId.equals(t.getOrderId()) && (!validOnly || t.getValidFlag() == 1)) {
                hit.add(t);
            }
        }
        hit.sort((a, b) -> {
            int bySeq = Integer.compare(a.getSeqNo(), b.getSeqNo());
            return validOnly ? bySeq : -bySeq;
        });
        return hit;
    }

    private void seedStation(String siteNo, int status) {
        TDrainStation s = new TDrainStation();
        s.setId((long) (siteNo.hashCode() & 0x7fffffff));
        s.setSiteNo(siteNo);
        s.setSiteName(siteNo + "泵站");
        s.setStatus(status);
        s.setDelFlag(0);
        stations.put(siteNo, s);
    }

    private TDrainWorkOrder seedOrder(long id, String siteNo, int stage, int status) {
        TDrainWorkOrder o = new TDrainWorkOrder();
        o.setId(id);
        o.setBizNo("WX-" + id);
        o.setSiteNo(siteNo);
        o.setStage(stage);
        o.setStatus(status);
        o.setDelFlag(0);
        orders.put(id, o);
        return o;
    }

    private void seedTrail(long id, long orderId, String bizNo, int stage, int action,
                           String operator, int validFlag, int seq) {
        TDrainWorkOrderTrail t = new TDrainWorkOrderTrail();
        t.setId(id);
        t.setOrderId(orderId);
        t.setBizNo(bizNo);
        t.setStageNo(stage);
        t.setAction(action);
        t.setOperator(operator);
        t.setHappenTime(new java.util.Date());
        t.setValidFlag(validFlag);
        t.setSeqNo(seq);
        trails.put(id, t);
    }

    private TDrainWorkOrder openOn(String siteNo) {
        return service.open(siteNo, "captain_li", "接抢险工单");
    }

    // 1. 开单即落接单档，经办人写进单里
    @Test
    void open_landsAtTakeStageWithOperator() {
        TDrainWorkOrder o = openOn("PS00");
        assertEquals(0, o.getStage().intValue());
        assertEquals(ACTIVE, o.getStatus().intValue());
        assertNotNull(o.getBizNo());
        assertTrue(o.getContent().contains("接单开单"));
        assertTrue(o.getContent().contains("经办人captain_li"));
        TDrainWorkOrderTrail t = service.trail(o.getId()).get(0);
        assertEquals(ACT_OPEN, t.getAction().intValue());
        assertEquals(0, t.getStageNo().intValue());
        assertNotNull(t.getHappenTime());
    }

    // 2. 五档逐档推进：接单→到场布防→处置→撤场→办结销号，一次只挪一档；不满五档不办结
    @Test
    void advance_oneStageAtATime_closedOnlyAtClose() {
        TDrainWorkOrder o = openOn("PS00");
        long id = o.getId();
        String[] names = {"到场布防", "处置", "撤场"};
        int seq = 2;
        for (int i = 0; i < names.length; i++) {
            TDrainWorkOrder cur = service.advance(id, "captain_li", names[i] + "落位");
            assertEquals(i + 1, cur.getStage().intValue(), "一次只进一档");
            assertEquals(ACTIVE, cur.getStatus().intValue(), "中间档一律在办，不许提前办结");
            assertTrue(cur.getContent().contains(names[i]), "推进记录写出档名");
            seq++;
        }
        TDrainWorkOrder done = service.advance(id, "boss_zhang", "现场恢复，销号");
        assertEquals(FINAL_STAGE, done.getStage().intValue());
        assertEquals(CLOSED, done.getStatus().intValue(), "走到办结销号才办结确认");
        assertTrue(done.getContent().contains("办结销号"));
        assertTrue(done.getContent().contains("经办人boss_zhang"));
    }

    // 3. 同一档不许挪第二回：要进的目标档已有有效推进笔即拒
    //    （正常撤回已把旧笔作废，此闸专堵并发抢挪/迁移来的重复脏数据）
    @Test
    void advance_sameStageTwice_rejected() {
        TDrainWorkOrder o = seedOrder(2101, "PS00", 2, ACTIVE);
        seedTrail(9101, o.getId(), o.getBizNo(), 0, ACT_OPEN, "captain_li", 1, 1);
        seedTrail(9102, o.getId(), o.getBizNo(), 1, ACT_ADVANCE, "captain_li", 1, 2);
        seedTrail(9103, o.getId(), o.getBizNo(), 2, ACT_ADVANCE, "captain_li", 1, 3);
        // 并发抢挪/迁移脏数据：单还停在处置档，撤场档却已挂着一笔有效挪单
        seedTrail(9104, o.getId(), o.getBizNo(), 3, ACT_ADVANCE, "captain_li", 1, 4);

        DrainWorkOrderException ex = assertThrows(DrainWorkOrderException.class,
                () -> service.advance(o.getId(), "captain_li", "又挪一回"));
        assertTrue(ex.getMessage().contains("撤场"), "目标档撤场已有有效推进笔");
        assertEquals(2, orders.get(o.getId()).getStage().intValue(), "被拒后档不挪");
    }

    // 4. 办结销号即终态关死：推进/撤回/抹除一律被拒
    @Test
    void close_sealsEverything() {
        TDrainWorkOrder o = seedOrder(2102, "PS00", FINAL_STAGE, CLOSED);
        int before = trails.size();

        assertThrows(DrainWorkOrderException.class, () -> service.advance(o.getId(), "a", "再推"));
        assertThrows(DrainWorkOrderException.class, () -> service.rollback(o.getId(), "a", "翻案"));
        assertThrows(DrainWorkOrderException.class, () -> service.remove(o.getId()));
        assertEquals(FINAL_STAGE, orders.get(o.getId()).getStage().intValue(), "状态原样不动");
        assertEquals(CLOSED, orders.get(o.getId()).getStatus().intValue());
        assertEquals(0, orders.get(o.getId()).getDelFlag().intValue(), "销号单不许抹");
        assertEquals(before, trails.size(), "关死后不许添任何流水");
    }

    // 5. 撤回只回一档、退回重办停住；撤回到的那一档起到撤回处的推进旧记一律作废，
    //    单据镜像里只剩开单/更早档与撤回痕，作废旧记不并排
    @Test
    void rollback_oneStage_voidedRecordsNotSideBySide() {
        TDrainWorkOrder o = openOn("PS00");
        long id = o.getId();
        service.advance(id, "captain_li", "布防");       // ->1
        service.advance(id, "captain_li", "处置措施A");  // ->2
        service.advance(id, "captain_li", "撤场中");     // ->3

        TDrainWorkOrder back = service.rollback(id, "boss_zhang", "处置办砸了，撤回复置");
        assertEquals(2, back.getStage().intValue(), "只回一档，不一把撸回起点");
        assertEquals(RETURNED, back.getStatus().intValue(), "挂退回重办停住");

        String content = back.getContent();
        assertTrue(content.contains("接单开单"));
        assertTrue(content.contains("到场布防"));
        assertTrue(content.contains("撤回处置重办"), "撤回决策留痕");
        assertTrue(content.contains("经办人boss_zhang"));
        assertFalse(content.contains("处置措施A"), "作废的处置旧记不许并排挂在单上");
        assertFalse(content.contains("撤场中"), "作废的撤场旧记不许并排挂在单上");

        // 流水台账倒盘：作废笔仍翻得到，valid_flag=0 标明
        List<TDrainWorkOrderTrail> all = service.trail(id);
        long voided = all.stream().filter(t -> t.getValidFlag() == 0).count();
        assertEquals(2, voided, "处置、撤场两笔推进旧记作废留库");
        TDrainWorkOrderTrail rollbackTrail = all.stream()
                .filter(t -> t.getAction() == ACT_ROLLBACK).findFirst().orElse(null);
        assertNotNull(rollbackTrail);
        assertEquals(1, rollbackTrail.getValidFlag().intValue(), "撤回决策笔本身有效");
    }

    // 6. 撤回后重办：只从撤回档进一档，旧进度不随行；重办这趟是新挪单，旧笔作废不并排
    @Test
    void resubmit_afterRollback_advancesJustOneWithFreshRecord() {
        TDrainWorkOrder o = openOn("PS00");
        long id = o.getId();
        service.advance(id, "li", "布防");
        service.advance(id, "li", "处置A");
        service.advance(id, "li", "第一次撤场");
        service.rollback(id, "boss", "处置存疑撤回");

        TDrainWorkOrder again = service.advance(id, "li", "处置整改完毕，重走撤场");
        assertEquals(3, again.getStage().intValue(), "重办只从撤回档(2)进一档到3");
        assertEquals(ACTIVE, again.getStatus().intValue(), "重新推进恢复在办");
        assertFalse(again.getContent().contains("处置A"), "旧记已作废不挂单");
        assertFalse(again.getContent().contains("第一次撤场"), "作废的撤场旧挪不挂单");
        assertTrue(again.getContent().contains("处置整改完毕，重走撤场"), "重办这趟新记上账");

        // 撤场档只认重办后的这一趟有效挪单，旧趟作废不并排
        long validWithdraw = service.trail(id).stream()
                .filter(t -> t.getValidFlag() == 1 && t.getStageNo() == 3 && t.getAction() == ACT_ADVANCE)
                .count();
        assertEquals(1, validWithdraw);
        long voided = service.trail(id).stream()
                .filter(t -> t.getValidFlag() == 0 && t.getAction() == ACT_ADVANCE).count();
        assertEquals(2, voided, "处置、撤场两趟旧挪都作废留库");

        TDrainWorkOrder done = service.advance(id, "boss", "销号");
        assertEquals(FINAL_STAGE, done.getStage().intValue());
        assertEquals(CLOSED, done.getStatus().intValue());
    }

    // 7. 接单档无处可撤
    @Test
    void rollback_atTakeStage_rejected() {
        TDrainWorkOrder o = openOn("PS00");
        assertThrows(DrainWorkOrderException.class,
                () -> service.rollback(o.getId(), "a", "没东西可撤"));
        assertEquals(0, orders.get(o.getId()).getStage().intValue());
        assertEquals(ACTIVE, orders.get(o.getId()).getStatus().intValue());
    }

    // 8. 开单闸：空站、停用站、压着未销单都不许开
    @Test
    void open_guards() {
        assertThrows(DrainWorkOrderException.class, () -> service.open("  ", "li", null));
        assertThrows(DrainWorkOrderException.class, () -> service.open("PS99", "li", null),
                "档案不存在不许开");
        assertThrows(DrainWorkOrderException.class, () -> service.open("PS01", "li", null),
                "停用站不许开");
        openOn("PS00");
        assertThrows(DrainWorkOrderException.class, () -> service.open("PS00", "li", null),
                "一站压着未销单不许重复开");
        assertThrows(DrainWorkOrderException.class, () -> service.open("PS00", " ", null),
                "经办人为空不许落无名单");
    }

    // 9. 在办单可逻辑删除；销号单不可抹；不存在的单各操作抛错
    @Test
    void active_logicalRemove_closedKept_missingRejected() {
        TDrainWorkOrder o = seedOrder(2103, "PS00", 1, ACTIVE);
        assertTrue(service.remove(o.getId()));
        assertEquals(1, orders.get(o.getId()).getDelFlag().intValue(), "只打删除标记，留库可倒查");
        assertNull(service.selectTDrainWorkOrderById(o.getId()));
        assertTrue(service.selectTDrainWorkOrderList(new QueryWrapper<>()).stream()
                .noneMatch(x -> x.getId().equals(o.getId())), "删除单不进台账");

        assertThrows(DrainWorkOrderException.class, () -> service.advance(404L, "a", null));
        assertThrows(DrainWorkOrderException.class, () -> service.rollback(404L, "a", null));
        assertThrows(DrainWorkOrderException.class, () -> service.remove(404L));
    }

    // 10. 每挪一档 seq 连续递增，倒序台账最近一笔在最前
    @Test
    void trail_seqContinuousAndReverseOrdered() {
        TDrainWorkOrder o = openOn("PS00");
        long id = o.getId();
        service.advance(id, "li", "布防");
        service.rollback(id, "boss", "退回");
        List<TDrainWorkOrderTrail> all = service.trail(id);
        assertEquals(3, all.size());
        assertEquals(3, all.get(0).getSeqNo().intValue(), "撤回笔最近在最前");
        assertEquals(2, all.get(1).getSeqNo().intValue());
        assertEquals(1, all.get(2).getSeqNo().intValue());
    }
}
