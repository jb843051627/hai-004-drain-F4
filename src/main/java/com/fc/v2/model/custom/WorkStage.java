package com.fc.v2.model.custom;

/**
 * 排涝抢险作业单五档环节配置。
 * 顺序固定：接单(0) -&gt; 到场布防(1) -&gt; 处置(2) -&gt; 撤场(3) -&gt; 办结销号(4)。
 * 一档一档往前挪，不许跨档；办结销号是终态，单子到这关死。
 *
 * @author fuce
 * @date 2026-09-27
 */
public enum WorkStage {

    /** 环节0 接单：工单派到抢险队，接单落名 */
    TAKE(0, "接单"),

    /** 环节1 到场布防：到场、围蔽、警戒锥等布防到位 */
    GUARD(1, "到场布防"),

    /** 环节2 处置：现场抢险处置 */
    HANDLE(2, "处置"),

    /** 环节3 撤场：清场收锥、人员设备撤离 */
    WITHDRAW(3, "撤场"),

    /** 环节4 办结销号：销号终态，过此即关死 */
    CLOSE(4, "办结销号");

    private final int stageNo;
    private final String stageName;

    WorkStage(int stageNo, String stageName) {
        this.stageNo = stageNo;
        this.stageName = stageName;
    }

    public int getStageNo() {
        return stageNo;
    }

    public String getStageName() {
        return stageName;
    }

    public static WorkStage of(Integer stageNo) {
        if (stageNo == null) {
            throw new IllegalArgumentException("作业环节为空");
        }
        for (WorkStage s : values()) {
            if (s.stageNo == stageNo) {
                return s;
            }
        }
        throw new IllegalArgumentException("非法作业环节: " + stageNo);
    }
}
