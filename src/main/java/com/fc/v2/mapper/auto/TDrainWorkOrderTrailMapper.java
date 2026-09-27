package com.fc.v2.mapper.auto;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TDrainWorkOrderTrail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 排涝抢险作业单逐档流水数据层。
 * 每挪一档落一笔实账；单据 content 只是本表有效流水的镜像，倒盘一律翻本表实笔，
 * 不靠单据上的冗余字段凑数。
 *
 * @author fuce
 * @date 2026-09-27
 */
public interface TDrainWorkOrderTrailMapper extends BaseMapper<TDrainWorkOrderTrail> {

    /** 单据全部流水笔数（含作废笔），用于 seq_no 全局递增。 */
    @Select("SELECT COUNT(*) FROM t_drain_work_order_trail WHERE order_id = #{orderId}")
    int countByOrder(@Param("orderId") Long orderId);

    /**
     * 当前有效流水：只取 valid_flag=1，按发生顺序排，
     * 用来在单据 content 上重建镜像——作废的旧档记录绝不并排挂上来。
     */
    @Select("SELECT id, order_id, biz_no, stage_no, action, operator, opinion, happen_time, "
            + "valid_flag, seq_no, create_by, create_time, update_by, update_time, remark "
            + "FROM t_drain_work_order_trail WHERE order_id = #{orderId} AND valid_flag = 1 "
            + "ORDER BY seq_no, happen_time")
    List<TDrainWorkOrderTrail> listValid(@Param("orderId") Long orderId);

    /**
     * 台账倒盘：seq_no 倒序，作废笔也留着翻得到，
     * valid_flag 标明哪一笔是退回后作废的旧档记录。
     */
    @Select("SELECT id, order_id, biz_no, stage_no, action, operator, opinion, happen_time, "
            + "valid_flag, seq_no, create_by, create_time, update_by, update_time, remark "
            + "FROM t_drain_work_order_trail WHERE order_id = #{orderId} "
            + "ORDER BY seq_no DESC, happen_time DESC")
    List<TDrainWorkOrderTrail> listTrail(@Param("orderId") Long orderId);

    /**
     * 撤回重办：从退回档起到撤回处这一趟往前挪的推进旧记一律作废
     * （只置 valid_flag=0，行不删——倒账要翻得到这一档上一轮怎么办砸的）。
     * 开单笔(action=3)不在此列：接单是事实，永不作废；退回决策笔(action=2)保留。
     */
    @Update("UPDATE t_drain_work_order_trail SET valid_flag = 0 WHERE order_id = #{orderId} "
            + "AND action = 1 AND valid_flag = 1 "
            + "AND stage_no BETWEEN #{fromStageNo} AND #{toStageNo}")
    int invalidateRange(@Param("orderId") Long orderId,
                        @Param("fromStageNo") int fromStageNo,
                        @Param("toStageNo") int toStageNo);
}
