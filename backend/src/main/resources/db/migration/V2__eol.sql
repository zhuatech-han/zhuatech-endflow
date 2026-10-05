-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE eol_case (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL,
 code varchar(60) NOT NULL UNIQUE, title varchar(160) NOT NULL, category varchar(60) NOT NULL,
 manufacturer varchar(160) NOT NULL, part_number varchar(160) NOT NULL, source_ref varchar(300) NOT NULL,
 department_id bigint NOT NULL, author_id bigint NOT NULL, buyer_id bigint NOT NULL, reviewer_id bigint NOT NULL,
 notice_date date NOT NULL, last_buy_date date NOT NULL, last_ship_date date NOT NULL,
 decision varchar(30) NOT NULL, alternative_part varchar(160) NOT NULL, validation_ref varchar(300) NOT NULL,
 rationale varchar(2000) NOT NULL, pack_size int NOT NULL, minimum_order int NOT NULL, decision_qty int NOT NULL,
 unit_price decimal(18,4) NOT NULL, budget decimal(18,2) NOT NULL, status varchar(30) NOT NULL,
 submitted boolean NOT NULL, order_ref varchar(160) NOT NULL, implementation_ref varchar(300) NOT NULL,
 order_date date, promised_date date, created_at timestamp(6) NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(author_id) REFERENCES account(id),
 FOREIGN KEY(buyer_id) REFERENCES account(id), FOREIGN KEY(reviewer_id) REFERENCES account(id)
);
CREATE INDEX ix_eol_scope ON eol_case(department_id,status);
CREATE TABLE demand_line (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, product_code varchar(100) NOT NULL,
 description varchar(300) NOT NULL, evidence varchar(2000) NOT NULL,
 monthly_demand int NOT NULL, months int NOT NULL, service_reserve int NOT NULL,
 allocated_stock int NOT NULL, confirmed_inbound int NOT NULL,
 FOREIGN KEY(case_id) REFERENCES eol_case(id), UNIQUE(case_id,product_code)
);
CREATE TABLE receipt (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, actor_id bigint NOT NULL, reversed_by bigint,
 quantity int NOT NULL, reference varchar(160) NOT NULL, evidence varchar(2000) NOT NULL,
 reversal_note varchar(2000) NOT NULL, received_date date NOT NULL, created_at timestamp(6) NOT NULL,
 reversed_at timestamp(6), FOREIGN KEY(case_id) REFERENCES eol_case(id), FOREIGN KEY(actor_id) REFERENCES account(id),
 FOREIGN KEY(reversed_by) REFERENCES account(id), UNIQUE(case_id,reference)
);
CREATE TABLE case_event (
 id bigint AUTO_INCREMENT PRIMARY KEY, case_id bigint NOT NULL, actor_id bigint NOT NULL,
 action varchar(60) NOT NULL, before_status varchar(30) NOT NULL, after_status varchar(30) NOT NULL,
 evidence varchar(2000) NOT NULL, created_at timestamp(6) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES eol_case(id), FOREIGN KEY(actor_id) REFERENCES account(id)
);
CREATE INDEX ix_case_event ON case_event(case_id,id);
CREATE TABLE command_record (
 id bigint AUTO_INCREMENT PRIMARY KEY, request_key varchar(36) NOT NULL UNIQUE,
 fingerprint varchar(64) NOT NULL, result_id bigint NOT NULL
);
