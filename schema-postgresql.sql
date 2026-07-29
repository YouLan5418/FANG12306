-- ============================================================================
-- 12306 重构版 PostgreSQL 建表脚本
-- 含：业务分区表 + 基础数据表 + pgvector 知识库表
-- 设计：HASH 分区替代 ShardingSphere 分片；主键含分区键；雪花 ID（应用层生成）
-- ============================================================================

-- pgvector 扩展（知识库向量检索）
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================================
-- 一、用户域（HASH 分区，对应原 ShardingSphere 分片）
-- ============================================================================

-- t_user：用户主表，按 username HASH 分区（原分片键 username）
CREATE TABLE t_user (
    id            BIGINT      NOT NULL,
    username      VARCHAR(256) NOT NULL,
    password      VARCHAR(512),
    real_name     VARCHAR(256),
    region        VARCHAR(64)  DEFAULT '0',
    id_type       SMALLINT,
    id_card       VARCHAR(256),          -- AES 加密密文（应用层 TypeHandler）
    phone         VARCHAR(128),          -- AES 加密密文
    telephone     VARCHAR(128),
    mail          VARCHAR(256),          -- AES 加密密文
    user_type     SMALLINT,
    verify_status SMALLINT,
    post_code     VARCHAR(64),
    address       VARCHAR(1024),         -- AES 加密密文
    deletion_time BIGINT      DEFAULT 0, -- 注销时间戳，配合 username 唯一实现注销后重用
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag      SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, username)
) PARTITION BY HASH (username);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_user_p%s PARTITION OF t_user FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE UNIQUE INDEX uk_user_username ON t_user (username, deletion_time);

-- t_user_phone：手机号反查 username，按 phone HASH 分区
CREATE TABLE t_user_phone (
    id            BIGINT      NOT NULL,
    username      VARCHAR(256),
    phone         VARCHAR(128) NOT NULL,
    deletion_time BIGINT      DEFAULT 0,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag      SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, phone)
) PARTITION BY HASH (phone);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_user_phone_p%s PARTITION OF t_user_phone FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE UNIQUE INDEX uk_user_phone ON t_user_phone (phone, deletion_time);

-- t_user_mail：邮箱反查 username，按 mail HASH 分区
CREATE TABLE t_user_mail (
    id            BIGINT      NOT NULL,
    username      VARCHAR(256),
    mail          VARCHAR(256) NOT NULL,
    deletion_time BIGINT      DEFAULT 0,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag      SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, mail)
) PARTITION BY HASH (mail);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_user_mail_p%s PARTITION OF t_user_mail FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE UNIQUE INDEX uk_user_mail ON t_user_mail (mail, deletion_time);

-- t_passenger：乘车人，按 username HASH 分区（与用户同维度）
CREATE TABLE t_passenger (
    id            BIGINT      NOT NULL,
    username      VARCHAR(256) NOT NULL,
    real_name     VARCHAR(128),
    id_type       SMALLINT,
    id_card       VARCHAR(256),          -- AES 加密密文
    discount_type SMALLINT,
    phone         VARCHAR(128),          -- AES 加密密文
    create_date   TIMESTAMP,
    verify_status SMALLINT,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag      SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, username)
) PARTITION BY HASH (username);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_passenger_p%s PARTITION OF t_passenger FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_passenger_username ON t_passenger (username);
CREATE INDEX idx_passenger_id_card ON t_passenger (id_card);

-- ============================================================================
-- 二、订单/支付域（HASH 分区，订单按 order_sn）
-- ============================================================================

-- t_order：订单主表，按 order_sn HASH 分区（热点路径：创建/支付/关单/退款均按 order_sn）
--   status：0待支付 10已支付 11部分退款 12全部退款 20已完成 30已取消
CREATE TABLE t_order (
    id             BIGINT      NOT NULL,
    order_sn       VARCHAR(64) NOT NULL,
    user_id        BIGINT,
    username       VARCHAR(256),
    train_id       BIGINT,
    train_number   VARCHAR(256),
    riding_date    DATE,
    departure      VARCHAR(64),
    arrival        VARCHAR(64),
    departure_time TIMESTAMP,
    arrival_time   TIMESTAMP,
    source         SMALLINT,
    status         SMALLINT,
    order_time     TIMESTAMP,
    pay_type       SMALLINT,
    pay_time       TIMESTAMP,
    create_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, order_sn)
) PARTITION BY HASH (order_sn);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_order_p%s PARTITION OF t_order FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_order_user_id ON t_order (user_id);
CREATE INDEX idx_order_status ON t_order (status);

-- t_order_item：订单明细，按 order_sn HASH 分区（与订单同分区）
--   status：0待支付 10已支付 20已进站 30已取消 40已退票 50已改签
CREATE TABLE t_order_item (
    id              BIGINT      NOT NULL,
    order_sn        VARCHAR(64) NOT NULL,
    user_id         BIGINT,
    username        VARCHAR(256),
    train_id        BIGINT,
    carriage_number VARCHAR(64),
    seat_type       SMALLINT,
    seat_number     VARCHAR(64),
    real_name       VARCHAR(256),
    id_type         SMALLINT,
    id_card         VARCHAR(256),        -- AES 加密密文
    ticket_type     SMALLINT,
    phone           VARCHAR(128),        -- AES 加密密文
    status          SMALLINT,
    amount          INTEGER,
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag        SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, order_sn)
) PARTITION BY HASH (order_sn);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_order_item_p%s PARTITION OF t_order_item FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_order_item_user_id ON t_order_item (user_id);

-- t_order_item_passenger：订单乘车人关系，按 order_sn HASH 分区（简化，原按 id_card）
CREATE TABLE t_order_item_passenger (
    id          BIGINT      NOT NULL,
    order_sn    VARCHAR(64) NOT NULL,
    id_type     SMALLINT,
    id_card     VARCHAR(256),            -- AES 加密密文
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, order_sn)
) PARTITION BY HASH (order_sn);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_order_item_passenger_p%s PARTITION OF t_order_item_passenger FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;

-- t_pay：支付单，按 order_sn HASH 分区
CREATE TABLE t_pay (
    id               BIGINT      NOT NULL,
    pay_sn           VARCHAR(64),
    order_sn         VARCHAR(64) NOT NULL,
    out_order_sn     VARCHAR(64),
    channel          VARCHAR(64),
    trade_type       VARCHAR(64),
    subject          VARCHAR(512),
    order_request_id VARCHAR(64),
    total_amount     INTEGER,
    trade_no         VARCHAR(256),
    gmt_payment      TIMESTAMP,
    pay_amount       INTEGER,
    status           VARCHAR(32),        -- TRADE_SUCCESS / TRADE_CLOSED 等
    create_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag         SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, order_sn)
) PARTITION BY HASH (order_sn);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_pay_p%s PARTITION OF t_pay FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_pay_pay_sn ON t_pay (pay_sn);

-- t_refund：退款记录，按 order_sn HASH 分区（原单表，新项目分区）
CREATE TABLE t_refund (
    id             BIGINT      NOT NULL,
    pay_sn         VARCHAR(64),
    order_sn       VARCHAR(64) NOT NULL,
    trade_no       VARCHAR(64),
    amount         INTEGER,
    user_id        BIGINT,
    username       VARCHAR(256),
    train_id       BIGINT,
    train_number   VARCHAR(256),
    riding_date    DATE,
    departure      VARCHAR(64),
    arrival        VARCHAR(64),
    departure_time TIMESTAMP,
    arrival_time   TIMESTAMP,
    seat_type      SMALLINT,
    id_type        SMALLINT,
    id_card        VARCHAR(256),         -- AES 加密密文
    real_name      VARCHAR(256),
    status         SMALLINT,
    refund_time    TIMESTAMP,
    create_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, order_sn)
) PARTITION BY HASH (order_sn);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_refund_p%s PARTITION OF t_refund FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_refund_user_id ON t_refund (user_id);

-- ============================================================================
-- 三、票务域（t_ticket 按 username，t_seat 按 train_id）
-- ============================================================================

-- t_ticket：车票，按 username HASH 分区（用户查自己的票）
CREATE TABLE t_ticket (
    id              BIGINT      NOT NULL,
    username        VARCHAR(256) NOT NULL,
    train_id        BIGINT,
    carriage_number VARCHAR(64),
    seat_number     VARCHAR(64),
    passenger_id    BIGINT,
    ticket_status   SMALLINT,            -- 0未支付 1已支付
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag        SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, username)
) PARTITION BY HASH (username);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_ticket_p%s PARTITION OF t_ticket FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_ticket_train_id ON t_ticket (train_id);

-- t_seat：座位，按 train_id HASH 分区（按车次查询座位）
--   seat_status：0可用 1锁定 2已售
CREATE TABLE t_seat (
    id              BIGINT      NOT NULL,
    train_id        BIGINT      NOT NULL,
    carriage_number VARCHAR(64),
    seat_number     VARCHAR(64),
    seat_type       SMALLINT,
    start_station   VARCHAR(256),
    end_station     VARCHAR(256),
    price           INTEGER,
    seat_status     SMALLINT,
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    del_flag        SMALLINT    DEFAULT 0,
    PRIMARY KEY (id, train_id)
) PARTITION BY HASH (train_id);
DO $$ DECLARE i INT; BEGIN
  FOR i IN 0..15 LOOP
    EXECUTE format('CREATE TABLE IF NOT EXISTS t_seat_p%s PARTITION OF t_seat FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
  END LOOP;
END $$;
CREATE INDEX idx_seat_loc ON t_seat (train_id, carriage_number, seat_number, seat_type, start_station, end_station);

-- ============================================================================
-- 四、基础数据表（单表，静态数据，不分片）
-- ============================================================================

CREATE TABLE t_train (
    id             BIGSERIAL PRIMARY KEY,
    train_number   VARCHAR(256),
    train_type     SMALLINT,             -- 0高铁 1动车 2普通车
    train_tag      VARCHAR(32),
    train_brand    VARCHAR(32),
    start_station  VARCHAR(256),
    end_station    VARCHAR(256),
    start_region   VARCHAR(256),
    end_region     VARCHAR(256),
    sale_time      TIMESTAMP,
    sale_status    SMALLINT,
    departure_time TIMESTAMP,
    arrival_time   TIMESTAMP,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT  DEFAULT 0
);
CREATE INDEX idx_train_number ON t_train (train_number);

CREATE TABLE t_station (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64),
    name        VARCHAR(256),
    spell       VARCHAR(64),
    region      VARCHAR(64),
    region_name VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);
CREATE INDEX idx_station_name ON t_station (name);
CREATE INDEX idx_station_spell ON t_station (spell);

CREATE TABLE t_carriage (
    id              BIGSERIAL PRIMARY KEY,
    train_id        BIGINT,
    carriage_number VARCHAR(64),
    carriage_type   SMALLINT,
    seat_count      SMALLINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag        SMALLINT  DEFAULT 0
);
CREATE INDEX idx_carriage_train_id ON t_carriage (train_id);

CREATE TABLE t_train_station (
    id             BIGSERIAL PRIMARY KEY,
    train_id       BIGINT,
    station_id     BIGINT,
    sequence       VARCHAR(32),
    departure      VARCHAR(64),
    arrival        VARCHAR(64),
    start_region   VARCHAR(256),
    end_region     VARCHAR(256),
    arrival_time   TIMESTAMP,
    departure_time TIMESTAMP,
    stopover_time  SMALLINT,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT  DEFAULT 0
);
CREATE INDEX idx_ts_train_id ON t_train_station (train_id);

CREATE TABLE t_train_station_relation (
    id             BIGSERIAL PRIMARY KEY,
    train_id       BIGINT,
    departure      VARCHAR(64),
    arrival        VARCHAR(64),
    start_region   VARCHAR(256),
    end_region     VARCHAR(256),
    departure_flag SMALLINT,
    arrival_flag   SMALLINT,
    departure_time TIMESTAMP,
    arrival_time   TIMESTAMP,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT  DEFAULT 0
);
CREATE INDEX idx_tsr_train_id ON t_train_station_relation (train_id);
CREATE INDEX idx_tsr_route ON t_train_station_relation (departure, arrival);

CREATE TABLE t_train_station_price (
    id          BIGSERIAL PRIMARY KEY,
    train_id    BIGINT,
    departure   VARCHAR(64),
    arrival     VARCHAR(64),
    seat_type   SMALLINT,
    price       INTEGER,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);
CREATE INDEX idx_tsp_route ON t_train_station_price (train_id, departure, arrival);

CREATE TABLE t_region (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(64),
    full_name    VARCHAR(64),
    code         VARCHAR(64),
    initial      VARCHAR(2),
    spell        VARCHAR(64),
    popular_flag SMALLINT,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag     SMALLINT  DEFAULT 0
);

-- ============================================================================
-- 五、用户辅助表（单表，注销流程辅助）
-- ============================================================================

CREATE TABLE t_user_deletion (
    id          BIGSERIAL PRIMARY KEY,
    id_type     SMALLINT,
    id_card     VARCHAR(256),            -- AES 加密密文
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);

CREATE TABLE t_user_reuse (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);
CREATE INDEX idx_reuse_username ON t_user_reuse (username);

-- ============================================================================
-- 六、智能客服 / 知识库（pgvector + 后台管理）
-- ============================================================================

-- kb_category：知识库分类（支持后台自添加）
CREATE TABLE kb_category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);
-- 起步三类种子数据
INSERT INTO kb_category (name, description) VALUES
    ('退改规则', '退票手续费、改签规则、退票时限'),
    ('购票流程', '注册登录、购票选座、支付流程'),
    ('证件要求', '乘车人证件类型、实名认证、儿童票规定');

-- kb_document：知识库文档
CREATE TABLE kb_document (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES kb_category(id),
    file_name   VARCHAR(256) NOT NULL,
    file_path   VARCHAR(512),
    file_type   VARCHAR(32),            -- markdown/word/pdf
    parser_type VARCHAR(32),            -- MARKDOWN/TIKA/OCR
    chunk_mode  VARCHAR(32),            -- structure_aware/fixed_size
    status      SMALLINT DEFAULT 0,     -- 0待处理 1处理中 2完成 3失败
    chunk_count INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag    SMALLINT  DEFAULT 0
);
CREATE INDEX idx_doc_category ON kb_document (category_id);

-- knowledge_chunk：向量切片（pgvector）
--   embedding 维度按所选 Embedding 模型调整（bge-m3 为 1024）
CREATE TABLE knowledge_chunk (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES kb_category(id),
    document_id BIGINT NOT NULL REFERENCES kb_document(id),
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1024),
    metadata    JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_kc_category ON knowledge_chunk (category_id);
CREATE INDEX idx_kc_document ON knowledge_chunk (document_id);
-- HNSW 向量索引（余弦相似度）
CREATE INDEX idx_kc_embedding ON knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- ingestion_task：入库任务（简化版，跟踪上传->解析->切片->向量化流水线）
CREATE TABLE ingestion_task (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT NOT NULL,
    status           SMALLINT DEFAULT 0, -- 0待处理 1解析 2切片 3向量化 4完成 5失败
    total_chunks     INTEGER DEFAULT 0,
    processed_chunks INTEGER DEFAULT 0,
    error_msg        TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_task_status ON ingestion_task (status);

-- chat_session / chat_message：对话历史持久化（MVP 用 Redis 缓存，此表用于后台查看/审计）
CREATE TABLE chat_session (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL UNIQUE,
    username    VARCHAR(256),
    title       VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL,
    role        VARCHAR(16) NOT NULL,    -- user/assistant/tool
    content     TEXT,
    tool_name   VARCHAR(64),
    tool_args   JSONB,
    tool_result TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_chat_msg_session ON chat_message (session_id, create_time);