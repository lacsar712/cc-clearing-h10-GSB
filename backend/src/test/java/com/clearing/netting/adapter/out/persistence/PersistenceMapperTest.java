package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.ObligationJpaEntity;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: read-side party mapping must keep payer/payee in the same order as the write side.
 * Historically the read path went through a swapped mapping (PartyMappingBug), so the list and
 * netting read payer/payee reversed relative to what was entered.
 */
class PersistenceMapperTest {

    @Test
    void toDomainKeepsPayerAndPayeeInOrder() {
        ObligationJpaEntity e = new ObligationJpaEntity();
        e.setObligationId("o-1");
        e.setPayerMemberId("payer-X");
        e.setPayeeMemberId("payee-Y");
        e.setCurrency("USD");
        e.setAmount(new BigDecimal("100"));
        e.setTradeDate(LocalDate.of(2026, 9, 1));
        e.setSettleDate(LocalDate.of(2026, 9, 10));
        e.setStatus(ObligationStatus.OPEN);

        TradeObligation o = PersistenceMapper.toDomain(e);

        assertEquals("payer-X", o.getPayerMemberId());
        assertEquals("payee-Y", o.getPayeeMemberId());
    }

    @Test
    void entityRoundTripPreservesParties() {
        TradeObligation original = new TradeObligation(
                "o-2",
                "payer-X",
                "payee-Y",
                "USD",
                new BigDecimal("100"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                ObligationStatus.OPEN,
                null);

        TradeObligation restored = PersistenceMapper.toDomain(PersistenceMapper.toEntity(original));

        assertEquals(original.getPayerMemberId(), restored.getPayerMemberId());
        assertEquals(original.getPayeeMemberId(), restored.getPayeeMemberId());
        assertEquals(original.getAmount(), restored.getAmount());
        assertEquals(original.getCurrency(), restored.getCurrency());
        assertEquals(original.getStatus(), restored.getStatus());
    }
}
