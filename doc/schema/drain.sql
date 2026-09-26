-- drain 城市排水防涝泵站运行与汛期调度管理 -- schema (hai-004)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_004

CREATE TABLE IF NOT EXISTS t_drain_dispatch_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '调度申请号',
  round_no int DEFAULT '1' COMMENT '申报轮次 首轮1 被驳回重报进下一轮',
  node_no int DEFAULT NULL COMMENT '当前签核节点 0值班长 1排水科 2分管领导',
  sign_mode int DEFAULT NULL COMMENT '签核模式 0或签 1会签',
  need_count int DEFAULT NULL COMMENT '本节点应签人数',
  sign_count int DEFAULT NULL COMMENT '本节点已签人数(与流水表实时重算一致,差一笔都不算过)',
  pump_scale decimal(12,2) DEFAULT NULL COMMENT '申请强排规模(m3/h) 批准后冻结',
  start_time datetime DEFAULT NULL COMMENT '申请开工时刻 批准后冻结',
  status int DEFAULT NULL COMMENT '签批单状态 0审批中 1批准强排 2已驳回(本轮终止)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_bill_round (bill_no, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泵站强排调度签批单';

-- 签批流水：哪个节点谁签的、什么时刻、批了哪一句，逐笔留痕，倒着能查。
-- 唯一索引 uk_bill_round_node_user：同一轮同一节点同一人只许落一笔，防重复签/替人凑数。
CREATE TABLE IF NOT EXISTS t_drain_dispatch_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '签批单主键 t_drain_dispatch_bill.id',
  bill_no varchar(64) DEFAULT NULL COMMENT '调度申请号(冗余便于台账倒查)',
  round_no int NOT NULL COMMENT '签批轮次',
  node_no int NOT NULL COMMENT '签核节点 0值班长 1排水科 2分管领导',
  sign_mode int DEFAULT NULL COMMENT '签核模式快照 0或签 1会签',
  approver varchar(64) NOT NULL COMMENT '签批人登录名',
  action int NOT NULL COMMENT '动作 1批准 2驳回',
  comment varchar(500) DEFAULT NULL COMMENT '批了哪一句(签批意见)',
  sign_time datetime NOT NULL COMMENT '签批时刻',
  seq_no int NOT NULL COMMENT '单据全局时序(倒查排序用)',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_round_node_user (bill_id, round_no, node_no, approver),
  KEY idx_bill_round (bill_id, round_no, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泵站强排调度签批流水';

CREATE TABLE IF NOT EXISTS t_drain_level_std (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '判线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判线名称',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '一档起排水位上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档起排水位上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档起排水位上限',
  eff_start datetime DEFAULT NULL COMMENT '生效起始时刻',
  eff_end datetime DEFAULT NULL COMMENT '失效截止时刻(不含)',
  priority int DEFAULT NULL COMMENT '取优序号(数值越大越优先)',
  status int DEFAULT NULL COMMENT '判线状态 0现行 1已作废',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泵站起排判线标准';

CREATE TABLE IF NOT EXISTS t_drain_meter_batch (
  batch_no varchar(64) NOT NULL COMMENT '汇交批次号(幂等凭据)',
  total_count int DEFAULT NULL COMMENT '报文原行数',
  ok_count int DEFAULT NULL COMMENT '已对平行数(按头一笔回话)',
  fail_count int DEFAULT NULL COMMENT '已剔除行数(按头一笔回话)',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间(头一笔落库时刻)',
  PRIMARY KEY (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='远传计量成批汇交批次头(同批重交幂等闸门)';

CREATE TABLE IF NOT EXISTS t_drain_meter_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '汇交批次号(断传人工补录行此列为空)',
  row_no int DEFAULT NULL COMMENT '远传报文行次',
  item_code varchar(64) DEFAULT NULL COMMENT '计量点编码',
  qty decimal(12,2) DEFAULT NULL COMMENT '本期远传读数',
  status int DEFAULT NULL COMMENT '行状态 0待对账 1已对平 2已剔除',
  source int DEFAULT '0' COMMENT '读数来源 0远传 1断传人工补录',
  read_time datetime DEFAULT NULL COMMENT '抄表时刻 仅断传人工补录照值守员实抄时间填(远传行留空,按落库时间归天,不许拿补录时刻顶替)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_batch_row (batch_no, row_no, id),
  KEY idx_item_day (item_code, source, read_time),
  -- 同一表计同一抄表时刻、同一来源只许一笔：断传补录防重手，远传防同刻重行（NULL 不冲突）
  UNIQUE KEY uk_item_source_read (item_code, source, read_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泵站远传计量明细行';

CREATE TABLE IF NOT EXISTS t_drain_patrol_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '巡检任务编号',
  site_no varchar(64) DEFAULT NULL COMMENT '到点该巡泵站编号(空=坏行,跳过交人工核)',
  due_at datetime DEFAULT NULL COMMENT '到期时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '应巡点位数',
  status int DEFAULT NULL COMMENT '状态 0待派 1已派 2无法派单',
  assignee varchar(64) DEFAULT NULL COMMENT '受派当班值守员(派定写死,不两人轮替)',
  dispatched_at datetime DEFAULT NULL COMMENT '派定时刻(落记号)',
  shift_id bigint DEFAULT NULL COMMENT '派定时所属值守排班(t_drain_duty_shift.id,一人一时段一站的判重锚点)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_site_status (site_no, status, due_at),
  -- 并发两道闸：待派行 shift_id/assignee 为 NULL（MySQL 唯一键里 NULL 不冲突），
  -- 一旦派定，同一值守时段(shift_id)同一人只许落一条已派、同一座站只许落一条已派——
  -- 两个轮次并发抢派时，库唯一键兜底，绝不出一人两站或一站两人。
  UNIQUE KEY uk_shift_assignee (shift_id, assignee),
  UNIQUE KEY uk_shift_site (shift_id, site_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汛期值守巡检任务条目';

-- 汛期值守排班：系统照这张表自己派。时段半开区间[start_at,end_at)，跨天夜班照实排；
-- 某时刻没有任何一条覆盖它，就是排班空档——空档不派不吭声，条目原样留着。
CREATE TABLE IF NOT EXISTS t_drain_duty_shift (
  id bigint NOT NULL COMMENT '主键',
  shift_name varchar(64) DEFAULT NULL COMMENT '班次名(白班/夜班)',
  start_at datetime NOT NULL COMMENT '值守时段起(含)',
  end_at datetime NOT NULL COMMENT '值守时段止(不含,跨天照排)',
  duty_user varchar(64) NOT NULL COMMENT '当班值守员登录名',
  status int DEFAULT '0' COMMENT '排班状态 0有效 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_span (start_at, end_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='汛期泵站值守排班表';

CREATE TABLE IF NOT EXISTS t_drain_run_record (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '运行单号',
  site_id int DEFAULT NULL COMMENT '受录泵站',
  site_no varchar(64) DEFAULT NULL COMMENT '泵站编号',
  qty decimal(12,2) DEFAULT NULL COMMENT '机组实际运行时长(小时)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '电耗核算费(元)',
  grade_level int DEFAULT NULL COMMENT '运行评定档',
  status int DEFAULT NULL COMMENT '状态 0待复核 1已复核 2已封存',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='机组运行记录单';

CREATE TABLE IF NOT EXISTS t_drain_station (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '泵站编号',
  site_name varchar(128) DEFAULT NULL COMMENT '泵站名称',
  site_type varchar(32) DEFAULT NULL COMMENT '泵站类别',
  road_name varchar(128) DEFAULT NULL COMMENT '管辖片区',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '本泵站考核一档上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '本泵站考核二档上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '本泵站考核三档上限',
  status int DEFAULT NULL COMMENT '档案状态 0在用 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在册泵站档案';

CREATE TABLE IF NOT EXISTS t_drain_work_order (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '作业单号',
  site_no varchar(64) DEFAULT NULL COMMENT '修单所压泵站编号(在办/退回单挂着的站,巡检自动派单本轮往后推)',
  stage int DEFAULT NULL COMMENT '当前环节 0..4',
  status int DEFAULT NULL COMMENT '作业单状态 0在办 1办结确认 2退回重办',
  content varchar(255) DEFAULT NULL COMMENT '推进记录',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次推进动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_site_open (site_no, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排涝抢险作业单';
