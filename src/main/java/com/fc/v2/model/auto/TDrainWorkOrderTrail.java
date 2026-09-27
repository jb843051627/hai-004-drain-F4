package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 排涝抢险作业单逐档流水对象 t_drain_work_order_trail
 * 单子每挪一档就落一笔：谁经办、什么时刻、进了哪一档、记了什么，一级级倒着盘得出来。
 * 被退回的那一档旧笔置 valid_flag=0 作废留库，不与重办新记并排挂在单上。
 *
 * @author fuce
 * @date 2026-09-27
 */
@TableName("t_drain_work_order_trail")
@ApiModel(value = "TDrainWorkOrderTrail", description = "排涝抢险作业单逐档流水")
public class TDrainWorkOrderTrail implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 作业单主键 t_drain_work_order.id */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "作业单主键")
    private Long orderId;

    /** 作业单号(冗余便于台账倒查) */
    @TableField("biz_no")
    @ApiModelProperty(value = "作业单号")
    private String bizNo;

    /** 进入的环节 0接单 1到场布防 2处置 3撤场 4办结销号 */
    @TableField("stage_no")
    @ApiModelProperty(value = "进入的环节 0接单 1到场布防 2处置 3撤场 4办结销号")
    private Integer stageNo;

    /** 动作 1推进 2退回 3开单 */
    @TableField("action")
    @ApiModelProperty(value = "动作 1推进 2退回 3开单")
    private Integer action;

    /** 经办人登录名 */
    @TableField("operator")
    @ApiModelProperty(value = "经办人登录名")
    private String operator;

    /** 本档记下的一笔(处置措施/退回缘由) */
    @TableField("opinion")
    @ApiModelProperty(value = "本档记下的一笔")
    private String opinion;

    /** 经办时刻 */
    @TableField("happen_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "经办时刻")
    private Date happenTime;

    /** 是否有效 1有效 0已作废(被退回的那一档旧记) */
    @TableField("valid_flag")
    @ApiModelProperty(value = "是否有效 1有效 0已作废")
    private Integer validFlag;

    /** 单据全局时序(倒查排序用) */
    @TableField("seq_no")
    @ApiModelProperty(value = "单据全局时序")
    private Integer seqNo;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getStageNo() {
        return stageNo;
    }

    public void setStageNo(Integer stageNo) {
        this.stageNo = stageNo;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Date getHappenTime() {
        return happenTime;
    }

    public void setHappenTime(Date happenTime) {
        this.happenTime = happenTime;
    }

    public Integer getValidFlag() {
        return validFlag;
    }

    public void setValidFlag(Integer validFlag) {
        this.validFlag = validFlag;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
