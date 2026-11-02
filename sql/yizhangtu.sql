-- ============================================================
-- 轻量行业一张图监管平台 · 建库建表 + 演示种子数据（水利/河长首发场景）
-- 可重复执行：内部先 DROP 再 CREATE
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 < sql/yizhangtu.sql
-- ============================================================

DROP DATABASE IF EXISTS yizhangtu;
CREATE DATABASE yizhangtu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yizhangtu;

-- ---------------- 账号（ADMIN 全量 / OPERATOR 操作 / VIEWER 只读） ----------------
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL COMMENT '登录名',
  password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
  phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  role        VARCHAR(10)  NOT NULL DEFAULT 'VIEWER' COMMENT 'ADMIN/OPERATOR/VIEWER',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='账号';

-- ---------------- 地图图层配置 ----------------
CREATE TABLE map_layer (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  name            VARCHAR(50) NOT NULL COMMENT '图层名',
  group_name      VARCHAR(50) NOT NULL DEFAULT '基础点位' COMMENT '分组：基础点位/区域面/专题',
  layer_type      VARCHAR(10) NOT NULL DEFAULT 'POINT' COMMENT 'POINT/POLYGON/STATS',
  default_visible TINYINT     NOT NULL DEFAULT 1 COMMENT '默认显示 1/0',
  sort_order      INT         NOT NULL DEFAULT 0 COMMENT '排序号（小在前）',
  style_json      VARCHAR(500) DEFAULT NULL COMMENT '样式配置 JSON',
  status          TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='地图图层';

-- ---------------- 点位对象 ----------------
CREATE TABLE point_object (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  name          VARCHAR(50)   NOT NULL COMMENT '点位名称',
  category      VARCHAR(10)   NOT NULL COMMENT 'MONITOR 监测/HAZARD 隐患/PROJECT 工程/PATROL 巡查',
  region        VARCHAR(50)   DEFAULT NULL COMMENT '所属区域（= region_area.name）',
  river_segment VARCHAR(50)   DEFAULT NULL COMMENT '河段',
  lng           DECIMAL(10,6) NOT NULL COMMENT '经度',
  lat           DECIMAL(10,6) NOT NULL COMMENT '纬度',
  attrs_json    TEXT          DEFAULT NULL COMMENT '属性 JSON，如 {"water_level":6.8,"rainfall":32}',
  photo         VARCHAR(255)  DEFAULT NULL COMMENT '现场照片 URL',
  status        VARCHAR(10)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 正常 / DISABLED 停用',
  created_by    BIGINT        DEFAULT NULL COMMENT '创建人 id',
  created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_category (category),
  KEY idx_region (region)
) ENGINE=InnoDB COMMENT='点位对象';

-- ---------------- 示范区域面 ----------------
CREATE TABLE region_area (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  name         VARCHAR(50)   NOT NULL COMMENT '区域名',
  parent_name  VARCHAR(50)   DEFAULT NULL COMMENT '上级行政区（演示虚构）',
  geojson      TEXT          NOT NULL COMMENT 'Polygon GeoJSON 文本',
  centroid_lng DECIMAL(10,6) DEFAULT NULL COMMENT '中心经度',
  centroid_lat DECIMAL(10,6) DEFAULT NULL COMMENT '中心纬度',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='示范区域';

-- ---------------- 告警阈值规则 ----------------
CREATE TABLE alarm_rule (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  point_id    BIGINT        NOT NULL COMMENT '点位 id',
  attr_key    VARCHAR(30)   NOT NULL COMMENT '属性键，如 water_level',
  attr_label  VARCHAR(30)   NOT NULL COMMENT '属性名，如 水位',
  upper_limit DECIMAL(12,2) DEFAULT NULL COMMENT '上限（超过告警）',
  lower_limit DECIMAL(12,2) DEFAULT NULL COMMENT '下限（低于告警）',
  enabled     TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  remark      VARCHAR(255)  DEFAULT NULL,
  created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_point (point_id)
) ENGINE=InnoDB COMMENT='告警阈值规则';

-- ---------------- 告警记录 ----------------
CREATE TABLE alarm_record (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  point_id      BIGINT       NOT NULL,
  point_name    VARCHAR(50)  NOT NULL,
  rule_id       BIGINT       DEFAULT NULL,
  alarm_type    VARCHAR(30)  NOT NULL COMMENT '属性键 + _UPPER/_LOWER，如 water_level_UPPER',
  value_str     VARCHAR(50)  NOT NULL COMMENT '实测值快照',
  threshold_str VARCHAR(50)  NOT NULL COMMENT '阈值快照',
  status        VARCHAR(12)  NOT NULL DEFAULT 'UNPROCESSED' COMMENT 'UNPROCESSED/PROCESSED/CLOSED',
  handle_note   VARCHAR(255) DEFAULT NULL COMMENT '处理说明（留痕）',
  handler       VARCHAR(50)  DEFAULT NULL COMMENT '处理人',
  handled_at    DATETIME     DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_point (point_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB COMMENT='告警记录';

-- ============================================================
-- 演示种子数据
-- 账号：admin/admin123(ADMIN)、operator/123456(OPERATOR)、viewer/123456(VIEWER)，BCrypt 加密
-- ============================================================
INSERT INTO sys_user (username, password, nickname, phone, role, status) VALUES
('admin',    '$2a$10$NnWoUGNv3XNwv2.KVjYIz.Er5zG/sbt4g6B.rc5oQ2lzL5HOHOhFC', '平台管理员', '13800000001', 'ADMIN', 1),
('operator', '$2a$10$IG1XFb5h6MmvaVkFG/nRFOLqXEoM91.FcsBzjbttZOW2Znlcw3TrK', '河道操作员', '13800000002', 'OPERATOR', 1),
('viewer',   '$2a$10$IG1XFb5h6MmvaVkFG/nRFOLqXEoM91.FcsBzjbttZOW2Znlcw3TrK', '观摩访客',   '13800000003', 'VIEWER', 1);

-- ---------------- 示范区域（坐标围绕南宁 108.3E / 22.8N 手绘简单多边形） ----------------
INSERT INTO region_area (name, parent_name, geojson, centroid_lng, centroid_lat) VALUES
('邕江上游段示范区', '青秀区',
 '{"type":"Polygon","coordinates":[[[108.30,22.80],[108.42,22.81],[108.44,22.89],[108.33,22.91],[108.29,22.86],[108.30,22.80]]]}',
 108.360000, 22.850000),
('左江扶绥县示范区', '扶绥县',
 '{"type":"Polygon","coordinates":[[[108.09,22.65],[108.20,22.66],[108.22,22.74],[108.12,22.76],[108.07,22.71],[108.09,22.65]]]}',
 108.150000, 22.700000),
('明江那隆镇示范区', '隆安县',
 '{"type":"Polygon","coordinates":[[[108.45,22.67],[108.56,22.68],[108.58,22.76],[108.48,22.78],[108.43,22.73],[108.45,22.67]]]}',
 108.500000, 22.720000),
('大明山片区示范区', '武鸣区',
 '{"type":"Polygon","coordinates":[[[108.19,22.90],[108.31,22.91],[108.33,22.99],[108.22,23.01],[108.17,22.96],[108.19,22.90]]]}',
 108.250000, 22.950000);

-- ---------------- 地图图层（3 个） ----------------
INSERT INTO map_layer (name, group_name, layer_type, default_visible, sort_order, style_json, status) VALUES
('监测点位',   '基础点位', 'POINT',   1, 1, '{"color":"#22d3ee","radius":9,"categories":["MONITOR"]}', 1),
('隐患工程专题','专题',    'POINT',   1, 2, '{"color":"#f59e0b","radius":8,"categories":["HAZARD","PROJECT","PATROL"]}', 1),
('示范区域面', '区域面',   'POLYGON', 1, 3, '{"color":"#3b82f6","fillOpacity":0.18}', 1);

-- ---------------- 点位（15 个，覆盖 4 类分类、分布在 4 个区域） ----------------
INSERT INTO point_object (name, category, region, river_segment, lng, lat, attrs_json, photo, status, created_by) VALUES
('邕江口监测站',     'MONITOR', '邕江上游段示范区', '邕江左岸 K3+200', 108.350000, 22.850000, '{"water_level":6.8,"rainfall":32}',   NULL, 'NORMAL', 1),
('青秀山闸监测点',   'MONITOR', '邕江上游段示范区', '邕江右岸 K6+100', 108.400000, 22.860000, '{"water_level":5.2,"rainfall":28}',   NULL, 'NORMAL', 1),
('民生广场隐患点',   'HAZARD',  '邕江上游段示范区', '邕江左岸 K5+000', 108.370000, 22.830000, '{"risk_level":"高","last_inspect":"2026-10-18"}', NULL, 'NORMAL', 1),
('邕江大桥加固工程', 'PROJECT', '邕江上游段示范区', '邕江右岸 K4+500', 108.330000, 22.870000, '{"progress":"65%","invest":"1.2亿"}', NULL, 'NORMAL', 1),
('青秀巡河一队',     'PATROL',  '邕江上游段示范区', '邕江左岸全线',    108.310000, 22.840000, '{"patrol_days":12,"team":"第一巡河队"}', NULL, 'NORMAL', 1),

('扶绥水文监测站',   'MONITOR', '左江扶绥县示范区', '左江右岸 K8+300', 108.140000, 22.700000, '{"water_level":4.5,"rainfall":45}',   NULL, 'NORMAL', 1),
('左江塌岸隐患点',   'HAZARD',  '左江扶绥县示范区', '左江左岸 K9+100', 108.180000, 22.720000, '{"risk_level":"中","last_inspect":"2026-10-16"}', NULL, 'NORMAL', 1),
('山口水库加固工程', 'PROJECT', '左江扶绥县示范区', '山口库区',        108.110000, 22.680000, '{"progress":"40%","invest":"0.8亿"}', NULL, 'NORMAL', 1),
('新和镇巡河队',     'PATROL',  '左江扶绥县示范区', '左江左岸全线',    108.160000, 22.670000, '{"patrol_days":9,"team":"第二巡河队"}',  NULL, 'NORMAL', 1),

('那隆闸监测站',     'MONITOR', '明江那隆镇示范区', '明江右岸 K2+600', 108.510000, 22.720000, '{"water_level":3.6,"rainfall":55}',   NULL, 'NORMAL', 1),
('明江堤段管涌点',   'HAZARD',  '明江那隆镇示范区', '明江左岸 K3+400', 108.540000, 22.740000, '{"risk_level":"高","last_inspect":"2026-10-20"}', NULL, 'NORMAL', 1),
('那隆河整治工程',   'PROJECT', '明江那隆镇示范区', '明江右岸 K1+800', 108.470000, 22.710000, '{"progress":"78%","invest":"0.5亿"}', NULL, 'NORMAL', 1),

('大明山山洪监测点', 'MONITOR', '大明山片区示范区', '山洪沟 A 线',     108.260000, 22.960000, '{"water_level":2.4,"rainfall":60}',   NULL, 'NORMAL', 1),
('山口村地灾隐患点', 'HAZARD',  '大明山片区示范区', '山洪沟 B 线',     108.220000, 22.940000, '{"risk_level":"中","last_inspect":"2026-10-15"}', NULL, 'NORMAL', 1),
('大明山巡护站',     'PATROL',  '大明山片区示范区', '片区巡护路线',    108.280000, 22.980000, '{"patrol_days":15,"team":"山护队"}',   NULL, 'NORMAL', 1);

-- ---------------- 阈值规则（2 条） ----------------
INSERT INTO alarm_rule (point_id, attr_key, attr_label, upper_limit, lower_limit, enabled, remark) VALUES
(1,  'water_level', '水位', 8.00, 0.00,  1, '汛期限制水位 8.0m，超过即告警'),
(13, 'rainfall',    '雨量', 50.00, NULL, 1, '24 小时累计雨量 50mm 预警');

-- ---------------- 历史告警（含未处理/已处理/已关闭，时间相对当前，便于演示“今日告警”） ----------------
INSERT INTO alarm_record
  (point_id, point_name, rule_id, alarm_type, value_str, threshold_str, status, handle_note, handler, handled_at, created_at)
VALUES
(1,  '邕江口监测站',     1, 'water_level_UPPER', '8.6m',  '> 8.0m', 'UNPROCESSED', NULL, NULL, NULL, NOW() - INTERVAL 10 HOUR),
(1,  '邕江口监测站',     1, 'water_level_UPPER', '8.3m',  '> 8.0m', 'PROCESSED',   '已开闸放水，水位回落至警戒以下', 'operator', NOW() - INTERVAL 20 HOUR, NOW() - INTERVAL 22 HOUR),
(13, '大明山山洪监测点', 2, 'rainfall_UPPER',    '62mm',  '> 50mm', 'PROCESSED',   '已通知镇村加密巡查，转移低洼户 3 户', 'operator', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY - INTERVAL 2 HOUR),
(13, '大明山山洪监测点', 2, 'rainfall_UPPER',    '58mm',  '> 50mm', 'UNPROCESSED', NULL, NULL, NULL, DATE_ADD(CURDATE(), INTERVAL 6 HOUR)),
(1,  '邕江口监测站',     1, 'water_level_UPPER', '8.1m',  '> 8.0m', 'CLOSED',      '误报：传感器标定中产生的跳变', 'admin', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY);
