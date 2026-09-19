package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.service.MultilateralNettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: obligations persisted then read back (list/netting read paths)
 * must keep payer/payee exactly as written — no swap anywhere.
 */
@DataJpaTest
@Import(ObligationRepositoryAdapter.class)
class ObligationRepositoryAdapterTest {

    private static final String PAYER = "PAYER_BANK";
    private static final String PAYEE = "PAYEE_BANK";
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 9, 18);
    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 9, 20);

    @Autowired
    private ObligationRepositoryAdapter adapter;

    @Test
    void readBackKeepsPayerAndPayeeOnEveryReadPath() {
        TradeObligation saved = adapter.save(TradeObligation.open(
                PAYER, PAYEE, "USD", new BigDecimal("250.5"), TRADE_DATE, SETTLE_DATE));

        assertParties(adapter.findById(saved.getObligationId()).orElseThrow());
        assertParties(only(adapter.findAll()));
        assertParties(only(adapter.findByFilters("USD", SETTLE_DATE, ObligationStatus.OPEN)));
        assertParties(only(adapter.findOpenBySettleDateAndCurrency(SETTLE_DATE, "USD")));
    }

    @Test
    void nettingOnReadBackObligationsKeepsEnteredDirection() {
        adapter.save(TradeObligation.open(
                PAYER, PAYEE, "USD", new BigDecimal("100"), TRADE_DATE, SETTLE_DATE));
        adapter.save(TradeObligation.open(
                PAYEE, PAYER, "USD", new BigDecimal("30"), TRADE_DATE, SETTLE_DATE));

        List<TradeObligation> readBack = adapter.findOpenBySettleDateAndCurrency(SETTLE_DATE, "USD");
        assertEquals(2, readBack.size());

        Map<String, Member> members = Map.of(
                PAYER, new Member(PAYER, "Payer Bank", MemberStatus.ACTIVE),
                PAYEE, new Member(PAYEE, "Payee Bank", MemberStatus.ACTIVE));
        List<NetPosition> positions = new MultilateralNettingService().net("run-t", "USD", readBack, members);

        // entered: PAYER pays 100, receives 30 => net -70; PAYEE net +70
        Map<String, BigDecimal> byMember = positions.stream()
                .collect(Collectors.toMap(NetPosition::getMemberId, NetPosition::getNetAmount));
        assertEquals(0, byMember.get(PAYER).compareTo(new BigDecimal("-70.00000000")));
        assertEquals(0, byMember.get(PAYEE).compareTo(new BigDecimal("70.00000000")));
    }

    private void assertParties(TradeObligation o) {
        assertEquals(PAYER, o.getPayerMemberId(), "payer swapped on read");
        assertEquals(PAYEE, o.getPayeeMemberId(), "payee swapped on read");
    }

    private TradeObligation only(List<TradeObligation> list) {
        assertEquals(1, list.size(), "expected exactly one obligation");
        return list.get(0);
    }
}
