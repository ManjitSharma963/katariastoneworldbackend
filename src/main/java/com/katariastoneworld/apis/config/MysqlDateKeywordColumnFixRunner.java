package com.katariastoneworld.apis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repairs Hibernate {@code ddl-auto=update} drift where a legacy NOT NULL {@code date} column coexisted
 * with the renamed column ({@code payment_date}, {@code expense_date}, {@code event_date}), so inserts
 * populated only the new column and MySQL raised 1364 on {@code date}.
 * <p>
 * Do not use {@code hibernate.globally_quoted_identifiers=true} with MySQL: it quotes data types in DDL
 * ({@code `INT`}, {@code `VARCHAR`}) and breaks {@code ALTER TABLE ... MODIFY}.
 */
@Component
@Order(Integer.MAX_VALUE)
public class MysqlDateKeywordColumnFixRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MysqlDateKeywordColumnFixRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public MysqlDateKeywordColumnFixRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            fixClientPurchasePayments();
            fixExpenses();
        } catch (Exception e) {
            log.warn("MySQL date-column compatibility fix failed (non-fatal): {}", e.getMessage(), e);
        }
    }

    private boolean tableExists(String table) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                table
        );
        return n != null && n > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column
        );
        return n != null && n > 0;
    }

    private void fixClientPurchasePayments() {
        final String t = "client_purchase_payments";
        if (!tableExists(t)) {
            return;
        }
        boolean hasDate = columnExists(t, "date");
        boolean hasPaymentDate = columnExists(t, "payment_date");
        if (hasDate && hasPaymentDate) {
            jdbcTemplate.execute(
                    "UPDATE `" + t + "` SET payment_date = COALESCE(payment_date, `date`)"
            );
            jdbcTemplate.execute("ALTER TABLE `" + t + "` DROP COLUMN `date`");
            log.info("Applied fix: {} had both `date` and payment_date; merged into payment_date and dropped `date`.", t);
            return;
        }
        if (hasDate && !hasPaymentDate) {
            jdbcTemplate.execute("ALTER TABLE `" + t + "` CHANGE COLUMN `date` payment_date DATE NOT NULL");
            log.info("Applied fix: renamed {}.`date` -> payment_date (MySQL keyword / 1364 safety).", t);
        }
    }

    private void fixExpenses() {
        final String t = "expenses";
        if (!tableExists(t)) {
            return;
        }
        boolean hasDate = columnExists(t, "date");
        boolean hasExpenseDate = columnExists(t, "expense_date");
        if (hasDate && hasExpenseDate) {
            jdbcTemplate.execute(
                    "UPDATE `" + t + "` SET expense_date = COALESCE(expense_date, `date`)"
            );
            jdbcTemplate.execute("ALTER TABLE `" + t + "` DROP COLUMN `date`");
            log.info("Applied fix: {} had both `date` and expense_date; merged into expense_date and dropped `date`.", t);
            return;
        }
        if (hasDate && !hasExpenseDate) {
            jdbcTemplate.execute("ALTER TABLE `" + t + "` CHANGE COLUMN `date` expense_date DATE NOT NULL");
            log.info("Applied fix: renamed {}.`date` -> expense_date (MySQL keyword / 1364 safety).", t);
        }
    }

}
