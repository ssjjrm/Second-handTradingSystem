-- =============================================================================
--  二手交易平台 · 建库建表脚本
--  文件：database/01_schema_secondhand_shop.sql
--  阶段：S1.1
-- =============================================================================
--
--  【本脚本做什么】
--    1. 建库 secondhand_shop（已存在则跳过）
--    2. 删掉 5 张旧表（反序），再重建 5 张新表（正序）
--
--  【本脚本不做什么】
--    - 不插入任何业务数据（那是 02_data_category.sql / 03_data_seed.sql 的事）
--    - 不碰参考项目的 book_cycle 库
--
--  【怎么执行】
--    方式一（推荐，走项目脚本）：
--      powershell -ExecutionPolicy Bypass -File scripts\db-init.ps1 -DbName secondhand_shop -SqlDir .\database
--    方式二（手动）：
--      mysql -uroot -p123456 --default-character-set=utf8mb4 < database\01_schema_secondhand_shop.sql
--
--  【重要设计约定 —— 后续 Java 代码必须遵守，否则会踩参考项目的坑】
--    1. 没有 deleted 字段！本项目用【物理删除】，MyBatis-Plus 的
--       logic-delete 配置必须保持关闭。
--       （参考项目的教训：yml 里配了 logic-delete-field: deleted，
--         但表里根本没这一列，所以每次查询都报 Unknown column 'deleted'）
--    2. 状态值全部用 tinyint 数字，Java 侧对应枚举类，禁止在业务代码里写裸数字。
--    3. favorite_count 是【冗余字段】，任何改动收藏的代码都必须同步维护它。
--    4. 时间字段由数据库给默认值兜底，Java 侧也会用 MyBatis-Plus 自动填充。
--
--  【5 张表的依赖关系】
--
--        user ──1:N(seller_id)──┐
--                               ▼
--        category ─1:N(category_id)─→ product
--                                        │
--                        ┌───────────────┴───────────────┐
--                        │ 1:N                           │ 1:N
--                        ▼                               ▼
--                    favorite                     user_behavior
--                   （当前状态）                    （历史流水）
--
--    建表顺序：user / category → product → favorite / user_behavior
--    删表顺序：正好相反
--
-- =============================================================================

-- 保证手动执行时客户端与服务器都用 utf8mb4 通信，避免中文注释/数据乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `secondhand_shop`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `secondhand_shop`;

-- -----------------------------------------------------------------------------
-- 删表：必须【反序】—— 先删引用别人的表，再删被引用的表
-- 顺序：favorite / user_behavior → product → category / user
-- （这样本脚本可以反复执行，每次都得到一份干净的结构）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `user_behavior`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `user`;


-- =============================================================================
--  表 1 / 5：user  用户表
-- =============================================================================
--  role 只有两种：user（普通用户）/ admin（管理员）。
--  本项目【不分买家和卖家】—— 任何人既能发商品也能买，所以没有 seller 角色。
--
--  wechat / qq 属于"联系方式"，只通过 /api/products/{id}/contact 接口
--  在【登录后主动点击】时才返回，任何列表接口都不返回。
--  phone 更进一步：只用于账号找回，永远不对外展示。
-- =============================================================================
CREATE TABLE `user` (
    `id`          int          NOT NULL AUTO_INCREMENT           COMMENT '用户ID',
    `username`    varchar(50)  NOT NULL                          COMMENT '登录名（唯一）',
    `password`    varchar(100) NOT NULL                          COMMENT '密码密文（BCrypt，固定60字符，绝不能存明文）',
    `nickname`    varchar(50)      DEFAULT NULL                  COMMENT '昵称（展示用）',
    `wechat`      varchar(50)      DEFAULT NULL                  COMMENT '微信号（仅登录用户点击"获取联系方式"后可见）',
    `qq`          varchar(20)      DEFAULT NULL                  COMMENT 'QQ号（仅登录用户点击"获取联系方式"后可见）',
    `phone`       varchar(20)      DEFAULT NULL                  COMMENT '手机号（仅账号找回用，任何接口都不返回）',
    `avatar`      varchar(255)     DEFAULT NULL                  COMMENT '头像URL',
    `role`        varchar(20)  NOT NULL DEFAULT 'user'           COMMENT '角色：user普通用户 / admin管理员',
    `status`      tinyint      NOT NULL DEFAULT 1                COMMENT '账号状态：1正常 / 0禁用（禁用后不能登录）',
    `create_time` datetime         DEFAULT CURRENT_TIMESTAMP     COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)                        COMMENT '登录名唯一，防止注册重名',
    KEY `idx_role` (`role`)                                      COMMENT '按角色筛选（管理员列表）',
    KEY `idx_status` (`status`)                                  COMMENT '按状态筛选（禁用/正常）',
    CONSTRAINT `chk_user_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_user_role`   CHECK (`role` IN ('user', 'admin'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户表';


-- =============================================================================
--  表 2 / 5：category  商品分类表（两级树，用 parent_id 自关联）
-- =============================================================================
--  一级分类：parent_id IS NULL      （如"数码电子"）
--  二级分类：parent_id = 一级的id    （如"手机"）
--
--  ⚠️ 参考项目的严重教训：
--     它的前端把分类 id 硬编码成 156 / 164（BookPublish.vue:29,37），
--     换一台机器导入数据后 id 变了，发布商品就选不中分类。
--     所以：二级分类的 parent_id **必须用子查询取**，不能写死数字。
--
--  例：SET @pid = (SELECT id FROM category WHERE name = '数码电子' AND parent_id IS NULL);
--      INSERT INTO category (name, parent_id) VALUES ('手机', @pid);
-- =============================================================================
CREATE TABLE `category` (
    `id`          int         NOT NULL AUTO_INCREMENT            COMMENT '分类ID',
    `name`        varchar(50) NOT NULL                           COMMENT '分类名称',
    `parent_id`   int             DEFAULT NULL                   COMMENT '父分类ID；NULL=一级分类',
    `sort_order`  int         NOT NULL DEFAULT 0                 COMMENT '排序值（越小越靠前）',
    `create_time` datetime        DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)                                COMMENT '分类名唯一（注意：是全库唯一，二级分类名也不能撞车）',
    KEY `idx_parent_id` (`parent_id`)                            COMMENT '按父分类查子分类',
    -- 自关联外键：删父分类时子分类自动升级为一级（SET NULL），不会连带删除
    CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`)
        REFERENCES `category` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '商品分类表（两级：parent_id IS NULL 为一级）';


-- =============================================================================
--  表 3 / 5：product  商品表
-- =============================================================================
--  status 五种取值，必须和 Java 枚举 ProductStatus 一一对应：
--      0 待审核  —— 用户新发布，等管理员审。列表页【不展示】
--      1 在售    —— 审核通过，正常展示，可以联系卖家
--      2 已售出  —— 卖家手动点"已卖出"。还会展示（让买家知道这价能成交），但不能联系
--      3 已下架  —— 卖家主动下架。列表页不展示
--      4 已拒绝  —— 管理员审核不通过。只有卖家本人在"我的发布"里能看到原因
--
--  ⚠️ 参考项目的 bug：它把"下架"和"已售出"都用 status=2 表示
--     （BookController:158），结果卖家一下架，商品就显示成"已售出"。
--     我们这里 2 和 3 分开。
--
--  favorite_count 是冗余字段（为了列表页不用 LEFT JOIN 就能显示"想要数"），
--  每次收藏/取消收藏都必须同步 +/- 1。
-- =============================================================================
CREATE TABLE `product` (
    `id`              int           NOT NULL AUTO_INCREMENT      COMMENT '商品ID',
    `title`           varchar(100)  NOT NULL                     COMMENT '标题',
    `description`     text                                       COMMENT '详细描述',
    `category_id`     int               DEFAULT NULL             COMMENT '分类ID（建议挂二级分类）',
    `condition_level` varchar(20)       DEFAULT NULL             COMMENT '成色：全新/几乎全新/轻微使用痕迹/明显使用痕迹',
    `price`           decimal(10,2) NOT NULL                     COMMENT '售价（元）',
    `original_price`  decimal(10,2)     DEFAULT NULL             COMMENT '购入原价（元），用于显示折扣',
    `images`          text                                       COMMENT '图片URL数组，存 JSON 字符串，如 ["https://...","https://..."]',
    `brand`           varchar(50)       DEFAULT NULL             COMMENT '品牌/型号',
    `trade_place`     varchar(100)      DEFAULT NULL             COMMENT '线下交易地点，如"东区食堂门口"',
    `seller_id`       int           NOT NULL                     COMMENT '卖家ID → user.id',
    `status`          tinyint       NOT NULL DEFAULT 0           COMMENT '0待审核/1在售/2已售出/3已下架/4已拒绝',
    `view_count`      int           NOT NULL DEFAULT 0           COMMENT '浏览量（被人看过几次）',
    `favorite_count`  int           NOT NULL DEFAULT 0           COMMENT '想要数（冗余，必须等于 favorite 表里该商品的行数）',
    `create_time`     datetime          DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间（列表默认按它倒序）',
    `update_time`     datetime          DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间（数据库自动维护）',
    PRIMARY KEY (`id`),
    KEY `idx_seller_id`   (`seller_id`)      COMMENT '查"我发布的"',
    KEY `idx_category_id` (`category_id`)    COMMENT '按分类筛选',
    KEY `idx_status`      (`status`)         COMMENT '按状态筛选（列表页恒带 status=1）',
    KEY `idx_create_time` (`create_time`)    COMMENT '按发布时间排序',
    KEY `idx_price`       (`price`)          COMMENT '按价格排序/筛选',
    CONSTRAINT `chk_product_price`  CHECK (`price` >= 0),
    CONSTRAINT `chk_product_status` CHECK (`status` BETWEEN 0 AND 4),
    -- 卖家注销 → 他的商品一起删（本项目没有注销入口，这条是数据兜底）
    CONSTRAINT `fk_product_seller` FOREIGN KEY (`seller_id`)
        REFERENCES `user` (`id`) ON DELETE CASCADE,
    -- 分类被删 → 商品的 category_id 置空（商品本身要保留！不能连带删掉）
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`)
        REFERENCES `category` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '商品表';


-- =============================================================================
--  表 4 / 5：favorite  收藏表（页面上的"想要"）
-- =============================================================================
--  这张表记录的是【当前状态】：有行 = 还想要，没行 = 不想要了。
--  所以 uk_user_product 唯一键必不可少 —— 它从数据库层面杜绝
--  "同一个人把同一件商品收藏两次"（参考项目靠 Java 代码 if 判断，
--   并发下会插进两条，导致 favorite_count 虚高）。
--
--  ⚠️ 它和 user_behavior 的区别：
--     favorite       = 状态，可增可删，每人每商品最多 1 行
--     user_behavior  = 流水，只追加，每人每商品可以有很多行
--     千万不要合并成一张表。
-- =============================================================================
CREATE TABLE `favorite` (
    `id`          int      NOT NULL AUTO_INCREMENT               COMMENT '收藏ID',
    `user_id`     int      NOT NULL                              COMMENT '用户ID → user.id',
    `product_id`  int      NOT NULL                              COMMENT '商品ID → product.id',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP         COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)       COMMENT '同一用户对同一商品只能收藏一次',
    KEY `idx_user_time` (`user_id`, `create_time`)               COMMENT '查"我的收藏"，按时间倒序',
    KEY `idx_product_id` (`product_id`)                          COMMENT '查"谁想要这件商品"',
    CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`)
        REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_favorite_product` FOREIGN KEY (`product_id`)
        REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '收藏表（当前状态：有行=想要，无行=取消）';


-- =============================================================================
--  表 5 / 5：user_behavior  用户行为表
-- =============================================================================
--  behavior_type 四种取值，对应 Java 枚举 BehaviorType：
--      1 浏览
--      2 收藏
--      3 咨询      —— 用户点了"获取联系方式"，这是本平台最核心的转化指标
--      4 取消收藏
--
--  这张表有两个用途：
--    (1) 卖家在"我的发布"里看到 contactCount（有多少人想要联系方式）→ 决定要不要降价
--    (2) 管理后台的"咨询记录"页面，取代参考项目的"订单管理"
--
--  ⚠️ 参考项目的 user_behavior 表【从头到尾没写过一行数据】——
--     表建了、实体类写了、Mapper 写了，但没有任何业务代码往里插数据。
--     本项目从 S5 开始每个相关接口都要写。
--
--  注意：这里【不能】加唯一键！同一用户浏览同一商品 10 次就是 10 行。
-- =============================================================================
CREATE TABLE `user_behavior` (
    `id`            int      NOT NULL AUTO_INCREMENT             COMMENT '行为ID',
    `user_id`       int      NOT NULL                            COMMENT '用户ID → user.id',
    `product_id`    int      NOT NULL                            COMMENT '商品ID → product.id',
    `behavior_type` tinyint  NOT NULL                            COMMENT '1浏览 / 2收藏 / 3咨询 / 4取消收藏',
    `behavior_time` datetime     DEFAULT CURRENT_TIMESTAMP       COMMENT '行为发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time`     (`user_id`, `behavior_time`)         COMMENT '查某用户的行为时间线（推荐用）',
    KEY `idx_product_id`    (`product_id`)                       COMMENT '统计某商品被多少人咨询',
    KEY `idx_behavior_type` (`behavior_type`)                    COMMENT '按行为类型统计（后台看"咨询量"）',
    CONSTRAINT `chk_behavior_type` CHECK (`behavior_type` BETWEEN 1 AND 4),
    CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`)
        REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_behavior_product` FOREIGN KEY (`product_id`)
        REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户行为表（浏览/收藏/咨询流水，只追加不修改）';


-- =============================================================================
--  自检：执行完应该看到 5 张表
-- =============================================================================
SHOW TABLES;
