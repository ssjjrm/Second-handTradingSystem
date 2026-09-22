-- =============================================================================
--  二手交易平台 · 种子数据（测试账号 + 商品 + 收藏 + 行为）
--  文件：database/03_data_seed.sql
--  阶段：S1.4
-- =============================================================================
--
--  【依赖】必须先执行 01_schema_secondhand_shop.sql 和 02_data_category.sql
--
--  【本脚本产出】
--      6 个测试账号
--      66 件商品（分布 5 个卖家、9 个一级分类、39 个二级分类）
--      若干收藏记录 + 行为记录（浏览/收藏/咨询/取消收藏 四种都覆盖）
--
--  【可重复执行 —— 关键设计】
--      product 表【没有业务唯一键】（标题允许重名），所以没法用
--      INSERT ... ON DUPLICATE KEY 做幂等。这里改用另一种办法：
--
--          TRUNCATE favorite / user_behavior / product  三张表 → 重新插入
--
--      好处：自增 id 每次从 1 开始，商品 id 顺序完全确定，
--            后面 favorite / user_behavior 引用起来不会错位。
--
--      user 和 category 两张表【不清空】（它们有 uk 唯一键，
--      用 ON DUPLICATE KEY UPDATE 就能安全重复执行）。
--
--  ⚠️ 注意：product 被 favorite / user_behavior 的外键引用，
--     MySQL 不允许直接 TRUNCATE 这种表，必须先关掉外键检查。
--
--  【密码为什么是密文】
--      这 6 个账号的 password 是 BCrypt 密文（$2b$10$ 开头，60 字符），
--      对应明文写在下面的注释里。绝不能在数据库里存明文。
--      （参考项目的 6 个账号密码全是明文，这是要改掉的缺陷之一）
--
-- =============================================================================

SET NAMES utf8mb4;
USE `secondhand_shop`;


-- =============================================================================
--  第 1 步：清空商品相关三张表（顺序：先删引用的，再删被引用的）
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `favorite`;
TRUNCATE TABLE `user_behavior`;
TRUNCATE TABLE `product`;
SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================
--  第 2 步：6 个测试账号
-- =============================================================================
--  账号        明文密码    角色     用途
--  admin       admin123   admin   管理员（审核商品、看咨询记录）
--  student01   123456     user    主要卖家，发布 25 件
--  student02   123456     user    卖家，发布 15 件
--  student03   123456     user    卖家，发布 12 件
--  buyer01     123456     user    买家，发布 8 件 + 有收藏和咨询
--  buyer02     123456     user    买家，发布 6 件 + 有收藏
-- =============================================================================
INSERT INTO `user` (`username`, `password`, `nickname`, `wechat`, `qq`, `phone`, `avatar`, `role`, `status`) VALUES
    ('admin',     '$2b$10$zR3X1IqDIlF4k54qII5UoOkPvqBENkCOoEL9y.g8OIwcI82dVvtcK', '平台管理员',   NULL,             NULL,          '13800000001', NULL, 'admin', 1),
    ('student01', '$2b$10$7XBOqdaeDOc/C0oxSpu77uKo1pQ0dVHn2opHocyAfatgGfSOV/92m', '阿哲的闲置小铺', 'zhe_ershou',     '1001001001',  '13800000002', NULL, 'user',  1),
    ('student02', '$2b$10$KMIdO2kbVXstSBgHCNLrQuu46LOR53XaQI90W.2HJHg7R9kRquVrW', '小雨清理柜子',   'xiaoyu_sell',    '1001001002',  '13800000003', NULL, 'user',  1),
    ('student03', '$2b$10$NjdrYYrBaSAzj.kLrHygUu94Eg5BUtDaoBmKEqcQnqMbIKzhlFQgu', '毕业季出清',     'biye_qingcang',  '1001001003',  '13800000004', NULL, 'user',  1),
    ('buyer01',   '$2b$10$cAzxt.d4/CCrYhVPDYxl7.Zk1Z2X0qTf63G2LoMaHkvVQLDcy6Vvm', '想捡漏的同学',   'jianlou_2024',   '1001001004',  '13800000005', NULL, 'user',  1),
    ('buyer02',   '$2b$10$Mbj3hU1HVS6gEO6RoBcYCumHSJfuHLBCxb5SEJcBZLQJtMNQW7ZJC', '宿舍收纳中',     'shoushe_nina',   '1001001005',  '13800000006', NULL, 'user',  1)
AS new
ON DUPLICATE KEY UPDATE
    `password` = new.`password`,
    `nickname` = new.`nickname`,
    `wechat`   = new.`wechat`,
    `qq`       = new.`qq`,
    `phone`    = new.`phone`,
    `role`     = new.`role`,
    `status`   = new.`status`;


-- =============================================================================
--  第 3 步：把要用的 id 存进变量
-- =============================================================================
--  为什么不直接写 1 / 2 / 3？
--      参考项目把分类 id 硬编码成 156 / 164，换台机器导数据 id 就变了。
--      这里全部用【按名字查】的方式取 id，无论 id 是多少都不会错。
-- =============================================================================

-- 5 个卖家 / 买家
SET @u_s1 = (SELECT `id` FROM `user` WHERE `username` = 'student01');
SET @u_s2 = (SELECT `id` FROM `user` WHERE `username` = 'student02');
SET @u_s3 = (SELECT `id` FROM `user` WHERE `username` = 'student03');
SET @u_b1 = (SELECT `id` FROM `user` WHERE `username` = 'buyer01');
SET @u_b2 = (SELECT `id` FROM `user` WHERE `username` = 'buyer02');

-- 数码电子
SET @c_phone     = (SELECT `id` FROM `category` WHERE `name` = '手机');
SET @c_laptop    = (SELECT `id` FROM `category` WHERE `name` = '笔记本');
SET @c_tablet    = (SELECT `id` FROM `category` WHERE `name` = '平板');
SET @c_headphone = (SELECT `id` FROM `category` WHERE `name` = '耳机');
SET @c_camera    = (SELECT `id` FROM `category` WHERE `name` = '相机');
SET @c_smart     = (SELECT `id` FROM `category` WHERE `name` = '智能设备');
-- 家用电器
SET @c_smallapp  = (SELECT `id` FROM `category` WHERE `name` = '小家电');
SET @c_kitchen   = (SELECT `id` FROM `category` WHERE `name` = '厨房电器');
SET @c_life      = (SELECT `id` FROM `category` WHERE `name` = '生活电器');
SET @c_personal  = (SELECT `id` FROM `category` WHERE `name` = '个护电器');
SET @c_av        = (SELECT `id` FROM `category` WHERE `name` = '影音设备');
-- 图书文具
SET @c_textbook  = (SELECT `id` FROM `category` WHERE `name` = '教材');
SET @c_kaoyan    = (SELECT `id` FROM `category` WHERE `name` = '考研资料');
SET @c_reading   = (SELECT `id` FROM `category` WHERE `name` = '课外书');
SET @c_stationery= (SELECT `id` FROM `category` WHERE `name` = '文具');
-- 服饰鞋包
SET @c_menswear  = (SELECT `id` FROM `category` WHERE `name` = '男装');
SET @c_womenswear= (SELECT `id` FROM `category` WHERE `name` = '女装');
SET @c_shoes     = (SELECT `id` FROM `category` WHERE `name` = '鞋靴');
SET @c_bags      = (SELECT `id` FROM `category` WHERE `name` = '箱包');
-- 运动户外
SET @c_fitness   = (SELECT `id` FROM `category` WHERE `name` = '健身器材');
SET @c_ball      = (SELECT `id` FROM `category` WHERE `name` = '球类');
SET @c_cycling   = (SELECT `id` FROM `category` WHERE `name` = '骑行');
SET @c_camping   = (SELECT `id` FROM `category` WHERE `name` = '露营');
SET @c_swim      = (SELECT `id` FROM `category` WHERE `name` = '游泳装备');
-- 日用百货
SET @c_storage   = (SELECT `id` FROM `category` WHERE `name` = '收纳');
SET @c_clean     = (SELECT `id` FROM `category` WHERE `name` = '清洁');
SET @c_cookware  = (SELECT `id` FROM `category` WHERE `name` = '厨具');
SET @c_daily     = (SELECT `id` FROM `category` WHERE `name` = '日用品');
SET @c_home      = (SELECT `id` FROM `category` WHERE `name` = '家纺');
-- 美妆个护
SET @c_skincare  = (SELECT `id` FROM `category` WHERE `name` = '护肤');
SET @c_makeup    = (SELECT `id` FROM `category` WHERE `name` = '彩妆');
SET @c_haircare  = (SELECT `id` FROM `category` WHERE `name` = '洗护');
SET @c_beautytool= (SELECT `id` FROM `category` WHERE `name` = '美妆工具');
-- 乐器文创
SET @c_guitar    = (SELECT `id` FROM `category` WHERE `name` = '吉他');
SET @c_otherinst = (SELECT `id` FROM `category` WHERE `name` = '其他乐器');
SET @c_creative  = (SELECT `id` FROM `category` WHERE `name` = '文创');
-- 其他闲置
SET @c_ticket    = (SELECT `id` FROM `category` WHERE `name` = '票券');
SET @c_giftcard  = (SELECT `id` FROM `category` WHERE `name` = '礼品卡');
SET @c_misc      = (SELECT `id` FROM `category` WHERE `name` = '杂项');


-- =============================================================================
--  第 4 步：66 件商品
-- =============================================================================
--  status 取值：0待审核 / 1在售 / 2已售出 / 3已下架 / 4已拒绝
--  分布：46 在售 + 7 待审 + 7 已售出 + 3 已下架 + 3 已拒绝
-- =============================================================================

-- ---------- 卖家 1：student01（25 件，数码电子为主） ----------
INSERT INTO `product` (`title`, `description`, `category_id`, `condition_level`, `price`, `original_price`, `images`, `brand`, `trade_place`, `seller_id`, `status`) VALUES
('【九成新】索尼 WH-1000XM5 头戴式降噪耳机 宿舍自提', '去年双十一买的，降噪真的顶，在图书馆戴着完全听不到空调声。因为换了别的耳机所以出，原装耳机包和充电线都在，无划痕。', @c_headphone, '轻微使用痕迹', 1580.00, 2399.00, '["https://picsum.photos/seed/sh01a/400/400","https://picsum.photos/seed/sh01b/400/400"]', '索尼', '3号宿舍楼下', @u_s1, 2),
('【几乎全新】iPad Air 5 64G 紫色 考研用完出', '考研期间买的，就用来刷网课和记笔记，贴了钢化膜带保护壳，屏幕无划痕，电池健康度还行。送原装充电头和一支平替笔。', @c_tablet, '几乎全新', 2450.00, 4399.00, '["https://picsum.photos/seed/sh02a/400/400","https://picsum.photos/seed/sh02b/400/400","https://picsum.photos/seed/sh02c/400/400"]', 'Apple', '图书馆一楼大厅', @u_s1, 1),
('【八成新】小米 13 8+256 黑色 无磕碰', '用了大概一年半，一直带壳带膜，边框有一点点使用痕迹但不明显。功能全部正常，无维修无进水，可当面验机。', @c_phone, '轻微使用痕迹', 1450.00, 3999.00, '["https://picsum.photos/seed/sh03a/400/400","https://picsum.photos/seed/sh03b/400/400"]', '小米', '南门快递站旁', @u_s1, 0),
('【九成新】佳能 EOS 200D II 单反套机 18-55 镜头', '大一入的坑，快门数一万二左右，拍过几次校园活动。因为现在都用手机了所以出，镜头无霉无雾，带原装肩带和两块电池。', @c_camera, '明显使用痕迹', 2280.00, 4199.00, '["https://picsum.photos/seed/sh04a/400/400","https://picsum.photos/seed/sh04b/400/400"]', '佳能', '东区食堂门口', @u_s1, 1),
('【九成新】联想小新 Pro 16 2022款 R7 16G+512G', '大二买的，主要写代码和跑虚拟机，散热还行。键盘无油光，屏幕无坏点，电池循环 180 次左右。可当面跑分。', @c_laptop, '轻微使用痕迹', 3150.00, 5499.00, '["https://picsum.photos/seed/sh05a/400/400","https://picsum.photos/seed/sh05b/400/400"]', '联想', '图书馆一楼大厅', @u_s1, 1),
('【九成新】Kindle Paperwhite 4 8G 带原装皮套', '吃灰两年了，买的时候想着多看书结果一本没看完。屏幕无划痕，背光正常，送原装皮套和一根数据线。', @c_tablet, '轻微使用痕迹', 420.00, 998.00, '["https://picsum.photos/seed/sh06a/400/400"]', '亚马逊', '3号宿舍楼下', @u_s1, 1),
('【几乎全新】小米手环 8 NFC 版 可以刷门禁', '买了两周，发现我手机已经能刷门禁了所以闲置。表带无磨损，充电器全新没拆，包装盒还在。', @c_smart, '几乎全新', 130.00, 249.00, '["https://picsum.photos/seed/sh07a/400/400","https://picsum.photos/seed/sh07b/400/400"]', '小米', '体育馆西门', @u_s1, 3),
('【全新未拆】罗技 MX Master 3S 无线鼠标 石墨黑', '买重复了，京东自营的，塑封都没拆。静音微动，做设计写代码都很舒服。有电子发票，可保修。', @c_smart, '全新', 520.00, 699.00, '["https://picsum.photos/seed/sh08a/400/400"]', '罗技', '南门快递站旁', @u_s1, 1),
('【九成新】戴森 V8 无绳吸尘器 换新出', '宿舍打扫用的，吸力还是很强，电池续航大概还有二十多分钟。换了新款所以出，所有吸头都在。', @c_life, '轻微使用痕迹', 980.00, 2990.00, '["https://picsum.photos/seed/sh09a/400/400","https://picsum.photos/seed/sh09b/400/400"]', '戴森', '东区食堂门口', @u_s1, 1),
('【几乎全新】米家空气净化器 4 Lite', '买来除甲醛的，用了不到两个月，滤芯还剩 90% 以上。因为要搬宿舍所以出，包装箱都留着可以搬。', @c_life, '几乎全新', 420.00, 699.00, '["https://picsum.photos/seed/sh10a/400/400"]', '米家', '3号宿舍楼下', @u_s1, 1),
('【九成新】美的电热水壶 1.5L 不锈钢内胆', '宿舍烧水用的，烧开自动断电，内胆有点水垢痕迹，可以用柠檬酸洗掉。功能完全正常。', @c_kitchen, '轻微使用痕迹', 45.00, 129.00, '["https://picsum.photos/seed/sh11a/400/400"]', '美的', '3号宿舍楼下', @u_s1, 1),
('【几乎全新】飞利浦电动牙刷 HX3216 含 3 个刷头', '用了一个月，后来去正畸医生说不能用电动牙刷。机身无水垢，刷头都是全新未拆的。', @c_personal, '几乎全新', 90.00, 199.00, '["https://picsum.photos/seed/sh12a/400/400","https://picsum.photos/seed/sh12b/400/400"]', '飞利浦', '图书馆一楼大厅', @u_s1, 0),
('【九成新】漫步者 R1700BT 桌面音箱 蓝牙版', '宿舍听歌用的，木质箱体音质不错，低音够用。因为毕业要寄回家太麻烦所以出，可当面试听。', @c_av, '轻微使用痕迹', 380.00, 699.00, '["https://picsum.photos/seed/sh13a/400/400","https://picsum.photos/seed/sh13b/400/400"]', '漫步者', '东区食堂门口', @u_s1, 1),
('【九成新】迪卡侬 20kg 可调节哑铃 一对', '疫情期间买的，练了半年就吃灰了。配重片齐全，杆子无变形。太重了不方便寄，只支持自提。', @c_fitness, '轻微使用痕迹', 180.00, 399.00, '["https://picsum.photos/seed/sh14a/400/400"]', '迪卡侬', '体育馆西门', @u_s1, 1),
('【九成新】阿迪达斯 Telstar 足球 5 号 比赛用球', '院队训练用的，踢了大概十来次，球面有轻微磨损但不影响使用，气密性很好。', @c_ball, '轻微使用痕迹', 60.00, 159.00, '["https://picsum.photos/seed/sh15a/400/400"]', '阿迪达斯', '体育馆西门', @u_s1, 2),
('【七成新】捷安特 ATX 660 山地车 26 寸 可小刀', '大二买的通勤车，刹车变速都正常，上周刚换过链条。车架有几处掉漆，不影响骑。带一把 U 型锁。', @c_cycling, '明显使用痕迹', 850.00, 1898.00, '["https://picsum.photos/seed/sh16a/400/400","https://picsum.photos/seed/sh16b/400/400"]', '捷安特', '南门快递站旁', @u_s1, 1),
('【几乎全新】迪卡侬 2 秒快开帐篷 双人款', '就开学露营用过一次，收纳袋都还是新的。展开收起真的很快，新手友好。', @c_camping, '几乎全新', 150.00, 299.00, '["https://picsum.photos/seed/sh17a/400/400"]', '迪卡侬', '体育馆西门', @u_s1, 1),
('【九成新】《计算机网络 自顶向下方法》第 8 版', '课程教材，只翻过前六章，后面几乎全新。没有笔记涂画，书角有点卷。考完就出。', @c_textbook, '轻微使用痕迹', 55.00, 89.00, '["https://picsum.photos/seed/sh18a/400/400"]', NULL, '图书馆一楼大厅', @u_s1, 1),
('【六成新】考研数学 张宇 1000 题 2024 版', '刷过一遍，书里有大量铅笔笔记和演算痕迹，介意勿拍。题目完整无缺页，适合二刷。', @c_kaoyan, '明显使用痕迹', 35.00, 79.00, '["https://picsum.photos/seed/sh19a/400/400"]', NULL, '图书馆一楼大厅', @u_s1, 4),
('【几乎全新】《百年孤独》精装正版 南海出版社', '买的时候想做文艺青年，结果第一章没看完。书脊没折痕，无笔记。', @c_reading, '几乎全新', 22.00, 55.00, '["https://picsum.photos/seed/sh20a/400/400"]', NULL, '东区食堂门口', @u_s1, 0),
('【九成新】小米台灯 Pro 无频闪 可调色温', '宿舍学习用的，护眼效果不错，可以调亮度和色温。底座有点使用痕迹，功能正常。', @c_life, '轻微使用痕迹', 150.00, 349.00, '["https://picsum.photos/seed/sh21a/400/400"]', '小米', '3号宿舍楼下', @u_s1, 1),
('【几乎全新】收纳箱 三件套 加大号 可折叠', '搬宿舍的时候买的，结果尺寸买大了塞不进柜子。一次没用过，可以拆开叠平。', @c_storage, '几乎全新', 40.00, 99.00, '["https://picsum.photos/seed/sh22a/400/400"]', NULL, '3号宿舍楼下', @u_s1, 1),
('【九成新】苏泊尔不粘炒锅 32cm 带锅盖', '在外面租房时用的，涂层完好没有脱落，木柄无开裂。因为换电磁炉了所以出。', @c_cookware, '轻微使用痕迹', 65.00, 169.00, '["https://picsum.photos/seed/sh23a/400/400"]', '苏泊尔', '东区食堂门口', @u_s1, 1),
('【全新】星巴克星礼卡 200 元 全国通用', '生日收到的，我平时不喝咖啡所以转出。卡未绑定未刮开，当面充值到账后再确认。', @c_giftcard, '全新', 165.00, 200.00, '["https://picsum.photos/seed/sh24a/400/400"]', '星巴克', '东区食堂门口', @u_s1, 1),
('【全新】电影通兑券 2 张 有效期还有一年', '公司活动发的，学校附近影院都能用，线上选座直接兑换。两张一起出，不单卖。', @c_ticket, '全新', 50.00, 80.00, '["https://picsum.photos/seed/sh25a/400/400"]', NULL, '南门快递站旁', @u_s1, 1);

-- ---------- 卖家 2：student02（15 件，服饰 + 图书 + 美妆） ----------
INSERT INTO `product` (`title`, `description`, `category_id`, `condition_level`, `price`, `original_price`, `images`, `brand`, `trade_place`, `seller_id`, `status`) VALUES
('【九成新】Nike Air Force 1 纯白 42 码', '穿了不到十次，鞋底磨损很轻微，鞋面刷干净了。因为脚长了一点所以出，原鞋盒还在。', @c_shoes, '轻微使用痕迹', 380.00, 799.00, '["https://picsum.photos/seed/sy01a/400/400","https://picsum.photos/seed/sy01b/400/400"]', 'Nike', '3号宿舍楼下', @u_s2, 2),
('【九成新】Adidas 三叶草卫衣 男 L 码 黑色', '买大了一码，穿过三四次，无起球无脱线。洗过两次，颜色还是很正。', @c_menswear, '轻微使用痕迹', 120.00, 399.00, '["https://picsum.photos/seed/sy02a/400/400"]', '阿迪达斯', '东区食堂门口', @u_s2, 1),
('【几乎全新】UNIQLO 摇粒绒外套 女 M 码 米白', '去年冬天买的，就穿过一两次，一直挂着。无污渍无起球，很保暖。', @c_womenswear, '几乎全新', 80.00, 249.00, '["https://picsum.photos/seed/sy03a/400/400","https://picsum.photos/seed/sy03b/400/400"]', '优衣库', '图书馆一楼大厅', @u_s2, 1),
('【九成新】JanSport 双肩包 蓝色 大容量', '高中背到大学的，拉链都顺滑，内衬无破损。因为换电脑包了所以出。可装 15.6 寸笔记本。', @c_bags, '轻微使用痕迹', 130.00, 329.00, '["https://picsum.photos/seed/sy04a/400/400"]', 'JanSport', '南门快递站旁', @u_s2, 3),
('【九成新】《线性代数应该这样学》第 3 版', '考研时买的辅助读物，翻过一半，有少量铅笔标注可以擦掉。书很新。', @c_textbook, '轻微使用痕迹', 25.00, 49.00, '["https://picsum.photos/seed/sy05a/400/400"]', NULL, '图书馆一楼大厅', @u_s2, 1),
('【七成新】考研英语 黄皮书 真题解析 2010-2023', '做了一遍，有大量红笔批注和划线圈画，介意慎拍。解析很详细，适合二刷背单词。', @c_kaoyan, '明显使用痕迹', 40.00, 89.00, '["https://picsum.photos/seed/sy06a/400/400"]', NULL, '图书馆一楼大厅', @u_s2, 4),
('【全新】晨光中性笔 30 支装 黑色 0.5mm', '开学囤多了，一整盒没拆封。写起来很顺，考试用这个手感不错。', @c_stationery, '全新', 15.00, 35.00, '["https://picsum.photos/seed/sy07a/400/400"]', '晨光', '3号宿舍楼下', @u_s2, 1),
('【全新未开封】兰蔻小黑瓶精华 30ml 专柜正装', '妈妈送的，我用不惯这个质地所以转出。专柜小票还在，批号可查，绝对正品。', @c_skincare, '全新', 480.00, 1080.00, '["https://picsum.photos/seed/sy08a/400/400","https://picsum.photos/seed/sy08b/400/400"]', '兰蔻', '图书馆一楼大厅', @u_s2, 0),
('【几乎全新】完美日记眼影盘 九色 大地色系', '用过两三次，用化妆刷取的，没有直接用手指碰过。颜色很日常，新手也能驾驭。', @c_makeup, '几乎全新', 45.00, 99.00, '["https://picsum.photos/seed/sy09a/400/400"]', '完美日记', '3号宿舍楼下', @u_s2, 1),
('【几乎全新】戴森吹风机 HD08 紫红色', '去年生日买的，用了不到十次，风速和温度都正常，滤网很干净。全套风嘴都在，带收纳盒。', @c_personal, '几乎全新', 1650.00, 2999.00, '["https://picsum.photos/seed/sy10a/400/400","https://picsum.photos/seed/sy10b/400/400"]', '戴森', '东区食堂门口', @u_s2, 1),
('【九成新】雅马哈 F310 民谣吉他 41 寸 带琴包', '大一社团招新时买的，学了半年就放弃了。琴颈无变形，琴弦刚换过，音准没问题，送琴包和拨片。', @c_guitar, '轻微使用痕迹', 480.00, 999.00, '["https://picsum.photos/seed/sy11a/400/400","https://picsum.photos/seed/sy11b/400/400"]', '雅马哈', '体育馆西门', @u_s2, 1),
('【几乎全新】尤克里里 23 寸 单板 送教程书', '买来想学，结果弹了两天。音色比合板的好听，送一本入门教程和调音器。', @c_otherinst, '几乎全新', 180.00, 399.00, '["https://picsum.photos/seed/sy12a/400/400"]', NULL, '体育馆西门', @u_s2, 1),
('【几乎全新】《三体》全集 三册 重庆出版社', '看完就放书架上了，三本都没折痕，无笔记。科幻入门必读，一套出更划算。', @c_reading, '几乎全新', 55.00, 110.00, '["https://picsum.photos/seed/sy13a/400/400"]', NULL, '东区食堂门口', @u_s2, 1),
('【几乎全新】迪卡侬 泳镜 + 泳帽 套装', '暑假办游泳卡买的，去了三次就放假了。泳镜防雾层完好，泳帽没变形。', @c_swim, '几乎全新', 35.00, 89.00, '["https://picsum.photos/seed/sy14a/400/400"]', '迪卡侬', '体育馆西门', @u_s2, 1),
('【九成新】膳魔师保温杯 500ml 樱花粉', '用了一个学期，保温效果还是很好，早上倒的水中午还烫。杯身有一道浅浅的划痕。', @c_daily, '轻微使用痕迹', 85.00, 199.00, '["https://picsum.photos/seed/sy15a/400/400"]', '膳魔师', '3号宿舍楼下', @u_s2, 1);

-- ---------- 卖家 3：student03（12 件，毕业季杂项） ----------
INSERT INTO `product` (`title`, `description`, `category_id`, `condition_level`, `price`, `original_price`, `images`, `brand`, `trade_place`, `seller_id`, `status`) VALUES
('【九成新】华硕 TUF 电竞显示器 27 寸 165Hz', '打游戏用的，无坏点无亮点，支架可升降旋转。因为毕业要寄回家太占地方所以出。', @c_av, '轻微使用痕迹', 780.00, 1599.00, '["https://picsum.photos/seed/sb01a/400/400","https://picsum.photos/seed/sb01b/400/400"]', '华硕', '3号宿舍楼下', @u_s3, 1),
('【几乎全新】罗技 K380 蓝牙键盘 可连三台设备', '图书馆写论文用的，很安静不吵人。键帽无油光，电池还有电，送两节新电池。', @c_smart, '几乎全新', 130.00, 249.00, '["https://picsum.photos/seed/sb02a/400/400"]', '罗技', '图书馆一楼大厅', @u_s3, 2),
('【九成新】iPhone 12 128G 白色 电池健康 89%', '自用机，一直带壳带膜，屏幕无划痕，后盖有一点点细纹。无维修无进水，可当面验机。', @c_phone, '轻微使用痕迹', 1980.00, 4599.00, '["https://picsum.photos/seed/sb03a/400/400","https://picsum.photos/seed/sb03b/400/400","https://picsum.photos/seed/sb03c/400/400"]', 'Apple', '南门快递站旁', @u_s3, 1),
('【九成新】华为 MatePad 11 平板 6+128G 带键盘', '上网课买的，配了原装磁吸键盘。屏幕贴膜无划痕，内存够用，看 PDF 很舒服。', @c_tablet, '轻微使用痕迹', 1150.00, 2299.00, '["https://picsum.photos/seed/sb04a/400/400","https://picsum.photos/seed/sb04b/400/400"]', '华为', '图书馆一楼大厅', @u_s3, 0),
('【九成新】小米移动电源 20000mAh 双向快充', '出门旅游带的，能给手机充四次左右。外壳有几道划痕，接口正常，电量显示准确。', @c_smart, '轻微使用痕迹', 65.00, 149.00, '["https://picsum.photos/seed/sb05a/400/400"]', '小米', '南门快递站旁', @u_s3, 1),
('【九成新】《数据结构与算法分析 C语言描述》', '专业课教材，只看过前四章，后面全新。书里没有笔记，适合自学。', @c_textbook, '轻微使用痕迹', 30.00, 59.00, '["https://picsum.photos/seed/sb06a/400/400"]', NULL, '图书馆一楼大厅', @u_s3, 1),
('【八成新】大行 D6 折叠自行车 16 寸 可上地铁', '通勤代步用的，折叠机构正常，刹车灵敏，链条上过油。车漆有几处磕碰痕迹，不影响骑。', @c_cycling, '明显使用痕迹', 620.00, 1499.00, '["https://picsum.photos/seed/sb07a/400/400","https://picsum.photos/seed/sb07b/400/400"]', '大行', '南门快递站旁', @u_s3, 1),
('【几乎全新】露营折叠椅 两把 承重 150kg', '买了四把，只用过两次，另外两把已经出了。收纳袋齐全，铝合金支架无变形。', @c_camping, '几乎全新', 90.00, 199.00, '["https://picsum.photos/seed/sb08a/400/400"]', NULL, '体育馆西门', @u_s3, 1),
('【九成新】小米加湿器 4L 上加水 静音款', '冬天宿舍开暖气太干买的，晚上开着几乎听不到声音。水箱无水垢，滤芯刚换过。', @c_smallapp, '轻微使用痕迹', 75.00, 169.00, '["https://picsum.photos/seed/sb09a/400/400"]', '小米', '3号宿舍楼下', @u_s3, 2),
('【几乎全新】美的空气炸锅 4.5L 触控款', '买来想做减脂餐，用过五次左右。内胆不粘涂层完好，无划痕，说明书还在。', @c_kitchen, '几乎全新', 160.00, 349.00, '["https://picsum.photos/seed/sb10a/400/400","https://picsum.photos/seed/sb10b/400/400"]', '美的', '东区食堂门口', @u_s3, 1),
('【全新未拆】沙宣洗发水 750ml 修护型', '618 囤的，买多了用不完。未开封，瓶身无磕碰，有效期还有两年多。', @c_haircare, '全新', 55.00, 129.00, '["https://picsum.photos/seed/sb11a/400/400"]', '沙宣', '3号宿舍楼下', @u_s3, 3),
('【全新】故宫文创 手账本礼盒 内含胶带贴纸', '朋友送的，我不用手账所以转出。礼盒塑封完好，里面本子、和纸胶带、贴纸全套都在。', @c_creative, '全新', 88.00, 168.00, '["https://picsum.photos/seed/sb12a/400/400","https://picsum.photos/seed/sb12b/400/400"]', '故宫文创', '东区食堂门口', @u_s3, 1);

-- ---------- 买家 1：buyer01（8 件，自己也卖点闲置） ----------
INSERT INTO `product` (`title`, `description`, `category_id`, `condition_level`, `price`, `original_price`, `images`, `brand`, `trade_place`, `seller_id`, `status`) VALUES
('【九成新】《深入理解计算机系统》CSAPP 第 3 版', '考研 408 用的，厚得能砸核桃。只看了前五章，后面全新，无笔记。', @c_textbook, '轻微使用痕迹', 75.00, 139.00, '["https://picsum.photos/seed/b101a/400/400"]', NULL, '图书馆一楼大厅', @u_b1, 1),
('【九成新】阿米洛 87 键机械键盘 静电容轴', '手感真的绝，就是太重了带不出去。键帽无油光，全键无冲突，送一根编织线。', @c_smart, '轻微使用痕迹', 420.00, 899.00, '["https://picsum.photos/seed/b102a/400/400","https://picsum.photos/seed/b102b/400/400"]', '阿米洛', '3号宿舍楼下', @u_b1, 1),
('【几乎全新】华为 FreeBuds Pro 2 无线耳机', '买手机时一起买的，用了几次觉得入耳式不舒服。降噪和音质都很好，充电盒无划痕。', @c_headphone, '几乎全新', 480.00, 1299.00, '["https://picsum.photos/seed/b103a/400/400","https://picsum.photos/seed/b103b/400/400"]', '华为', '图书馆一楼大厅', @u_b1, 1),
('【九成新】优衣库 牛仔外套 男 M 码 深蓝', '经典款，穿过一季，没有破损和明显褪色。洗过三四次，尺码正常。', @c_menswear, '轻微使用痕迹', 100.00, 299.00, '["https://picsum.photos/seed/b104a/400/400"]', '优衣库', '东区食堂门口', @u_b1, 1),
('【七成新】折叠书桌 60cm 床上可用', '宿舍床上用的，桌面有几道划痕，桌腿折叠正常。太重不支持快递，只能自提。', @c_misc, '明显使用痕迹', 80.00, 199.00, '["https://picsum.photos/seed/b105a/400/400"]', NULL, '3号宿舍楼下', @u_b1, 4),
('【全新】蓝月亮洗衣液 3kg 补充装 未开封', '开学囤的，发现宿舍楼下就有卖的。塑封完整，无破损无渗漏。', @c_clean, '全新', 30.00, 59.00, '["https://picsum.photos/seed/b106a/400/400"]', '蓝月亮', '3号宿舍楼下', @u_b1, 0),
('【九成新】斯伯丁 篮球 7 号 室内外通用', '班级比赛用的，打了七八次，纹路还很清晰，气密性正常。送一个打气筒。', @c_ball, '轻微使用痕迹', 85.00, 189.00, '["https://picsum.photos/seed/b107a/400/400"]', '斯伯丁', '体育馆西门', @u_b1, 1),
('【几乎全新】吉他拨片 + 变调夹 套装', '买吉他时配的，我不弹了所以一起出。拨片有厚中薄各几个，变调夹弹簧正常。', @c_guitar, '几乎全新', 25.00, 60.00, '["https://picsum.photos/seed/b108a/400/400"]', NULL, '体育馆西门', @u_b1, 1);

-- ---------- 买家 2：buyer02（6 件） ----------
INSERT INTO `product` (`title`, `description`, `category_id`, `condition_level`, `price`, `original_price`, `images`, `brand`, `trade_place`, `seller_id`, `status`) VALUES
('【九成新】松下电吹风 EH-NE20 恒温护发', '用了半年，风力够大不烫头皮。线材无破损，滤网清过，功能全部正常。', @c_personal, '轻微使用痕迹', 95.00, 229.00, '["https://picsum.photos/seed/b201a/400/400"]', '松下', '3号宿舍楼下', @u_b2, 2),
('【几乎全新】《人类简史》中信出版社 精装', '看了三分之一，书很新，无笔记无折角。内容很有意思，推荐。', @c_reading, '几乎全新', 28.00, 68.00, '["https://picsum.photos/seed/b202a/400/400"]', NULL, '东区食堂门口', @u_b2, 1),
('【几乎全新】华为手环 8 标准版 黑色', '买了想监测睡眠，戴了两周就不戴了。表带无磨损，充电器包装都在。', @c_smart, '几乎全新', 120.00, 249.00, '["https://picsum.photos/seed/b203a/400/400"]', '华为', '体育馆西门', @u_b2, 0),
('【全新】化妆刷 12 支套装 带收纳包', '买重复了，一整套没拆封。刷毛很软不掉毛，适合新手入门。', @c_beautytool, '全新', 40.00, 99.00, '["https://picsum.photos/seed/b204a/400/400"]', NULL, '3号宿舍楼下', @u_b2, 1),
('【几乎全新】桌面收纳盒 亚克力 三层抽屉', '整理宿舍桌面买的，结果柜子放不下。无划痕无裂纹，抽屉滑动顺畅。', @c_storage, '几乎全新', 25.00, 69.00, '["https://picsum.photos/seed/b205a/400/400"]', NULL, '3号宿舍楼下', @u_b2, 2),
('【几乎全新】全棉四件套 1.5m 床 浅灰格子', '买错尺寸了，宿舍床是 0.9m 的用不了。拆都没拆过，吊牌还在。', @c_home, '几乎全新', 95.00, 259.00, '["https://picsum.photos/seed/b206a/400/400","https://picsum.photos/seed/b206b/400/400"]', NULL, '3号宿舍楼下', @u_b2, 1);


-- =============================================================================
--  第 5 步：让发布时间和浏览量看起来自然
-- =============================================================================
--  用确定性公式（不用 RAND()），这样每次重跑结果都一样，便于排查问题。
--  create_time：分布在最近 60 天内，不同商品错开
--  view_count ：10 ~ 499 之间，热门商品（id 小的）浏览量偏高
-- =============================================================================
UPDATE `product`
SET `create_time` = DATE_SUB(NOW(), INTERVAL MOD(`id` * 13, 60) DAY) - INTERVAL MOD(`id` * 7, 24) HOUR,
    `view_count`  = 10 + MOD(`id` * 37, 430) + MOD(`id` * 11, 60);


-- =============================================================================
--  第 6 步：收藏数据
-- =============================================================================
--  规则：谁都可以收藏别人的商品，但【不能收藏自己的】—— 这也是业务规则
--        （BR-F-1：不能收藏自己发布的商品）。这里用 seller_id <> 自己 过滤掉。
--
--  用 INSERT IGNORE：撞上 uk_user_product 唯一键就跳过，所以可重复执行。
-- =============================================================================
INSERT IGNORE INTO `favorite` (`user_id`, `product_id`, `create_time`)
SELECT @u_b1, p.`id`, DATE_SUB(NOW(), INTERVAL MOD(p.`id` * 3, 25) DAY)
FROM `product` p
WHERE p.`status` IN (1, 2) AND p.`seller_id` <> @u_b1
ORDER BY MOD(p.`id` * 17, 101) DESC
LIMIT 18;

INSERT IGNORE INTO `favorite` (`user_id`, `product_id`, `create_time`)
SELECT @u_b2, p.`id`, DATE_SUB(NOW(), INTERVAL MOD(p.`id` * 5, 25) DAY)
FROM `product` p
WHERE p.`status` IN (1, 2) AND p.`seller_id` <> @u_b2
ORDER BY MOD(p.`id` * 23, 101) DESC
LIMIT 14;

INSERT IGNORE INTO `favorite` (`user_id`, `product_id`, `create_time`)
SELECT @u_s2, p.`id`, DATE_SUB(NOW(), INTERVAL MOD(p.`id` * 7, 20) DAY)
FROM `product` p
WHERE p.`status` IN (1, 2) AND p.`seller_id` <> @u_s2
ORDER BY MOD(p.`id` * 29, 101) DESC
LIMIT 9;

INSERT IGNORE INTO `favorite` (`user_id`, `product_id`, `create_time`)
SELECT @u_s3, p.`id`, DATE_SUB(NOW(), INTERVAL MOD(p.`id` * 11, 20) DAY)
FROM `product` p
WHERE p.`status` IN (1, 2) AND p.`seller_id` <> @u_s3
ORDER BY MOD(p.`id` * 31, 101) DESC
LIMIT 6;


-- =============================================================================
--  第 7 步：同步 favorite_count（冗余字段）
-- =============================================================================
--  ⚠️ 必须做这一步！
--     favorite_count 是冗余字段，如果它和 favorite 表的实际行数对不上，
--     后面"取消收藏 -1"就会出现负数或不归零，调试时非常困惑。
--     这条 UPDATE 保证两者永远一致。
-- =============================================================================
UPDATE `product` p
SET p.`favorite_count` = (SELECT COUNT(*) FROM `favorite` f WHERE f.`product_id` = p.`id`);


-- =============================================================================
--  第 8 步：行为数据
-- =============================================================================
--  user_behavior 里【不能有唯一键】（同一人可浏览同一商品多次），
--  所以没法用 INSERT IGNORE 做幂等，这里直接清空重插。
-- =============================================================================
DELETE FROM `user_behavior`;

-- 8.1 浏览记录（type=1）：每个用户浏览一批在售/已售出的商品
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `behavior_time`)
SELECT u.`id`, p.`id`, 1, DATE_SUB(NOW(), INTERVAL MOD(p.`id` + u.`id` * 3, 40) DAY)
FROM `user` u
CROSS JOIN `product` p
WHERE u.`role` = 'user'
  AND p.`status` IN (1, 2)
  AND MOD(p.`id` * u.`id`, 5) = 0;

-- 8.2 收藏记录（type=2）：和 favorite 表一一对应（时间也一致）
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `behavior_time`)
SELECT f.`user_id`, f.`product_id`, 2, f.`create_time`
FROM `favorite` f;

-- 8.3 咨询记录（type=3）：点过"获取联系方式"的行为
--     这是本平台最核心的转化指标，卖家在"我的发布"里看到的 contactCount 就是它
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `behavior_time`)
SELECT f.`user_id`, f.`product_id`, 3, DATE_ADD(f.`create_time`, INTERVAL 2 HOUR)
FROM `favorite` f
WHERE f.`user_id` IN (@u_b1, @u_b2)
  AND MOD(f.`id`, 2) = 1;

-- 8.4 取消收藏记录（type=4）：买过的商品被卖家下架了，于是取消收藏
INSERT INTO `user_behavior` (`user_id`, `product_id`, `behavior_type`, `behavior_time`)
SELECT @u_b2, p.`id`, 4, DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM `product` p
WHERE p.`status` = 3
LIMIT 3;


-- =============================================================================
--  第 9 步：自检
-- =============================================================================
SELECT '用户数'      AS `项目`, COUNT(*) AS `数量` FROM `user`
UNION ALL SELECT '分类数',      COUNT(*) FROM `category`
UNION ALL SELECT '商品数',      COUNT(*) FROM `product`
UNION ALL SELECT '收藏数',      COUNT(*) FROM `favorite`
UNION ALL SELECT '行为数',      COUNT(*) FROM `user_behavior`;

SELECT `status` AS `状态`, COUNT(*) AS `数量`
FROM `product` GROUP BY `status` ORDER BY `status`;

SELECT `behavior_type` AS `行为类型`, COUNT(*) AS `数量`
FROM `user_behavior` GROUP BY `behavior_type` ORDER BY `behavior_type`;

-- 一致性检查：下面这条应该返回 0 行（表示 favorite_count 和 favorite 表完全一致）
SELECT p.`id`, p.`favorite_count`, COUNT(f.`id`) AS `real_count`
FROM `product` p
LEFT JOIN `favorite` f ON f.`product_id` = p.`id`
GROUP BY p.`id`, p.`favorite_count`
HAVING p.`favorite_count` <> COUNT(f.`id`);
