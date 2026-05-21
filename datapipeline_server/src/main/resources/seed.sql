-- ============================================================================
-- Birla Bank Ltd — Identity Hub seed data
-- ============================================================================
-- One organisation, five source-system schemas, 33 unique humans across 87
-- rows. Correlation rate: ~79% (26 of 33 people appear in 2+ systems).
--
-- Schemas (deliberately different column conventions to mirror heterogeneous
-- real-world source systems):
--   org_db.organisation     — tenant org master
--   cbs_db.employee_master  — Core Banking System (verbose snake_case, _ts/_no suffixes)
--   ad_db.ad_users          — Active Directory (LDAP attribute names: samAccountName, etc.)
--   crm_db.contacts         — Customer Relationship Mgmt (SaaS conventions: uuid, tax_id)
--   trade_db.traders        — Trading platform (terse finance shorthand: trdr_nm, eml)
--   los_db.loan_officer     — Loan Origination System (banking longhand: permanent_account_no)
--
-- Planted scenarios for the correlation pipeline:
--   • 12 people in 3+ systems → exercises the union-find clustering on email/PAN/name+DOB
--   • 7 singletons → must NOT be merged into anyone
--   • 1 cross-correlation: Rohan Desai's CBS row and Neha Khanna's CRM row share
--     phone (9821554433) + DOB (1989-10-20). They live in different clusters
--     so the pipeline emits a POSSIBLE_DUPLICATE edge.
--   • Name variants across systems (Rajesh Kumar → Rajesh K. → R Kumar) test
--     that the resolver doesn't rely on name equality.
--
-- Compatibility: H2 (PostgreSQL mode preferred). For SQLite, drop the
-- CREATE SCHEMA statements and use prefixed table names (e.g. cbs_employee_master).
--
-- Spring Boot wiring (application.yml):
--   spring:
--     datasource:
--       url: jdbc:h2:mem:identityhub;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
--       driver-class-name: org.h2.Driver
--     sql:
--       init:
--         mode: always
--         data-locations: classpath:./<DirectoryStructure>/seed.sql
-- ============================================================================

-- ─── 0. Schema setup ──────────────────────────────────────────────────────
DROP SCHEMA IF EXISTS org_db CASCADE;
DROP SCHEMA IF EXISTS cbs_db CASCADE;
DROP SCHEMA IF EXISTS ad_db CASCADE;
DROP SCHEMA IF EXISTS crm_db CASCADE;
DROP SCHEMA IF EXISTS trade_db CASCADE;
DROP SCHEMA IF EXISTS los_db CASCADE;
CREATE SCHEMA org_db;
CREATE SCHEMA cbs_db;
CREATE SCHEMA ad_db;
CREATE SCHEMA crm_db;
CREATE SCHEMA trade_db;
CREATE SCHEMA los_db;

-- ─── 1. Organisation ───────────────────────────────────────────────────────
CREATE TABLE org_db.organisation (
    org_id         VARCHAR(20)  PRIMARY KEY,
    org_name       VARCHAR(100) NOT NULL,
    industry       VARCHAR(60),
    country_code   VARCHAR(5),
    registered_on  DATE,
    created_at     TIMESTAMP
);

INSERT INTO org_db.organisation (org_id, org_name, industry, country_code, registered_on, created_at) VALUES
    ('ORG-BIRLA-001', 'Birla Bank Ltd', 'Banking & Financial Services', 'IN', '2010-04-15', '2010-04-15 09:00:00');

-- ─── 2. CBS — Core Banking System (verbose snake_case, Hungarian suffixes)
CREATE TABLE cbs_db.employee_master (
    emp_id            VARCHAR(20)  PRIMARY KEY,
    employee_name     VARCHAR(100),
    work_email_addr   VARCHAR(150),
    contact_mobile    VARCHAR(20),
    date_of_birth     DATE,
    pan_number        VARCHAR(10),
    branch_code       VARCHAR(10),
    designation       VARCHAR(80),
    joining_date      DATE,
    active_flag       CHAR(1) DEFAULT 'Y',
    last_modified_ts  TIMESTAMP
);

INSERT INTO cbs_db.employee_master (emp_id, employee_name, work_email_addr, contact_mobile, date_of_birth, pan_number, branch_code, designation, joining_date, active_flag, last_modified_ts) VALUES
    ('EMP-4013', 'Rajesh Kumar', 'rajesh.kumar@birlabank.in', '+91-9810000001', '1985-03-14', 'AAAPK1234A', 'DEL-001', 'AVP - Trading Operations', '2011-02-15', 'Y', '2026-05-01 08:15:00'),
    ('EMP-4026', 'Priya Sharma', 'priya.sharma@birlabank.in', '+91-9876543210', '1992-07-22', 'AAAPS5678B', 'MUM-002', 'Senior Risk Analyst', '2012-03-15', 'Y', '2026-05-02 09:18:00'),
    ('EMP-4039', 'Amit Patel', 'amit.patel@birlabank.in', '+91-9830007890', '1988-11-09', 'AAAPP9999C', 'AMD-008', 'Loan Officer', '2013-04-15', 'Y', '2026-05-03 10:21:00'),
    ('EMP-4052', 'Sneha Iyer', 'sneha.iyer@birlabank.in', '+91-9445001122', '1990-05-18', 'AAAPI4321D', 'CHN-004', 'Relationship Manager', '2014-05-15', 'Y', '2026-05-04 11:24:00'),
    ('EMP-4065', 'Vikram Singh', 'vikram.singh@birlabank.in', '+91-9818234567', '1982-09-30', 'AAAPS8765E', 'DEL-001', 'Senior Treasury Dealer', '2015-06-15', 'Y', '2026-05-05 12:27:00'),
    ('EMP-4078', 'Anjali Reddy', 'anjali.reddy@birlabank.in', '+91-9000112233', '1995-02-14', 'AAAPR1010F', 'HYD-005', 'Compliance Officer', '2016-07-15', 'Y', '2026-05-06 13:30:00'),
    ('EMP-4091', 'Karthik Menon', 'karthik.menon@birlabank.in', '+91-9447889900', '1987-08-05', 'AAAPM2020G', 'KCH-010', 'Equity Trader', '2017-08-15', 'Y', '2026-05-07 14:33:00'),
    ('EMP-4104', 'Deepika Joshi', 'deepika.joshi@birlabank.in', '+91-9920334455', '1993-12-25', 'AAAPJ3030H', 'MUM-002', 'Credit Analyst', '2018-09-15', 'Y', '2026-05-08 15:36:00'),
    ('EMP-4117', 'Arjun Nair', 'arjun.nair@birlabank.in', '+91-9886234567', '1986-06-12', 'AAAPN4040I', 'BLR-003', 'Treasury Dealer', '2019-01-15', 'Y', '2026-05-09 16:39:00'),
    ('EMP-4130', 'Meera Krishnan', 'meera.krishnan@birlabank.in', '+91-9740112233', '1991-04-08', 'AAAPK5050J', 'BLR-003', 'Relationship Manager', '2010-02-15', 'Y', '2026-05-10 17:42:00'),
    ('EMP-4156', 'Kavya Pillai', 'kavya.pillai@birlabank.in', '+91-9495667788', '1994-01-15', 'AAAPP7070L', 'KCH-010', 'Credit Analyst', '2012-04-15', 'Y', '2026-05-11 18:45:00'),
    ('EMP-4169', 'Suresh Banerjee', 'suresh.banerjee@birlabank.in', '+91-9836445566', '1983-07-03', 'AAAPB8080M', 'KOL-006', 'Senior Treasury Dealer', '2013-05-15', 'Y', '2026-05-12 19:48:00'),
    ('EMP-4182', 'Pooja Mehta', 'pooja.mehta@birlabank.in', '+91-9824778899', '1990-11-28', 'AAAPM9090N', 'AMD-008', 'Relationship Manager', '2014-06-15', 'Y', '2026-05-13 08:51:00'),
    ('EMP-4208', 'Divya Krishnamurthy', 'divya.k@birlabank.in', '+91-9844112233', '1996-09-09', 'AAAPK1313P', 'BLR-003', 'KYC Officer', '2016-08-15', 'Y', '2026-05-14 09:54:00'),
    ('EMP-4234', 'Nisha Agarwal', 'nisha.agarwal@birlabank.in', '+91-9818776655', '1992-06-25', 'AAAPA1515R', 'DEL-001', 'Loan Officer', '2018-01-15', 'Y', '2026-05-15 10:57:00'),
    ('EMP-4247', 'Shruti Kapoor', 'shruti.kapoor@birlabank.in', '+91-9810223344', '1990-07-14', 'AAAPK2121X', 'DEL-001', 'Branch Manager', '2019-02-15', 'Y', '2026-05-16 11:00:00'),
    ('EMP-4260', 'Varun Bhat', 'varun.bhat@birlabank.in', '+91-9886443322', '1986-11-22', 'AAAPB2222Y', 'BLR-003', 'AVP - Operations', '2010-03-15', 'Y', '2026-05-17 12:03:00'),
    ('EMP-4299', 'Chitra Iyer', 'chitra.iyer@birlabank.in', '+91-9444223311', '1991-03-25', 'AAAPI5555B', 'CHN-004', 'KYC Officer', '2013-06-15', 'Y', '2026-05-18 13:06:00'),
    ('EMP-4351', 'Riya Chowdhury', 'riya.c@birlabank.in', '+91-9831223344', '1993-05-07', 'AAAPC9999F', 'KOL-006', 'KYC Officer', '2017-01-15', 'Y', '2026-05-19 14:09:00'),
    ('EMP-4416', 'Ravi Subramanian', 'ravi.s@birlabank.in', '+91-9445998877', '1986-02-28', 'AAAPS1515L', 'CHN-004', 'AVP - Operations', '2012-06-15', 'Y', '2026-05-20 15:12:00');

-- ─── 3. AD — Active Directory / LDAP (LDAP attribute names)
CREATE TABLE ad_db.ad_users (
    sam_account_name      VARCHAR(50)  PRIMARY KEY,
    display_name          VARCHAR(120),
    user_principal_name   VARCHAR(150),
    telephone_number      VARCHAR(20),
    employee_id           VARCHAR(20),
    department            VARCHAR(60),
    title                 VARCHAR(80),
    office_location       VARCHAR(40),
    account_enabled       BOOLEAN DEFAULT TRUE,
    when_changed          TIMESTAMP
);

INSERT INTO ad_db.ad_users (sam_account_name, display_name, user_principal_name, telephone_number, employee_id, department, title, office_location, account_enabled, when_changed) VALUES
    ('rkumar', 'Rajesh Kumar', 'rajesh.kumar@birlabank.in', '+91-9810000001', 'EMP-4013', 'Trading', 'AVP - Trading Operations', 'Delhi-NCR', 'True', '2026-05-01 09:20:00'),
    ('psharma', 'Priya Sharma', 'priya.sharma@birlabank.in', '+91-9876543210', 'EMP-4026', 'Risk', 'Senior Risk Analyst', 'Mumbai', 'True', '2026-05-02 10:25:00'),
    ('apatel', 'Amit Patel', 'amit.patel@birlabank.in', '+91-9830007890', 'EMP-4039', 'Lending', 'Loan Officer', 'Ahmedabad', 'True', '2026-05-03 11:30:00'),
    ('siyer', 'Sneha B. Iyer', 'sneha.iyer@birlabank.in', '+91-9445001122', 'EMP-4052', 'Retail', 'Relationship Manager', 'Chennai', 'True', '2026-05-04 12:35:00'),
    ('vsingh', 'Vikram Singh', 'vikram.singh@birlabank.in', '+91-9818234567', 'EMP-4065', 'Treasury', 'Senior Treasury Dealer', 'Delhi-NCR', 'True', '2026-05-05 13:40:00'),
    ('areddy', 'Anjali Reddy', 'anjali.reddy@birlabank.in', '+91-9000112233', 'EMP-4078', 'Compliance', 'Compliance Officer', 'Hyderabad', 'True', '2026-05-06 14:45:00'),
    ('deepika.j', 'Deepika Joshi', 'deepika.joshi@birlabank.in', '+91-9920334455', 'EMP-4104', 'Credit', 'Credit Analyst', 'Mumbai', 'True', '2026-05-07 15:50:00'),
    ('mkrishnan', 'Meera Krishnan', 'meera.krishnan@birlabank.in', '+91-9740112233', 'EMP-4130', 'Retail', 'Relationship Manager', 'Bengaluru', 'True', '2026-05-08 16:55:00'),
    ('rdesai', 'Rohan Desai', 'rohan.desai@birlabank.in', '+91-9821554433', NULL, 'Lending', 'Staff', 'Mumbai', 'True', '2026-05-09 17:00:00'),
    ('sbanerjee', 'Suresh Kumar Banerjee', 'suresh.banerjee@birlabank.in', '+91-9836445566', 'EMP-4169', 'Treasury', 'Senior Treasury Dealer', 'Kolkata', 'True', '2026-05-10 18:05:00'),
    ('pmehta', 'Pooja Mehta', 'pooja.mehta@birlabank.in', '+91-9824778899', 'EMP-4182', 'Retail', 'Relationship Manager', 'Ahmedabad', 'True', '2026-05-11 09:10:00'),
    ('arao', 'Aditya Rao', 'aditya.rao@birlabank.in', '+91-9986554433', NULL, 'Trading', 'Staff', 'Bengaluru', 'True', '2026-05-12 10:15:00'),
    ('dkrishnamurthy', 'Divya Krishnamurthy', 'divya.k@birlabank.in', '+91-9844112233', 'EMP-4208', 'Compliance', 'KYC Officer', 'Bengaluru', 'True', '2026-05-13 11:20:00'),
    ('rverma', 'Rahul Verma', 'rahul.verma@birlabank.in', '+91-9899007766', NULL, 'Treasury', 'Staff', 'Delhi-NCR', 'True', '2026-05-14 12:25:00'),
    ('nagarwal', 'Nisha Agarwal', 'nisha.agarwal@birlabank.in', '+91-9818776655', 'EMP-4234', 'Lending', 'Loan Officer', 'Delhi-NCR', 'True', '2026-05-15 13:30:00'),
    ('skapoor', 'Shruti Kapoor', 'shruti.kapoor@birlabank.in', '+91-9810223344', 'EMP-4247', 'Retail', 'Branch Manager', 'Delhi-NCR', 'True', '2026-05-16 14:35:00'),
    ('vbhat', 'Varun Bhat', 'varun.bhat@birlabank.in', '+91-9886443322', 'EMP-4260', 'Operations', 'AVP - Operations', 'Bengaluru', 'True', '2026-05-17 15:40:00'),
    ('abhattacharya', 'Akash Bhattacharya', 'akash.b@birlabank.in', '+91-9831997744', NULL, 'Trading', 'Staff', 'Kolkata', 'True', '2026-05-18 16:45:00'),
    ('lpillai', 'Lakshmi Pillai', 'lakshmi.p@birlabank.in', '+91-9446001122', NULL, 'IT', 'Staff', 'Kochi', 'True', '2026-05-19 17:50:00'),
    ('hpatel', 'Harsh Patel', 'harsh.p@birlabank.in', '+91-9824998877', NULL, 'Trading', 'Staff', 'Ahmedabad', 'True', '2026-05-20 18:55:00');

-- ─── 4. CRM — Contacts (SaaS conventions: uuid, tax_id, full_name)
CREATE TABLE crm_db.contacts (
    contact_uuid     VARCHAR(40)  PRIMARY KEY,
    full_name        VARCHAR(120),
    primary_email    VARCHAR(150),
    mobile_phone     VARCHAR(20),
    birthdate        DATE,
    tax_id           VARCHAR(15),
    region           VARCHAR(40),
    role_type        VARCHAR(50),
    lifecycle_stage  VARCHAR(30),
    updated_at       TIMESTAMP
);

INSERT INTO crm_db.contacts (contact_uuid, full_name, primary_email, mobile_phone, birthdate, tax_id, region, role_type, lifecycle_stage, updated_at) VALUES
    ('crm-0001-7919-0131', 'Rajesh K.', 'rajesh.kumar@birlabank.in', '+91-9810000001', '1985-03-14', 'AAAPK1234A', 'Delhi-NCR', 'Senior RM', 'Active', '2026-04-01 10:15:00'),
    ('crm-0004-1676-0524', 'Sneha Iyer', 'sneha.iyer@birlabank.in', '+91-9445001122', '1990-05-18', 'AAAPI4321D', 'Chennai', 'Senior RM', 'Active', '2026-04-02 11:22:00'),
    ('crm-0006-7514-0786', 'Anjali Reddy', 'anjali.reddy@birlabank.in', '+91-9000112233', '1995-02-14', 'AAAPR1010F', 'Hyderabad', 'Compliance RM', 'Active', '2026-04-03 12:29:00'),
    ('crm-0007-5433-0917', 'Karthik Menon', 'karthik.menon@birlabank.in', '+91-9447889900', '1987-08-05', 'AAAPM2020G', 'Kochi', 'Wealth RM', 'Active', '2026-04-04 13:36:00'),
    ('crm-0008-3352-1048', 'Deepika Joshi', 'deepika.joshi@birlabank.in', '+91-9920334455', '1993-12-25', 'AAAPJ3030H', 'Mumbai', 'Credit RM', 'Active', '2026-04-05 14:43:00'),
    ('crm-0010-9190-1310', 'Meera Krishnan', 'meera.krishnan@birlabank.in', '+91-9740112233', '1991-04-08', 'AAAPK5050J', 'Bengaluru', 'Senior RM', 'Active', '2026-04-06 15:50:00'),
    ('crm-0012-5028-1572', 'Kavya Pillai', 'kavya.pillai@birlabank.in', '+91-9495667788', '1994-01-15', 'AAAPP7070L', 'Kochi', 'Credit RM', 'Active', '2026-04-07 16:57:00'),
    ('crm-0014-0866-1834', 'Pooja Mehta', 'pooja.mehta@birlabank.in', '+91-9824778899', '1990-11-28', 'AAAPM9090N', 'Ahmedabad', 'Retail RM', 'Active', '2026-04-08 17:04:00'),
    ('crm-0015-8785-1965', 'Aditya Rao', 'aditya.rao@birlabank.in', '+91-9986554433', '1988-03-17', 'AAAPR1212O', 'Bengaluru', 'Wealth RM', 'Active', '2026-04-09 10:11:00'),
    ('crm-0016-6704-2096', 'Divya Krishnamurthy', 'divya.k@birlabank.in', '+91-9844112233', '1996-09-09', 'AAAPK1313P', 'Bengaluru', 'KYC Specialist', 'Active', '2026-04-10 11:18:00'),
    ('crm-0018-2542-2358', 'Nisha Agarwal', 'nisha.agarwal@birlabank.in', '+91-9818776655', '1992-06-25', 'AAAPA1515R', 'Delhi-NCR', 'Lending RM', 'Active', '2026-04-11 12:25:00'),
    ('crm-0019-0461-2489', 'Shruti Kapoor', 'shruti.kapoor@birlabank.in', '+91-9810223344', '1990-07-14', 'AAAPK2121X', 'Delhi-NCR', 'Branch Lead', 'Active', '2026-04-12 13:32:00'),
    ('crm-0020-8380-2620', 'Varun Bhat', 'varun.bhat@birlabank.in', '+91-9886443322', '1986-11-22', 'AAAPB2222Y', 'Bengaluru', 'Ops Lead', 'Active', '2026-04-13 14:39:00'),
    ('crm-0022-4218-2882', 'Neha Khanna', 'neha.khanna@birlabank.in', '+91-9999888877', '1993-08-19', 'AAAPK4444A', 'Delhi-NCR', 'Wealth RM', 'Active', '2026-04-14 15:46:00'),
    ('crm-0023-2137-3013', 'Chitra Iyer', 'chitra.iyer@birlabank.in', '+91-9444223311', '1991-03-25', 'AAAPI5555B', 'Chennai', 'KYC Specialist', 'Active', '2026-04-15 16:53:00'),
    ('crm-0026-5894-3406', 'Tejas Mukherjee', 'tejas.m@birlabank.in', '+91-9830998877', '1989-08-23', 'AAAPM8888E', 'Kolkata', 'Wealth RM', 'Active', '2026-04-16 17:00:00'),
    ('crm-0031-5489-4061', 'Ananya Bose', 'ananya.bose@birlabank.in', '+91-9836001122', '1994-09-03', 'AAAPB1414K', 'Kolkata', 'Credit RM', 'Active', '2026-04-17 10:07:00');

-- ─── 5. TRADE — Trading & Treasury Platform (terse finance shorthand)
CREATE TABLE trade_db.traders (
    trader_cd      VARCHAR(15)  PRIMARY KEY,
    trdr_nm        VARCHAR(80),
    eml            VARCHAR(150),
    mobile         VARCHAR(20),
    dob            DATE,
    id_proof_num   VARCHAR(15),
    desk           VARCHAR(30),
    book           VARCHAR(20),
    auth_lmt_inr   DECIMAL(15, 2),
    last_updt_dt   TIMESTAMP
);

INSERT INTO trade_db.traders (trader_cd, trdr_nm, eml, mobile, dob, id_proof_num, desk, book, auth_lmt_inr, last_updt_dt) VALUES
    ('TRD-00117', 'R Kumar', 'rajesh.kumar@birlabank.in', '+91-9810000001', '1985-03-14', 'AAAPK1234A', 'FX', 'FX-SPOT', '50000000', '2026-05-01 07:30:00'),
    ('TRD-00585', 'Vikram Singh', 'vikram.singh@birlabank.in', '+91-9818234567', '1982-09-30', 'AAAPS8765E', 'Rates', 'IRS-BOOK', '75000000', '2026-05-02 08:41:00'),
    ('TRD-00719', 'Karthik Menon', 'karthik.menon@birlabank.in', '+91-9447889900', '1987-08-05', 'AAAPM2020G', 'Equity', 'EQ-CASH', '25000000', '2026-05-03 09:52:00'),
    ('TRD-00953', 'Arjun Nair', 'arjun.nair@birlabank.in', '+91-9886234567', '1986-06-12', NULL, 'FX', 'FX-SPOT', '50000000', '2026-05-04 10:03:00'),
    ('TRD-01321', 'Suresh Banerjee', 'suresh.banerjee@birlabank.in', '+91-9836445566', '1983-07-03', 'AAAPB8080M', 'Rates', 'IRS-BOOK', '75000000', '2026-05-05 11:14:00'),
    ('TRD-01555', 'Aditya Rao', 'aditya.rao@birlabank.in', '+91-9986554433', '1988-03-17', NULL, 'Equity', 'EQ-CASH', '25000000', '2026-05-06 12:25:00'),
    ('TRD-01789', 'Rahul Verma', 'rahul.verma@birlabank.in', '+91-9899007766', '1985-12-01', 'AAAPV1414Q', 'Rates', 'IRS-BOOK', '75000000', '2026-05-07 13:36:00'),
    ('TRD-01923', 'Shruti Kapoor-Nair', 'shruti.kapoor@birlabank.in', '+91-9810223344', '1990-07-14', 'AAAPK2121X', 'FX', 'FX-SPOT', '50000000', '2026-05-08 14:47:00'),
    ('TRD-02040', 'V Bhat', 'varun.bhat@birlabank.in', '+91-9886443322', '1986-11-22', 'AAAPB2222Y', 'Equity', 'EQ-CASH', '25000000', '2026-05-09 15:58:00'),
    ('TRD-02157', 'Akash Bhattacharya', 'akash.b@birlabank.in', '+91-9831997744', '1989-04-30', NULL, 'Equity', 'EQ-CASH', '25000000', '2026-05-10 16:09:00'),
    ('TRD-02274', 'Neha Khanna', 'neha.khanna@birlabank.in', '+91-9999888877', '1993-08-19', 'AAAPK4444A', 'Equity', 'EQ-CASH', '25000000', '2026-05-11 17:20:00'),
    ('TRD-02408', 'Sandeep Reddy', 'sandeep.reddy@birlabank.in', '+91-9701234567', '1984-04-19', NULL, 'Rates', 'IRS-BOOK', '75000000', '2026-05-12 18:31:00'),
    ('TRD-02993', 'Subramaniam Iyer', 's.iyer@birlabank.in', '+91-9444556677', '1984-12-08', 'AAAPI1111H', 'FX', 'FX-SPOT', '50000000', '2026-05-13 19:42:00'),
    ('TRD-03010', 'Harsh Patel', 'harsh.p@birlabank.in', '+91-9824998877', '1988-06-15', NULL, 'Equity', 'EQ-CASH', '25000000', '2026-05-14 20:53:00'),
    ('TRD-03244', 'Ravi Subramanian', 'ravi.s@birlabank.in', '+91-9445998877', '1986-02-28', 'AAAPS1515L', 'Rates', 'IRS-BOOK', '75000000', '2026-05-15 07:04:00');

-- ─── 6. LOS — Loan Origination System (banking longhand)
CREATE TABLE los_db.loan_officer (
    officer_code           VARCHAR(20)  PRIMARY KEY,
    officer_full_name      VARCHAR(100),
    official_email_id      VARCHAR(150),
    mobile_no              VARCHAR(20),
    date_of_birth          DATE,
    permanent_account_no   VARCHAR(10),
    region_code            VARCHAR(10),
    product_specialty      VARCHAR(50),
    delegation_authority   VARCHAR(20),
    record_updated_on      TIMESTAMP
);

INSERT INTO los_db.loan_officer (officer_code, officer_full_name, official_email_id, mobile_no, date_of_birth, permanent_account_no, region_code, product_specialty, delegation_authority, record_updated_on) VALUES
    ('LOS-OFC-0046', 'Priya Sharma', 'priya.sharma@birlabank.in', '+91-9876543210', '1992-07-22', 'AAAPS5678B', 'MUM', 'Risk Underwriter', 'L4', '2026-05-01 11:40:00'),
    ('LOS-OFC-0069', 'Amit Patel', 'amit.patel@birlabank.in', '+91-9830007890', '1988-11-09', 'AAAPP9999C', 'AMD', 'Home Loans', 'L3', '2026-05-02 12:53:00'),
    ('LOS-OFC-0138', 'Anjali Reddy', 'anjali.reddy@birlabank.in', '+91-9000112233', '1995-02-14', 'AAAPR1010F', 'HYD', 'KYC Reviewer', 'L2', '2026-05-03 13:06:00'),
    ('LOS-OFC-0184', 'Deepika Joshi', 'deepika.joshi@birlabank.in', '+91-9920334455', '1993-12-25', 'AAAPJ3030H', 'MUM', 'Credit Risk', 'L4', '2026-05-04 14:19:00'),
    ('LOS-OFC-0253', 'Rohan Desai', 'rohan.desai@birlabank.in', '+91-9821554433', '1989-10-20', 'AAAPD6060K', 'MUM', 'SME Loans', 'L3', '2026-05-05 15:32:00'),
    ('LOS-OFC-0276', 'Kavya Pillai', 'kavya.pillai@birlabank.in', '+91-9495667788', '1994-01-15', 'AAAPP7070L', 'KCH', 'Credit Risk', 'L4', '2026-05-06 16:45:00'),
    ('LOS-OFC-0368', 'Divya K.', 'divya.k@birlabank.in', '+91-9844112233', '1996-09-09', 'AAAPK1313P', 'BLR', 'KYC Reviewer', 'L2', '2026-05-07 17:58:00'),
    ('LOS-OFC-0414', 'Nisha Agarwal', 'nisha.agarwal@birlabank.in', '+91-9818776655', '1992-06-25', 'AAAPA1515R', 'DEL', 'Personal Loans', 'L2', '2026-05-08 18:11:00'),
    ('LOS-OFC-0437', 'S Kapoor', 'shruti.kapoor@birlabank.in', '+91-9810223344', '1990-07-14', 'AAAPK2121X', 'DEL', 'SME Loans', 'L3', '2026-05-09 19:24:00'),
    ('LOS-OFC-0460', 'Varun Bhat', 'varun.bhat@birlabank.in', '+91-9886443322', '1986-11-22', 'AAAPB2222Y', 'BLR', 'Operations', 'L3', '2026-05-10 11:37:00'),
    ('LOS-OFC-0529', 'Chitra Iyer', 'chitra.iyer@birlabank.in', '+91-9444223311', '1991-03-25', 'AAAPI5555B', 'CHN', 'KYC Reviewer', 'L2', '2026-05-11 12:50:00'),
    ('LOS-OFC-0644', 'Manoj Pillai', 'manoj.p@birlabank.in', '+91-9495334455', '1987-10-30', 'AAAPP1010G', 'KCH', 'Home Loans', 'L3', '2026-05-12 13:03:00'),
    ('LOS-OFC-0713', 'Ananya Bose', 'ananya.bose@birlabank.in', '+91-9836001122', '1994-09-03', 'AAAPB1414K', 'KOL', 'Credit Risk', 'L4', '2026-05-13 14:16:00'),
    ('LOS-OFC-0736', 'Ravi Subramanian', 'ravi.s@birlabank.in', '+91-9445998877', '1986-02-28', 'AAAPS1515L', 'CHN', 'Operations', 'L3', '2026-05-14 15:29:00'),
    ('LOS-OFC-0759', 'Vijay Krishnan', 'vijay.k@birlabank.in', '+91-9740887766', '1990-12-12', 'AAAPK1616M', 'BLR', 'Personal Loans', 'L2', '2026-05-15 16:42:00');

-- ─── 7. Planted cross-correlation case ──────────────────────────────────────
-- Simulates a real data-entry error: Neha Khanna (person 22) had her CRM
-- row created with phone+DOB belonging to Rohan Desai (person 11). Email
-- still resolves Neha correctly across her CRM and TRADE rows, but the
-- pipeline emits a POSSIBLE_DUPLICATE edge between her cluster and Rohans.
UPDATE crm_db.contacts SET mobile_phone = '+91-9821554433', birthdate = '1989-10-20'
WHERE contact_uuid = 'crm-0022-4218-2882';

-- ─── 8. Indexes (the JdbcSourceAdapter SELECTs are full-table scans;
--     in production add covering indexes on the modified-timestamp column)
CREATE INDEX idx_cbs_modified   ON cbs_db.employee_master (last_modified_ts);
CREATE INDEX idx_ad_changed     ON ad_db.ad_users (when_changed);
CREATE INDEX idx_crm_updated    ON crm_db.contacts (updated_at);
CREATE INDEX idx_trade_updated  ON trade_db.traders (last_updt_dt);
CREATE INDEX idx_los_updated    ON los_db.loan_officer (record_updated_on);

-- ─── End of seed data ─────────────────────────────────────────────────────
