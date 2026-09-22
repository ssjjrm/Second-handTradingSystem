-- =============================================================================
--  二手交易平台 · 一键重置数据库
--  文件：database/99_reset.sql
--  阶段：S1.5
-- =============================================================================
--
--  【作用】
--      开发阶段会反复重建数据库。这个脚本 = 删库 + 建库 + 重新导入全部数据，
--      一条命令回到"刚初始化完"的状态，不用手敲 DROP DATABASE。
--
--  【怎么执行】
--      必须在【项目根目录】下执行（因为里面的 source 用的是相对路径）：
--
--          mysql -uroot -p123456 --default-character-set=utf8mb4 < database\99_reset.sql
--
--      或者进 mysql 交互客户端后：
--          source database/99_reset.sql
--
--  ⚠️⚠️ 安全警告 ⚠️⚠️
--      本脚本只删 `secondhand_shop`（本项目的库）。
--      绝对不要去删 `book_cycle` —— 那是参考项目的数据库，
--      重新导入它很麻烦。改动本文件时请务必确认库名没写错。
--
--  【为什么命名成 99_ 开头】
--      scripts\db-init.ps1 在自动导入时会【跳过】文件名以 99_ 开头的脚本，
--      否则它会把这个刚要建好的库又删掉。
--
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 删库（只删本项目这一个库） ----------
DROP DATABASE IF EXISTS `secondhand_shop`;

-- ---------- 2. 建空库 ----------
CREATE DATABASE `secondhand_shop`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- ---------- 3. 重新导入（01 建表 → 02 分类 → 03 数据） ----------
-- 路径相对于【执行 mysql 时的当前目录】，所以必须在项目根目录下运行本脚本。
source database/01_schema_secondhand_shop.sql
source database/02_data_category.sql
source database/03_data_seed.sql

-- ---------- 4. 收尾确认 ----------
SELECT '数据库已重置完成' AS `结果`;
SHOW TABLES FROM `secondhand_shop`;
