-- BML Switch — create DB + user (run as MySQL admin / root)
-- Callers: bank DBA before Tomcat deploy.
-- Schema files: 01_schema.sql, 02_seed.sql, 03_imal_cif_acc_gl.sql
-- User: "tomcat pe deploy ... DB ky script DB pe run"

CREATE DATABASE IF NOT EXISTS bml_switch
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'bml_switch'@'%' IDENTIFIED BY 'change_me';
CREATE USER IF NOT EXISTS 'bml_switch'@'localhost' IDENTIFIED BY 'change_me';

GRANT ALL PRIVILEGES ON bml_switch.* TO 'bml_switch'@'%';
GRANT ALL PRIVILEGES ON bml_switch.* TO 'bml_switch'@'localhost';
FLUSH PRIVILEGES;

-- Then either:
-- A) Run 01 → 02 → 03 against bml_switch, set FLYWAY_ENABLED=false
-- B) Leave DB empty, set FLYWAY_ENABLED=true (app migrates on first start)
