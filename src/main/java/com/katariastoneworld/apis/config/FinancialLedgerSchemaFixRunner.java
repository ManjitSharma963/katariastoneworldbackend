package com.katariastoneworld.apis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps DB schema compatible with {@link com.katariastoneworld.apis.entity.FinancialLedgerEntry}.
 *
 * Some older DBs have `financial_ledger.is_deleted` as NOT NULL with no default, but the backend inserts
 * do not provide that column. This runner ensures the column has a default of 0 at startup.
 */
@Component
public class FinancialLedgerSchemaFixRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FinancialLedgerSchemaFixRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public FinancialLedgerSchemaFixRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger'",
                    Integer.class
            );
            if (tableExists == null || tableExists <= 0) {
                return;
            }

            Integer colExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger' " +
                            "AND COLUMN_NAME = 'is_deleted'",
                    Integer.class
            );

            if (colExists == null || colExists <= 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE financial_ledger " +
                                "ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0"
                );
                log.info("Applied schema fix: added financial_ledger.is_deleted DEFAULT 0");
                return;
            }

            String defaultVal = jdbcTemplate.queryForObject(
                    "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger' " +
                            "AND COLUMN_NAME = 'is_deleted'",
                    String.class
            );

            // If there is no default, inserts will fail.
            if (defaultVal == null) {
                jdbcTemplate.execute(
                        "ALTER TABLE financial_ledger " +
                                "MODIFY is_deleted TINYINT(1) NOT NULL DEFAULT 0"
                );
                log.info("Applied schema fix: set financial_ledger.is_deleted DEFAULT 0");
            }
        } catch (Exception e) {
            // Do not crash the application due to a schema fix attempt; log and proceed.
            log.warn("Financial ledger schema fix failed (non-fatal). {}", e.getMessage(), e);
        }
    }
}

