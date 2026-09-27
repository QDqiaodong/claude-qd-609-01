-- 射箭馆（弓箭俱乐部）演示数据：建表 + 初始化数据
-- 回合的箭支走 round_arrow 这张有序集合连接表，shot_index 从 0 连续递增。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS lanes (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  lane_no       VARCHAR(16)  NOT NULL COMMENT '道号',
  distance      INT          NOT NULL COMMENT '距离（米）：10/18/30/50',
  target_type   VARCHAR(32)  NOT NULL COMMENT '箭靶类型',
  status        VARCHAR(16)  NOT NULL COMMENT 'OPEN 开放 / OCCUPIED 占用 / MAINTENANCE 维护 / LOCKED 安全锁定',
  hourly_price  DECIMAL(10,2) NOT NULL COMMENT '每小时单价',
  occupant_id   BIGINT       NULL COMMENT '占用会员',
  occupant_name VARCHAR(32)  NULL,
  opened_at     DATETIME     NULL COMMENT '开台时间',
  planned_hours INT          NULL COMMENT '预计时长（小时）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lane_no (lane_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS members (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  card_no       VARCHAR(24)  NOT NULL COMMENT '会员卡号',
  name          VARCHAR(32)  NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  level         VARCHAR(16)  NOT NULL COMMENT 'NORMAL 普通 / SILVER 银 / GOLD 金',
  balance       DECIMAL(10,2) NOT NULL COMMENT '余额',
  register_date DATE         NOT NULL COMMENT '注册日期',
  total_spend   DECIMAL(12,2) NOT NULL COMMENT '累计消费',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rounds (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  round_no      VARCHAR(24)  NOT NULL COMMENT '回合号',
  member_id     BIGINT       NOT NULL,
  lane_id       BIGINT       NOT NULL,
  start_time    DATETIME     NOT NULL COMMENT '开始时间',
  arrow_count   INT          NOT NULL COMMENT '本组箭支数：6 或 12',
  total_score   INT          NOT NULL COMMENT '总分',
  average_score DECIMAL(5,2) NOT NULL COMMENT '平均环',
  personal_best TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否个人最好成绩',
  status        VARCHAR(16)  NOT NULL COMMENT 'ONGOING 进行中 / PAUSED 暂停（安全联锁） / SUBMITTED 已提交',
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_no (round_no),
  KEY idx_round_member (member_id),
  KEY idx_round_lane (lane_id),
  CONSTRAINT fk_round_member FOREIGN KEY (member_id) REFERENCES members (id),
  CONSTRAINT fk_round_lane   FOREIGN KEY (lane_id)   REFERENCES lanes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS arrow_score (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  ring       VARCHAR(4)  NOT NULL COMMENT '环数标记：X/10/9/.../1/M',
  ring_value INT         NOT NULL COMMENT '计分环值：X 与 10 均为 10，M 为 0',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Round 到 ArrowScore 的有序集合连接表：shot_index 由 Hibernate 的 @OrderColumn 维护，
-- 保证列表顺序 = 射箭顺序（第 0 支、第 1 支 ...）。
CREATE TABLE IF NOT EXISTS round_arrow (
  round_id   BIGINT NOT NULL,
  arrow_id   BIGINT NOT NULL,
  shot_index INT    NOT NULL,
  PRIMARY KEY (round_id, shot_index),
  UNIQUE KEY uk_round_arrow_arrow (arrow_id),
  CONSTRAINT fk_ra_round FOREIGN KEY (round_id) REFERENCES rounds (id),
  CONSTRAINT fk_ra_arrow FOREIGN KEY (arrow_id) REFERENCES arrow_score (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS courses (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  course_name VARCHAR(64) NOT NULL COMMENT '课程名',
  coach       VARCHAR(32) NOT NULL COMMENT '教练',
  level       VARCHAR(16) NOT NULL COMMENT 'BASIC 初级 / ADVANCED 进阶 / COMPETITION 竞技',
  class_time  DATETIME    NOT NULL COMMENT '上课时间',
  capacity    INT         NOT NULL COMMENT '人数上限',
  enrolled    INT         NOT NULL DEFAULT 0 COMMENT '已报名',
  venue       VARCHAR(32) NOT NULL COMMENT '教室/场地',
  PRIMARY KEY (id),
  KEY idx_course_time (class_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_enroll (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  course_id   BIGINT   NOT NULL,
  member_id   BIGINT   NOT NULL,
  enroll_time DATETIME NOT NULL COMMENT '报名时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_enroll (course_id, member_id),
  CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES courses (id),
  CONSTRAINT fk_enroll_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS equipment (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  equip_code  VARCHAR(24)  NOT NULL COMMENT '器材编号',
  type        VARCHAR(16)  NOT NULL COMMENT 'RECURVE 反曲弓 / COMPOUND 复合弓 / TRADITIONAL 传统弓 / GEAR 护具 / ARROW 箭支',
  brand       VARCHAR(32)  NOT NULL COMMENT '品牌',
  rent_price  DECIMAL(10,2) NOT NULL COMMENT '租金（元/小时）',
  status      VARCHAR(16)  NOT NULL COMMENT 'INSTOCK 在库 / RENTED 租出 / REPAIR 维修 / LOCKED 安全锁定',
  renter_id   BIGINT       NULL,
  renter_name VARCHAR(32)  NULL,
  rented_at   DATETIME     NULL COMMENT '租出时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_equip_code (equip_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================= 安全停射联锁台 =================
-- 停射事件主表：值班经理创建后立即联锁箭道/回合/器材；
-- 状态机 PENDING 待处置 → REVIEWING 分项复核 → CLEARED 待放行 → RELEASED 已放行；
-- 任一复核不通过退回 PENDING（继续锁定），重新发起复核时 review_round +1。
CREATE TABLE IF NOT EXISTS safety_event (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  event_no     VARCHAR(24)  NOT NULL COMMENT '事件编号',
  reason       VARCHAR(24)  NOT NULL COMMENT '原因类别：LANE_DEVICE 箭道设备异常 / PERSON_INTRUSION 人员闯入射线 / EQUIP_SUSPECT 疑似器材故障 / OTHER 其他',
  severity     VARCHAR(16)  NOT NULL COMMENT '严重级别：NOTICE 一般 / MAJOR 严重 / CRITICAL 紧急',
  description  VARCHAR(500) NOT NULL COMMENT '现场说明',
  status       VARCHAR(16)  NOT NULL COMMENT 'PENDING / REVIEWING / CLEARED / RELEASED',
  review_round INT          NOT NULL DEFAULT 1 COMMENT '复核轮次',
  created_by   VARCHAR(32)  NOT NULL COMMENT '创建人（值班经理）',
  created_at   DATETIME     NOT NULL,
  released_by  VARCHAR(32)  NULL COMMENT '放行执行人（值班经理）',
  released_at  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_event_no (event_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-箭道快照：prev_status 记录锁定前业务状态，放行时按此恢复，绝不粗暴覆盖。
CREATE TABLE IF NOT EXISTS safety_event_lane (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  event_id    BIGINT      NOT NULL,
  lane_id     BIGINT      NOT NULL,
  prev_status VARCHAR(16) NOT NULL COMMENT '锁定前状态：OPEN / OCCUPIED / MAINTENANCE',
  restored    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '放行时是否已恢复',
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_lane (event_id, lane_id),
  CONSTRAINT fk_sl_event FOREIGN KEY (event_id) REFERENCES safety_event (id),
  CONSTRAINT fk_sl_lane  FOREIGN KEY (lane_id)  REFERENCES lanes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-器材快照：同样保留锁定前状态（租出中的器材放行后仍归原会员，renter 字段锁定期间不动）。
CREATE TABLE IF NOT EXISTS safety_event_equipment (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  event_id     BIGINT      NOT NULL,
  equipment_id BIGINT      NOT NULL,
  prev_status  VARCHAR(16) NOT NULL COMMENT '锁定前状态：INSTOCK / RENTED / REPAIR',
  restored     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_equip (event_id, equipment_id),
  CONSTRAINT fk_se_event FOREIGN KEY (event_id)     REFERENCES safety_event (id),
  CONSTRAINT fk_se_equip FOREIGN KEY (equipment_id) REFERENCES equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件-回合快照：受影响箭道上进行中的回合被暂停，放行后从原进度继续。
CREATE TABLE IF NOT EXISTS safety_event_round (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  event_id    BIGINT      NOT NULL,
  round_id    BIGINT      NOT NULL,
  prev_status VARCHAR(16) NOT NULL COMMENT '锁定前状态：ONGOING',
  restored    TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_round (event_id, round_id),
  CONSTRAINT fk_srd_event FOREIGN KEY (event_id) REFERENCES safety_event (id),
  CONSTRAINT fk_srd_round FOREIGN KEY (round_id) REFERENCES rounds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分项复核：每个事件每一轮有 RANGE（教练）与 EQUIPMENT（器材管理员）两项，
-- (event_id, review_round, item) 唯一，同一复核项只能形成一份最终结论。
CREATE TABLE IF NOT EXISTS safety_review (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  event_id     BIGINT       NOT NULL,
  review_round INT          NOT NULL,
  item         VARCHAR(16)  NOT NULL COMMENT 'RANGE 射线与人员安全 / EQUIPMENT 器材检查',
  conclusion   VARCHAR(8)   NULL COMMENT 'PASS 通过 / FAIL 不通过，NULL 为待复核',
  reviewer     VARCHAR(32)  NULL COMMENT '复核人',
  reviewed_at  DATETIME     NULL,
  note         VARCHAR(500) NULL COMMENT '复核意见；不通过时必填原因',
  PRIMARY KEY (id),
  UNIQUE KEY uk_safety_review (event_id, review_round, item),
  CONSTRAINT fk_srv_event FOREIGN KEY (event_id) REFERENCES safety_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 状态轨迹：创建、发起复核、每次复核结论、复核齐通过、退回待处置、复射放行，全程留痕。
CREATE TABLE IF NOT EXISTS safety_event_log (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  event_id   BIGINT       NOT NULL,
  action     VARCHAR(24)  NOT NULL COMMENT 'CREATE / START_REVIEW / REVIEW_PASS / REVIEW_FAIL / BACK_PENDING / CLEARED / RELEASE',
  operator   VARCHAR(32)  NOT NULL COMMENT '操作人',
  role       VARCHAR(16)  NOT NULL COMMENT 'MANAGER 值班经理 / COACH 教练 / KEEPER 器材管理员',
  detail     VARCHAR(500) NULL,
  created_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_safety_log_event (event_id),
  CONSTRAINT fk_slog_event FOREIGN KEY (event_id) REFERENCES safety_event (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- lanes：12 行
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (1, 'A01', 10, '纸质靶', 'OPEN', 60.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (2, 'A02', 10, '纸质靶', 'OCCUPIED', 60.00, 3, '赵启明', '2026-09-22 10:20:00', 2);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (3, 'A03', 18, '三联靶', 'OPEN', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (4, 'A04', 18, '三联靶', 'OPEN', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (5, 'A05', 18, '三联靶', 'MAINTENANCE', 80.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (6, 'A06', 30, '复合靶', 'OPEN', 100.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (7, 'A07', 30, '复合靶', 'OCCUPIED', 100.00, 7, '郑楠', '2026-09-22 13:05:00', 3);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (8, 'A08', 30, '复合靶', 'OCCUPIED', 100.00, 5, '周然', '2026-09-22 15:40:00', 1);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (9, 'A09', 50, '三联靶', 'OPEN', 140.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (10, 'A10', 50, '三联靶', 'OPEN', 140.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (11, 'A11', 50, '3D动物靶', 'MAINTENANCE', 120.00, NULL, NULL, NULL, NULL);
INSERT INTO lanes (`id`, `lane_no`, `distance`, `target_type`, `status`, `hourly_price`, `occupant_id`, `occupant_name`, `opened_at`, `planned_hours`) VALUES (12, 'A12', 30, '复合靶', 'OPEN', 100.00, NULL, NULL, NULL, NULL);

-- members：12 行
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (1, 'AR-2023-0001', '陈子昂', '13800010001', 'GOLD', 2680.00, '2023-03-18', 12480.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (2, 'AR-2023-0002', '林悦', '13800010002', 'SILVER', 960.00, '2023-06-02', 3420.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (3, 'AR-2023-0003', '赵启明', '13800010003', 'NORMAL', 320.00, '2023-09-11', 680.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (4, 'AR-2024-0004', '孙嘉', '13800010004', 'GOLD', 4120.00, '2024-01-05', 15600.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (5, 'AR-2024-0005', '周然', '13800010005', 'NORMAL', 150.00, '2024-02-20', 240.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (6, 'AR-2024-0006', '吴桐', '13800010006', 'SILVER', 1280.00, '2024-04-09', 4100.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (7, 'AR-2024-0007', '郑楠', '13800010007', 'NORMAL', 480.00, '2024-05-16', 720.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (8, 'AR-2024-0008', '何清', '13800010008', 'GOLD', 3260.00, '2024-06-21', 10200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (9, 'AR-2024-0009', '冯磊', '13800010009', 'SILVER', 880.00, '2024-07-30', 3200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (10, 'AR-2025-0010', '许静', '13800010010', 'NORMAL', 260.00, '2025-01-12', 520.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (11, 'AR-2025-0011', '高翔', '13800010011', 'SILVER', 1560.00, '2025-03-08', 5200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (12, 'AR-2025-0012', '唐宁', '13800010012', 'NORMAL', 100.00, '2025-05-27', 180.00);

-- rounds：12 行
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (1, 'R20260915001', 1, 1, '2026-09-15 19:10:00', 6, 58, 9.67, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (2, 'R20260916001', 2, 3, '2026-09-16 18:40:00', 6, 43, 7.17, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (3, 'R20260916002', 4, 6, '2026-09-16 20:05:00', 12, 112, 9.33, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (4, 'R20260917001', 6, 4, '2026-09-17 19:30:00', 6, 45, 7.50, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (5, 'R20260918001', 8, 9, '2026-09-18 20:15:00', 12, 109, 9.08, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (6, 'R20260919001', 1, 10, '2026-09-19 19:00:00', 6, 58, 9.67, 0, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (7, 'R20260920001', 9, 8, '2026-09-20 18:20:00', 6, 37, 6.17, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (8, 'R20260920002', 11, 7, '2026-09-20 20:30:00', 12, 104, 8.67, 1, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (9, 'R20260921001', 4, 12, '2026-09-21 19:45:00', 6, 59, 9.83, 0, 'SUBMITTED');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (10, 'R20260922001', 3, 2, '2026-09-22 10:20:00', 6, 0, 0.00, 0, 'ONGOING');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (11, 'R20260922002', 7, 7, '2026-09-22 13:05:00', 12, 0, 0.00, 0, 'ONGOING');
INSERT INTO rounds (`id`, `round_no`, `member_id`, `lane_id`, `start_time`, `arrow_count`, `total_score`, `average_score`, `personal_best`, `status`) VALUES (12, 'R20260922003', 5, 8, '2026-09-22 15:40:00', 6, 0, 0.00, 0, 'ONGOING');

-- arrow_score：72 行
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (1, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (2, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (3, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (4, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (5, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (6, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (7, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (8, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (9, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (10, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (11, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (12, 'M', 0);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (13, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (14, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (15, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (16, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (17, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (18, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (19, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (20, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (21, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (22, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (23, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (24, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (25, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (26, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (27, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (28, '6', 6);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (29, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (30, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (31, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (32, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (33, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (34, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (35, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (36, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (37, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (38, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (39, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (40, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (41, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (42, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (43, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (44, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (45, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (46, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (47, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (48, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (49, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (50, '6', 6);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (51, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (52, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (53, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (54, 'M', 0);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (55, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (56, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (57, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (58, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (59, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (60, '7', 7);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (61, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (62, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (63, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (64, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (65, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (66, '8', 8);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (67, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (68, 'X', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (69, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (70, '9', 9);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (71, '10', 10);
INSERT INTO arrow_score (`id`, `ring`, `ring_value`) VALUES (72, '10', 10);

-- round_arrow：72 行
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 1, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 2, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 3, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 4, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 5, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (1, 6, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 7, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 8, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 9, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 10, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 11, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (2, 12, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 13, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 14, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 15, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 16, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 17, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 18, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 19, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 20, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 21, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 22, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 23, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (3, 24, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 25, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 26, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 27, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 28, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 29, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (4, 30, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 31, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 32, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 33, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 34, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 35, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 36, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 37, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 38, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 39, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 40, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 41, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (5, 42, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 43, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 44, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 45, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 46, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 47, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (6, 48, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 49, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 50, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 51, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 52, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 53, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (7, 54, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 55, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 56, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 57, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 58, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 59, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 60, 5);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 61, 6);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 62, 7);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 63, 8);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 64, 9);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 65, 10);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (8, 66, 11);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 67, 0);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 68, 1);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 69, 2);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 70, 3);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 71, 4);
INSERT INTO round_arrow (`round_id`, `arrow_id`, `shot_index`) VALUES (9, 72, 5);

-- courses：10 行
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (1, '反曲弓入门体验', '张岩', 'BASIC', '2026-09-23 19:00:00', 8, 5, '一号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (2, '站姿与撒放基础', '张岩', 'BASIC', '2026-09-24 19:30:00', 8, 4, '一号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (3, '瞄点与稳定训练', '李慕白', 'ADVANCED', '2026-09-25 20:00:00', 6, 3, '二号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (4, '复合弓调校实操', '王铮', 'ADVANCED', '2026-09-26 19:00:00', 6, 3, '器材工坊');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (5, '30米定距强化', '李慕白', 'ADVANCED', '2026-09-27 20:00:00', 6, 6, '二号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (6, '竞技反曲特训班', '陈亦驰', 'COMPETITION', '2026-09-28 19:00:00', 4, 3, '竞技馆');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (7, '室内18米积分赛', '陈亦驰', 'COMPETITION', '2026-09-29 19:30:00', 4, 2, '竞技馆');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (8, '传统弓礼仪与技法', '苏禾', 'BASIC', '2026-09-30 19:00:00', 10, 3, '三号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (9, '青少年安全射箭课', '苏禾', 'BASIC', '2026-10-01 10:00:00', 10, 5, '一号教学区');
INSERT INTO courses (`id`, `course_name`, `coach`, `level`, `class_time`, `capacity`, `enrolled`, `venue`) VALUES (10, '赛前心理与节奏', '陈亦驰', 'COMPETITION', '2026-10-02 19:00:00', 4, 2, '竞技馆');

-- course_enroll：36 行
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (1, 1, 1, '2026-09-13 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (2, 1, 2, '2026-09-13 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (3, 1, 3, '2026-09-13 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (4, 1, 5, '2026-09-13 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (5, 1, 7, '2026-09-13 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (6, 2, 3, '2026-09-14 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (7, 2, 5, '2026-09-14 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (8, 2, 10, '2026-09-14 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (9, 2, 12, '2026-09-14 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (10, 3, 2, '2026-09-15 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (11, 3, 6, '2026-09-15 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (12, 3, 11, '2026-09-15 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (13, 4, 4, '2026-09-16 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (14, 4, 6, '2026-09-16 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (15, 4, 9, '2026-09-16 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (16, 5, 1, '2026-09-17 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (17, 5, 2, '2026-09-17 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (18, 5, 4, '2026-09-17 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (19, 5, 6, '2026-09-17 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (20, 5, 9, '2026-09-17 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (21, 5, 11, '2026-09-17 09:45:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (22, 6, 1, '2026-09-18 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (23, 6, 4, '2026-09-18 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (24, 6, 8, '2026-09-18 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (25, 7, 4, '2026-09-19 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (26, 7, 8, '2026-09-19 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (27, 8, 7, '2026-09-20 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (28, 8, 10, '2026-09-20 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (29, 8, 12, '2026-09-20 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (30, 9, 3, '2026-09-12 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (31, 9, 5, '2026-09-12 09:17:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (32, 9, 7, '2026-09-12 09:24:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (33, 9, 10, '2026-09-12 09:31:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (34, 9, 12, '2026-09-12 09:38:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (35, 10, 1, '2026-09-13 09:10:00');
INSERT INTO course_enroll (`id`, `course_id`, `member_id`, `enroll_time`) VALUES (36, 10, 8, '2026-09-13 09:17:00');

-- equipment：12 行
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (1, 'E-RC-01', 'RECURVE', 'Hoyt', 45.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (2, 'E-RC-02', 'RECURVE', 'W&W', 40.00, 'RENTED', 3, '赵启明', '2026-09-22 10:25:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (3, 'E-RC-03', 'RECURVE', 'KAP', 38.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (4, 'E-CP-01', 'COMPOUND', 'Mathews', 80.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (5, 'E-CP-02', 'COMPOUND', 'Hoyt', 75.00, 'RENTED', 7, '郑楠', '2026-09-22 13:10:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (6, 'E-CP-03', 'COMPOUND', 'PSE', 70.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (7, 'E-TR-01', 'TRADITIONAL', '三利达', 35.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (8, 'E-TR-02', 'TRADITIONAL', '黑石', 30.00, 'REPAIR', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (9, 'E-GR-01', 'GEAR', 'Fivics', 12.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (10, 'E-GR-02', 'GEAR', 'Cartel', 15.00, 'RENTED', 5, '周然', '2026-09-22 15:45:00');
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (11, 'E-AR-01', 'ARROW', 'Easton', 20.00, 'INSTOCK', NULL, NULL, NULL);
INSERT INTO equipment (`id`, `equip_code`, `type`, `brand`, `rent_price`, `status`, `renter_id`, `renter_name`, `rented_at`) VALUES (12, 'E-AR-02', 'ARROW', 'Gold Tip', 18.00, 'INSTOCK', NULL, NULL, NULL);

-- ================= 馆内团体淘汰赛计分台 =================
-- 设计要点：
--   1. 赛事一旦 STARTED，队伍与对阵全部冻结，后续轮次的队伍由 winner_team_id 自动带入，不依赖前端拼接。
--   2. 局（game）确认后只读；改错只能由裁判撤回【当前未结束比赛的最后一局】，撤回动作与原因进 tournament_log。
--   3. 平分走加赛：每轮每名队员 1 支，先比加赛总分、再比 X 数，仍平继续下一轮，直到唯一胜者。
--   4. 所有对阵关系、局分、加赛箭、轨迹全部落库，刷新或服务重启不丢。

-- 先补足会员到 24 人（8 队 x 3 人需要 24 名不同会员）
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (13, 'AR-2025-0013', '罗一飞', '13800010013', 'GOLD', 3890.00, '2025-06-08', 11800.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (14, 'AR-2025-0014', '韩雪', '13800010014', 'SILVER', 760.00, '2025-06-19', 2600.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (15, 'AR-2025-0015', '邓超', '13800010015', 'NORMAL', 210.00, '2025-07-02', 360.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (16, 'AR-2025-0016', '曹颖', '13800010016', 'GOLD', 5120.00, '2025-07-25', 18900.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (17, 'AR-2025-0017', '彭于', '13800010017', 'NORMAL', 90.00, '2025-08-03', 150.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (18, 'AR-2025-0018', '曾黎', '13800010018', 'SILVER', 1430.00, '2025-08-16', 4600.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (19, 'AR-2025-0019', '肖战', '13800010019', 'NORMAL', 300.00, '2025-09-01', 480.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (20, 'AR-2025-0020', '田雨', '13800010020', 'GOLD', 2780.00, '2025-09-09', 9900.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (21, 'AR-2025-0021', '董洁', '13800010021', 'SILVER', 640.00, '2025-09-12', 2200.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (22, 'AR-2025-0022', '范冰', '13800010022', 'NORMAL', 180.00, '2025-09-15', 300.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (23, 'AR-2025-0023', '夏雨', '13800010023', 'GOLD', 4560.00, '2025-09-18', 16700.00);
INSERT INTO members (`id`, `card_no`, `name`, `phone`, `level`, `balance`, `register_date`, `total_spend`) VALUES (24, 'AR-2025-0024', '沈腾', '13800010024', 'NORMAL', 260.00, '2025-09-20', 420.00);

CREATE TABLE IF NOT EXISTS tournaments (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  tournament_no          VARCHAR(24)  NOT NULL COMMENT '赛事编号',
  name                   VARCHAR(64)  NOT NULL COMMENT '赛事名称',
  status                 VARCHAR(16)  NOT NULL COMMENT 'SETUP 筹备中 / ONGOING 进行中 / FINISHED 已结束',
  team_size              INT          NOT NULL COMMENT '参赛队数：4 或 8',
  games_per_match        INT          NOT NULL COMMENT '每场局数',
  arrows_per_member      INT          NOT NULL COMMENT '每局每名队员规定箭数',
  current_round          INT          NOT NULL DEFAULT 1 COMMENT '当前轮次（1 起）',
  total_rounds           INT          NOT NULL COMMENT '总轮次：4队=2，8队=3',
  champion_team_id       BIGINT       NULL COMMENT '冠军队',
  created_by             VARCHAR(32)  NOT NULL COMMENT '创建赛事的值班经理',
  created_at             DATETIME     NOT NULL,
  started_at             DATETIME     NULL,
  finished_at            DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tournament_no (tournament_no),
  KEY idx_tournament_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tournament_team (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tournament_id BIGINT       NOT NULL,
  slot_no       INT          NOT NULL COMMENT '队在赛事中的签位（1..team_size）',
  team_name     VARCHAR(48)  NOT NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_tournament_slot (tournament_id, slot_no),
  UNIQUE KEY uk_team_tournament_name (tournament_id, team_name),
  CONSTRAINT fk_team_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 队-会员：同一会员在同一赛事里只能出现在一支队伍（uk_team_member_tournament_member 兜底）
CREATE TABLE IF NOT EXISTS tournament_team_member (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  tournament_id BIGINT      NOT NULL COMMENT '冗余赛事列，支撑同赛事会员唯一约束',
  team_id       BIGINT      NOT NULL,
  member_id     BIGINT      NOT NULL,
  seat_no       INT         NOT NULL COMMENT '队内座次 1/2/3',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_member_tournament_member (tournament_id, member_id),
  UNIQUE KEY uk_team_member_team_seat (team_id, seat_no),
  KEY idx_tm_member (member_id),
  CONSTRAINT fk_tm_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
  CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES tournament_team (id),
  CONSTRAINT fk_tm_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对阵：slot 形如 101/102（第一轮）、201（第二轮）、301（决赛）。
-- 后续轮次的 team_a_id/team_b_id 在前序比赛确认胜者时由服务端写入，前端不能拼。
CREATE TABLE IF NOT EXISTS tournament_match (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  tournament_id    BIGINT       NOT NULL,
  slot_no          INT          NOT NULL COMMENT '场次槽位：轮次*100+轮内序号',
  round_no         INT          NOT NULL COMMENT '第几轮',
  source_slot_a    INT          NULL COMMENT 'A 队来源场次（首轮为空，直接挂签位队）',
  source_slot_b    INT          NULL COMMENT 'B 队来源场次',
  team_a_id        BIGINT       NULL,
  team_b_id        BIGINT       NULL,
  team_a_seed      INT          NULL COMMENT '首轮 A 队签位',
  team_b_seed      INT          NULL COMMENT '首轮 B 队签位',
  status           VARCHAR(16)  NOT NULL COMMENT 'PENDING 待开赛场次 / LIVE 比赛中 / FINISHED 已结束',
  winner_team_id   BIGINT       NULL,
  win_mode         VARCHAR(16)  NULL COMMENT 'REGULATION 局分取胜 / PLAYOFF 加赛取胜',
  reg_score_a      INT          NOT NULL DEFAULT 0 COMMENT '正规局总分 A',
  reg_score_b      INT          NOT NULL DEFAULT 0 COMMENT '正规局总分 B',
  reg_x_a          INT          NOT NULL DEFAULT 0 COMMENT '正规局 X 数 A',
  reg_x_b          INT          NOT NULL DEFAULT 0 COMMENT '正规局 X 数 B',
  started_at       DATETIME     NULL,
  finished_at      DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_tournament_slot (tournament_id, slot_no),
  KEY idx_match_round (tournament_id, round_no),
  CONSTRAINT fk_match_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id),
  CONSTRAINT fk_match_team_a FOREIGN KEY (team_a_id) REFERENCES tournament_team (id),
  CONSTRAINT fk_match_team_b FOREIGN KEY (team_b_id) REFERENCES tournament_team (id),
  CONSTRAINT fk_match_winner FOREIGN KEY (winner_team_id) REFERENCES tournament_team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tournament_game (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  match_id      BIGINT       NOT NULL,
  game_no       INT          NOT NULL COMMENT '局号（1 起，顺序即比赛顺序）',
  status        VARCHAR(16)  NOT NULL COMMENT 'ENTERING 录入中 / CONFIRMED 已确认',
  score_a       INT          NOT NULL DEFAULT 0,
  score_b       INT          NOT NULL DEFAULT 0,
  x_a           INT          NOT NULL DEFAULT 0,
  x_b           INT          NOT NULL DEFAULT 0,
  created_by    VARCHAR(32)  NULL,
  confirmed_by  VARCHAR(32)  NULL,
  created_at    DATETIME     NOT NULL,
  confirmed_at  DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_match_no (match_id, game_no),
  CONSTRAINT fk_game_match FOREIGN KEY (match_id) REFERENCES tournament_match (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 淘汰赛专用箭支（口径沿用 X/10..1/M；与日常计分的 arrow_score 独立）。
-- 叶子除环数外带上 member_id / team_id，箭属于哪支队伍、哪位队员随箭落库；
-- 局 / 加赛轮到箭的有序关系走 join 表（@JoinTable + @OrderColumn），与日常 round_arrow 同款。
CREATE TABLE IF NOT EXISTS tournament_arrow (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  ring       VARCHAR(4)  NOT NULL,
  ring_value INT         NOT NULL,
  member_id  BIGINT      NULL,
  team_id    BIGINT      NULL,
  PRIMARY KEY (id),
  KEY idx_ta_member (member_id),
  CONSTRAINT fk_ta_member FOREIGN KEY (member_id) REFERENCES members (id),
  CONSTRAINT fk_ta_team FOREIGN KEY (team_id) REFERENCES tournament_team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 局-箭 有序集合连接表：shot_index 由 Hibernate 的 @OrderColumn 维护（箭序 0 起）
CREATE TABLE IF NOT EXISTS tournament_game_arrow (
  game_id    BIGINT NOT NULL,
  arrow_id   BIGINT NOT NULL,
  shot_index INT    NOT NULL,
  PRIMARY KEY (game_id, shot_index),
  UNIQUE KEY uk_game_arrow_arrow (arrow_id),
  CONSTRAINT fk_ga_game FOREIGN KEY (game_id) REFERENCES tournament_game (id),
  CONSTRAINT fk_ga_arrow FOREIGN KEY (arrow_id) REFERENCES tournament_arrow (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 加赛轮：每轮每队三名队员各射一支（共 6 支）
CREATE TABLE IF NOT EXISTS tournament_playoff_round (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  match_id      BIGINT       NOT NULL,
  round_no      INT          NOT NULL COMMENT '加赛第几轮（1 起）',
  status        VARCHAR(16)  NOT NULL COMMENT 'ENTERING 录入中 / TIED 仍平分 / DECIDED 已分出胜负',
  score_a       INT          NOT NULL DEFAULT 0,
  score_b       INT          NOT NULL DEFAULT 0,
  x_a           INT          NOT NULL DEFAULT 0,
  x_b           INT          NOT NULL DEFAULT 0,
  winner_team_id BIGINT      NULL,
  created_by    VARCHAR(32)  NULL,
  confirmed_by  VARCHAR(32)  NULL,
  created_at    DATETIME     NOT NULL,
  decided_at    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_playoff_match_round (match_id, round_no),
  CONSTRAINT fk_pr_match FOREIGN KEY (match_id) REFERENCES tournament_match (id),
  CONSTRAINT fk_pr_winner FOREIGN KEY (winner_team_id) REFERENCES tournament_team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 加赛箭有序集合连接表：每名队员每轮只有一支（uk_pa_round_member 兜底）
CREATE TABLE IF NOT EXISTS tournament_playoff_arrow (
  playoff_round_id BIGINT NOT NULL,
  arrow_id         BIGINT NOT NULL,
  shot_index       INT    NOT NULL,
  PRIMARY KEY (playoff_round_id, shot_index),
  UNIQUE KEY uk_pa_arrow (arrow_id),
  CONSTRAINT fk_pa_round FOREIGN KEY (playoff_round_id) REFERENCES tournament_playoff_round (id),
  CONSTRAINT fk_pa_arrow FOREIGN KEY (arrow_id) REFERENCES tournament_arrow (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 完整操作轨迹：建队/换队/开赛/开赛轮/记局/确认局/撤回局/加赛/确认胜者/晋级/夺冠
CREATE TABLE IF NOT EXISTS tournament_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tournament_id BIGINT       NOT NULL,
  match_id      BIGINT       NULL,
  action        VARCHAR(24)  NOT NULL,
  operator      VARCHAR(32)  NOT NULL COMMENT '操作人（撤回人也在此）',
  role          VARCHAR(16)  NOT NULL COMMENT 'MANAGER / REFEREE / SYSTEM',
  detail        VARCHAR(500) NULL,
  created_at    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tlog_tournament (tournament_id),
  KEY idx_tlog_match (match_id),
  CONSTRAINT fk_tlog_tournament FOREIGN KEY (tournament_id) REFERENCES tournaments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
