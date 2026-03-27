package com.playbook.consumer.service;

import com.playbook.consumer.model.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final ConcurrentHashMap<String, BigDecimal> balances = new ConcurrentHashMap<>();

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    @KafkaListener(topics = "account-transactions", groupId = "balance-group")
    public void handleTransactionEvent(TransactionEvent event) {
        BigDecimal newBalance = balances.compute(event.accountId(), (accountId, currentBalance) -> {
            if (currentBalance == null) {
                currentBalance = BigDecimal.ZERO;
            }
            if ("CREDIT".equals(event.type())) {
                return currentBalance.add(event.amount());
            } else {
                return currentBalance.subtract(event.amount());
            }
        });

        String amountPrefix = "CREDIT".equals(event.type()) ? "+" : "-";

        log.info("""

                ============================================
                  TRANSACTION RECORDED
                --------------------------------------------
                  TXN:      {}
                  Account:  {}
                  Type:     {}
                  Amount:   {}{}
                  Desc:     {}
                  Balance:  {}
                ============================================""",
                event.transactionId(),
                event.accountId(),
                event.type(),
                amountPrefix,
                CURRENCY_FORMAT.format(event.amount()),
                event.description(),
                CURRENCY_FORMAT.format(newBalance)
        );
    }
}
