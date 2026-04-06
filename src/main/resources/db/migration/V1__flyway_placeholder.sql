-- No-op placeholder so Flyway has an initial version when spring.flyway.enabled=true.
-- Existing MySQL databases: use spring.flyway.baseline-on-migrate=true before first run.
SELECT 1;
