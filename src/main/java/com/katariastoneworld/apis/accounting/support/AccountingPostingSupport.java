package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
import com.katariastoneworld.apis.accounting.dto.PostedTransactionResult;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared posting/void helpers: logging, balanced-entry metadata, engine delegation.
 */
@Component
public class AccountingPostingSupport {

    private static final Logger log = LoggerFactory.getLogger(AccountingPostingSupport.class);

    private final AccountingTransactionService accountingTransactionService;

    public AccountingPostingSupport(AccountingTransactionService accountingTransactionService) {
        this.accountingTransactionService = accountingTransactionService;
    }

    public PostedTransactionResult postIn(PostMoneyCommand command, String sourceModule) {
        logPost("IN", sourceModule, command);
        return accountingTransactionService.postIn(command);
    }

    public PostedTransactionResult postOut(PostMoneyCommand command, String sourceModule) {
        logPost("OUT", sourceModule, command);
        return accountingTransactionService.postOut(command);
    }

    public void voidByRequestId(String location, String requestId, String reason, String sourceModule) {
        log.info("accounting_void module={} requestId={} reason={}", sourceModule, requestId, reason);
        accountingTransactionService.voidByRequestId(VoidByRequestIdCommand.of(location, requestId, reason));
    }

    public void voidByReference(
            String location,
            MoneyReferenceType referenceType,
            Long referenceId,
            MoneyCategory category,
            String ledgerTxnType,
            String reason,
            String sourceModule) {
        log.info("accounting_void module={} refType={} refId={} category={} txnType={} reason={}",
                sourceModule, referenceType, referenceId, category, ledgerTxnType, reason);
        accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                location,
                AccountingReference.of(referenceType, referenceId, category, ledgerTxnType),
                reason));
    }

    public static String balancedEntryMetadata(String debitAccount, String creditAccount) {
        return "{\"debitAccount\":\"" + escape(debitAccount) + "\",\"creditAccount\":\"" + escape(creditAccount) + "\"}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void logPost(String direction, String module, PostMoneyCommand command) {
        log.info(
                "accounting_post_{} module={} requestId={} amount={} category={} refType={} refId={} txnType={}",
                direction.toLowerCase(),
                module,
                command.requestId(),
                command.amount(),
                command.category(),
                command.referenceType(),
                command.referenceId(),
                command.ledgerTxnType());
    }
}
