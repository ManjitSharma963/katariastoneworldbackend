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
 * <ul>
 *   <li>Some older DBs have {@code financial_ledger.is_deleted} NOT NULL with no default — we set DEFAULT 0.</li>
 *   <li>Very old DBs use MySQL {@code ENUM} for {@code event_type} (e.g. only BILL_PAYMENT, ADVANCE_DEPOSIT).
 *       New rows use CLIENT_PAYMENT_IN / CLIENT_PAYMENT_OUT; ENUM rejects them (SQL 1265 data truncated).
 *       We convert {@code event_type} to {@code VARCHAR(32)} to match {@code db/financial_ledger.mysql.sql}.</li>
 *   <li>Same for {@code payment_mode}: legacy ENUMs often omit {@code OTHER} (and differ from
 *       {@link com.katariastoneworld.apis.entity.BillPaymentMode}). We convert to {@code VARCHAR(32)}.</li>
 * </ul>
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

            fixLegacyDateColumnIfPresent();
            fixEventTypeColumnIfEnum();
            fixPaymentModeColumnIfEnum();

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

    /**
     * Older DBs (or Hibernate ddl-auto drift) can keep a NOT NULL column {@code date} while the entity maps
     * {@code event_date} only — inserts then fail with MySQL 1364 "Field 'date' doesn't have a default value".
     */
    private void fixLegacyDateColumnIfPresent() {
        Integer hasDate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger' AND COLUMN_NAME = 'date'",
                Integer.class
        );
        if (hasDate == null || hasDate <= 0) {
            return;
        }
        Integer hasEventDate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger' AND COLUMN_NAME = 'event_date'",
                Integer.class
        );
        boolean eventDatePresent = hasEventDate != null && hasEventDate > 0;
        if (eventDatePresent) {
            jdbcTemplate.execute(
                    "UPDATE financial_ledger SET event_date = COALESCE(event_date, `date`)"
            );
            jdbcTemplate.execute("ALTER TABLE financial_ledger DROP COLUMN `date`");
            log.info(
                    "Applied schema fix: financial_ledger had legacy `date` alongside event_date; merged into event_date and dropped `date`."
            );
        } else {
            jdbcTemplate.execute(
                    "ALTER TABLE financial_ledger CHANGE COLUMN `date` event_date DATE NOT NULL"
            );
            log.info("Applied schema fix: renamed financial_ledger.`date` -> event_date.");
        }
    }

    private void fixEventTypeColumnIfEnum() {
        modifyFinancialLedgerColumnToVarcharIfEnum(
                "event_type",
                "CLIENT_PAYMENT_IN / CLIENT_PAYMENT_OUT and future event types"
        );
    }

    private void fixPaymentModeColumnIfEnum() {
        modifyFinancialLedgerColumnToVarcharIfEnum(
                "payment_mode",
                "OTHER and all BillPaymentMode values"
        );
    }

    /**
     * Legacy schemas used ENUM; JPA persists {@link jakarta.persistence.EnumType#STRING} names that may not be listed.
     */
    private void modifyFinancialLedgerColumnToVarcharIfEnum(String columnName, String reasonLogFragment) {
        String dataType = jdbcTemplate.query(
                "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'financial_ledger' AND COLUMN_NAME = '"
                        + columnName
                        + "'",
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (dataType == null) {
            return;
        }
        if (!"enum".equalsIgnoreCase(dataType.trim())) {
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE financial_ledger MODIFY COLUMN `" + columnName + "` VARCHAR(32) NOT NULL"
        );
        log.info(
                "Applied schema fix: financial_ledger.{} was ENUM; converted to VARCHAR(32) for {}.",
                columnName,
                reasonLogFragment
        );
    }
}

