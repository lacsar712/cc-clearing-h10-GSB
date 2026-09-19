package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.ObligationJpaEntity;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: read-side mapping must not swap payer/payee (ex-PartyMappingBug).
 */
class PersistenceMapperTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 9, 18);
    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 9, 20);

    @Test
    void entityToDomainKeepsPayerAndPayee() {
        ObligationJpaEntity e = entity("ob-1", "PAYER_BANK", "PAYEE_BANK");

        TradeObligation o = PersistenceMapper.toDomain(e);

        assertEquals("PAYER_BANK", o.getPayerMemberId());
        assertEquals("PAYEE_BANK", o.getPayeeMemberId());
    }

    @Test
    void writeThenReadRoundTripPreservesParties() {
        TradeObligation written = TradeObligation.open(
                "PAYER_BANK", "PAYEE_BANK", "USD",
                new BigDecimal("123.45"), TRADE_DATE, SETTLE_DATE);

        TradeObligation readBack = PersistenceMapper.toDomain(PersistenceMapper.toEntity(written));

        assertEquals(written.getObligationId(), readBack.getObligationId());
        assertEquals(written.getPayerMemberId(), readBack.getPayerMemberId());
        assertEquals(written.getPayeeMemberId(), readBack.getPayeeMemberId());
        assertEquals(written.getCurrency(), readBack.getCurrency());
        assertEquals(0, written.getAmount().compareTo(readBack.getAmount()));
        assertEquals(written.getStatus(), readBack.getStatus());
    }

    private ObligationJpaEntity entity(String id, String payer, String payee) {
        ObligationJpaEntity e = new ObligationJpaEntity();
        e.setObligationId(id);
        e.setPayerMemberId(payer);
        e.setPayeeMemberId(payee);
        e.setCurrency("USD");
        e.setAmount(new BigDecimal("100.00000000"));
        e.setTradeDate(TRADE_DATE);
        e.setSettleDate(SETTLE_DATE);
        e.setStatus(ObligationStatus.OPEN);
        e.setNettingRunId(null);
        return e;
    }
}
