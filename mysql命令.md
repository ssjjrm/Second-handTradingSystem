以下默认在 **MySQL 客户端** 中执行；标注 `[shell]` 的在系统终端执行。SQL 一般以 `;` 结尾，`\G` 可代替 `;` 竖排显示。

## 1. 连接与退出

```bash
# [shell] 连接本机 MySQL
mysql -u root -p

# [shell] 连接远程 MySQL
mysql -h 127.0.0.1 -P 3306 -u root -p

# [shell] 直接进入某个数据库
mysql -u root -p db_name

# [shell] 不进入交互，直接执行 SQL
mysql -u root -p -e "SHOW DATABASES;"

# [shell] 查看客户端版本
mysql --version
```

```sql
-- MySQL 客户端内退出
exit;
quit;
\q
```

## 2. 数据库操作

```sql
SHOW DATABASES;                         -- 查看所有数据库
CREATE DATABASE db_name;                -- 创建数据库
CREATE DATABASE db_name DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_name;                            -- 切换数据库
SELECT DATABASE();                      -- 查看当前数据库
DROP DATABASE db_name;                  -- 删除数据库
SHOW CREATE DATABASE db_name\G          -- 查看建库语句
ALTER DATABASE db_name CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 3. 表操作

```sql
SHOW TABLES;                            -- 查看当前库所有表
SHOW TABLES LIKE 'user%';               -- 模糊查表
DESC table_name;                        -- 查看表结构
SHOW COLUMNS FROM table_name;           -- 查看列
SHOW CREATE TABLE table_name\G          -- 查看建表语句
SHOW TABLE STATUS LIKE 'table_name'\G   -- 查看表状态

CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE table_name ADD COLUMN age INT;
ALTER TABLE table_name DROP COLUMN age;
ALTER TABLE table_name MODIFY COLUMN name VARCHAR(100);
ALTER TABLE table_name CHANGE COLUMN old_col new_col VARCHAR(100);
ALTER TABLE table_name RENAME TO new_table;

RENAME TABLE old_table TO new_table;
TRUNCATE TABLE table_name;              -- 清空表，不可回滚
DROP TABLE table_name;                  -- 删除表
```

## 4. 数据增删改查

```sql
SELECT * FROM table_name LIMIT 10;
SELECT id, name FROM table_name WHERE id = 1;
SELECT * FROM table_name ORDER BY id DESC LIMIT 10;
SELECT COUNT(*) FROM table_name;
SELECT * FROM table_name WHERE name LIKE '%张%';

INSERT INTO table_name (name, age) VALUES ('张三', 20);
INSERT INTO table_name (name, age) VALUES ('李四', 21), ('王五', 22);

UPDATE table_name SET name = '张三丰' WHERE id = 1;

DELETE FROM table_name WHERE id = 1;
```

## 5. 索引与约束

```sql
SHOW INDEX FROM table_name;             -- 查看索引

CREATE INDEX idx_name ON table_name (name);
ALTER TABLE table_name ADD INDEX idx_name (name);
ALTER TABLE table_name ADD UNIQUE (email);
ALTER TABLE table_name ADD PRIMARY KEY (id);

DROP INDEX idx_name ON table_name;
ALTER TABLE table_name DROP PRIMARY KEY;
```

## 6. 用户与权限

```sql
CREATE USER 'user'@'localhost' IDENTIFIED BY 'password';
CREATE USER 'user'@'%' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON db_name.* TO 'user'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON db_name.* TO 'user'@'%';
FLUSH PRIVILEGES;

SHOW GRANTS FOR 'user'@'localhost';
REVOKE ALL PRIVILEGES ON db_name.* FROM 'user'@'localhost';

ALTER USER 'user'@'localhost' IDENTIFIED BY 'newpassword';
DROP USER 'user'@'localhost';
```

## 7. 备份与恢复

```bash
# [shell] 备份单个数据库
mysqldump -u root -p db_name > db_name.sql

# [shell] 备份单张表
mysqldump -u root -p db_name table_name > table_name.sql

# [shell] 只备份表结构
mysqldump -u root -p -d db_name > schema.sql

# [shell] 备份所有数据库
mysqldump -u root -p --all-databases > all_databases.sql

# [shell] 恢复数据库
mysql -u root -p db_name < db_name.sql
```

```sql
-- MySQL 客户端内执行 SQL 文件
source /path/to/db_name.sql;
```

## 8. 状态、进程与排查

```sql
SELECT VERSION();
SELECT NOW();
SELECT USER(), CURRENT_USER();

SHOW STATUS LIKE 'Threads_connected';
SHOW VARIABLES LIKE 'max_connections';
SHOW VARIABLES LIKE 'character_set%';

SHOW PROCESSLIST;
SHOW FULL PROCESSLIST;
KILL 12345;                             -- 杀掉指定连接/进程

EXPLAIN SELECT * FROM table_name WHERE id = 1;
SHOW WARNINGS;
SHOW ERRORS;
SHOW ENGINE INNODB STATUS\G
```

## 9. 事务

```sql
START TRANSACTION;
-- 执行 SQL
COMMIT;
-- 或回滚
ROLLBACK;
```

## 10. mysqladmin 常用管理命令

```bash
# [shell]
mysqladmin -u root -p status
mysqladmin -u root -p processlist
mysqladmin -u root -p create db_name
mysqladmin -u root -p drop db_name
mysqladmin -u root -p shutdown
```

> 注意：生产环境慎用 `DROP DATABASE`、`DROP TABLE`、`TRUNCATE TABLE`、不带 `WHERE` 的 `DELETE` / `UPDATE`，操作前先备份。