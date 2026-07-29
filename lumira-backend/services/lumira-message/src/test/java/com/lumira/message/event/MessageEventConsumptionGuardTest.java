package com.lumira.message.event;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageEventConsumptionGuardTest {

    @Test
    void commitsSideEffectOnlyWhenReceiptIsNew() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = immediateTransactions();
        MessageEventConsumptionGuard guard = new MessageEventConsumptionGuard(jdbc, transactions);
        Runnable sideEffect = mock(Runnable.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1);

        boolean executed = guard.executeOnce(identity(), sideEffect);

        assertThat(executed).isTrue();
        verify(sideEffect).run();
    }

    @Test
    void skipsSideEffectForDuplicateReceipt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = immediateTransactions();
        MessageEventConsumptionGuard guard = new MessageEventConsumptionGuard(jdbc, transactions);
        Runnable sideEffect = mock(Runnable.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean executed = guard.executeOnce(identity(), sideEffect);

        assertThat(executed).isFalse();
        verify(sideEffect, never()).run();
    }

    @SuppressWarnings("unchecked")
    private TransactionTemplate immediateTransactions() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private MessageEventConsumptionGuard.EventIdentity identity() {
        return new MessageEventConsumptionGuard.EventIdentity(
                "message-review-result-v1",
                "1001",
                ReviewResultEventStreamConsumer.EVENT_TYPE,
                "review",
                "500:100:1"
        );
    }
}
