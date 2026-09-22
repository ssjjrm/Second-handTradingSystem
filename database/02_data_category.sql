-- =============================================================================
--  二手交易平台 · 分类数据
--  文件：database/02_data_category.sql
--  阶段：S1.3
-- =============================================================================
--
--  【数据结构】两级树，靠 category.parent_id 自关联
--      一级分类：parent_id IS NULL          （9 个）
--      二级分类：parent_id = 某个一级分类的 id（41 个）
--
--  【本脚本的两条铁律】
--
--   1. 二级分类的 parent_id 必须用【子查询】取，绝不能写死数字。
--      参考项目的血泪教训：它的前端把分类 id 硬编码成 156 / 164
--      （BookPublish.vue:29,37 和 BookDetail.vue:68,76），
--      换台机器重新导入数据后 id 变了，发布商品时就选不中分类，
--      而且页面上还不报错 —— 极难排查。
--
--      正确写法：
--          SET @pid = (SELECT id FROM category WHERE name = '数码电子' AND parent_id IS NULL);
--          INSERT INTO category (name, parent_id, sort_order) VALUES ('手机', @pid, 1);
--
--   2. 二级分类名不能重复（含和一级分类名也不能重复），
--      因为 category 表上有 uk_name 全局唯一索引。
--      比如不能出现两个叫"其他"的二级分类。
--
--  【可重复执行】
--      用 INSERT ... ON DUPLICATE KEY UPDATE：第一次是插入，
--      再执行一次就变成"按名字更新 parent_id 和 sort_order"，不会报错。
--      （这里刻意不用 DELETE FROM category —— 那样会把已有商品的
--        category_id 一起置空，见 fk_product_category 的 ON DELETE SET NULL）
--
-- =============================================================================

SET NAMES utf8mb4;
USE `secondhand_shop`;


-- =============================================================================
--  第 1 步：插入 9 个一级分类（parent_id 留空）
-- =============================================================================
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('数码电子', NULL, 1),
    ('家用电器', NULL, 2),
    ('图书文具', NULL, 3),
    ('服饰鞋包', NULL, 4),
    ('运动户外', NULL, 5),
    ('日用百货', NULL, 6),
    ('美妆个护', NULL, 7),
    ('乐器文创', NULL, 8),
    ('其他闲置', NULL, 9)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;


-- =============================================================================
--  第 2 步：逐个一级分类，挂上它的二级分类
--  每组固定三步：SET @pid = ...（取父 id）→ INSERT 子分类 → （下一组）
-- =============================================================================

-- ---------- 1. 数码电子（6 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '数码电子' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('手机',     @pid, 1),
    ('笔记本',   @pid, 2),
    ('平板',     @pid, 3),
    ('耳机',     @pid, 4),
    ('相机',     @pid, 5),
    ('智能设备', @pid, 6)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 2. 家用电器（5 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '家用电器' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('小家电',   @pid, 1),
    ('厨房电器', @pid, 2),
    ('生活电器', @pid, 3),
    ('个护电器', @pid, 4),
    ('影音设备', @pid, 5)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 3. 图书文具（4 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '图书文具' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('教材',     @pid, 1),
    ('考研资料', @pid, 2),
    ('课外书',   @pid, 3),
    ('文具',     @pid, 4)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 4. 服饰鞋包（4 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '服饰鞋包' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('男装', @pid, 1),
    ('女装', @pid, 2),
    ('鞋靴', @pid, 3),
    ('箱包', @pid, 4)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 5. 运动户外（5 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '运动户外' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('健身器材', @pid, 1),
    ('球类',     @pid, 2),
    ('骑行',     @pid, 3),
    ('露营',     @pid, 4),
    ('游泳装备', @pid, 5)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 6. 日用百货（5 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '日用百货' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('收纳',   @pid, 1),
    ('清洁',   @pid, 2),
    ('厨具',   @pid, 3),
    ('日用品', @pid, 4),
    ('家纺',   @pid, 5)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 7. 美妆个护（4 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '美妆个护' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('护肤',     @pid, 1),
    ('彩妆',     @pid, 2),
    ('洗护',     @pid, 3),
    ('美妆工具', @pid, 4)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 8. 乐器文创（4 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '乐器文创' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('吉他',     @pid, 1),
    ('电子琴',   @pid, 2),
    ('其他乐器', @pid, 3),
    ('文创',     @pid, 4)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;

-- ---------- 9. 其他闲置（4 个） ----------
SET @pid = (SELECT `id` FROM `category` WHERE `name` = '其他闲置' AND `parent_id` IS NULL);
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
    ('票券',   @pid, 1),
    ('礼品卡', @pid, 2),
    ('代购',   @pid, 3),
    ('杂项',   @pid, 4)
AS new
ON DUPLICATE KEY UPDATE
    `parent_id`  = new.`parent_id`,
    `sort_order` = new.`sort_order`;


-- =============================================================================
--  自检：执行完应该看到 9 行，每行二级数量 4~6
-- =============================================================================
SELECT  p.`sort_order`      AS `一级序号`,
        p.`name`            AS `一级分类`,
        COUNT(c.`id`)       AS `二级数量`
FROM `category` p
LEFT JOIN `category` c ON c.`parent_id` = p.`id`
WHERE p.`parent_id` IS NULL
GROUP BY p.`id`, p.`name`, p.`sort_order`
ORDER BY p.`sort_order`;

SELECT COUNT(*) AS `分类总数`, SUM(`parent_id` IS NULL) AS `一级数`, SUM(`parent_id` IS NOT NULL) AS `二级数`
FROM `category`;
