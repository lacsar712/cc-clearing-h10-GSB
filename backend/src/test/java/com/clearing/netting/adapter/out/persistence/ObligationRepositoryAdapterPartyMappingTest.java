package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.ObligationJpaRepository;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.service.MultilateralNettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end persistence regression for the payer/payee read mapping:
 * obligations written through the adapter must come back from findById/findAll
 * (the list path) with the same payer/payee, and netting over the read-back
 * obligations must produce positions with the same direction as the entry.
 */
@DataJpaTest
class ObligationRepositoryAdapterPartyMappingTest {

    private static final String PAYER_BANK = "PAYER_BANK";
    private static final String PAYEE_BANK = "PAYEE_BANK";

    @Autowired
    private ObligationJpaRepository jpaRepository;

    private ObligationRepositoryAdapter adapter() {
        return new ObligationRepositoryAdapter(jpaRepository);
    }

    private TradeObligation entered(String id, String payer, String payee, String amount, LocalDate settleDate) {
        return new TradeObligation(
                id, payer, payee, "USD", new BigDecimal(amount),
                settleDate.minusDays(1), settleDate, ObligationStatus.OPEN, null);
    }

    @Test
    void readBackPayerAndPayeeMatchWrittenObligation() {
        LocalDate settleDate = LocalDate.of(2026, 9, 10);
        TradeObligation saved = adapter().save(
                entered("ob-1", PAYER_BANK, PAYEE_BANK, "100", settleDate));

        TradeObligation byId = adapter().findById(saved.getObligationId()).orElseThrow();
        assertEquals(PAYER_BANK, byId.getPayerMemberId());
        assertEquals(PAYEE_BANK, byId.getPayeeMemberId());

        List<TradeObligation> listed = adapter().findAll();
        assertEquals(1, listed.size());
        TradeObligation row = listed.get(0);
        // list view must not display the parties reversed
        assertEquals(PAYER_BANK, row.getPayerMemberId());
        assertEquals(PAYEE_BANK, row.getPayeeMemberId());
    }

    @Test
    void nettingOverPersistedObligationsKeepsEntryDirection() {
        LocalDate settleDate = LocalDate.of(2026, 9, 10);
        ObligationRepositoryAdapter adapter = adapter();
        adapter.save(entered("ob-a-b", "A", "B", "100", settleDate));
        adapter.save(entered("ob-b-c", "B", "C", "60", settleDate));
        adapter.save(entered("ob-c-a", "C", "A", "40", settleDate));

        // the netting run reads OPEN obligations straight out of persistence
        List<TradeObligation> reloaded =
                adapter.findOpenBySettleDateAndCurrency(settleDate, "USD");
        assertEquals(3, reloaded.size());

        // parties survive the read in the exact entered direction
        Map<String, TradeObligation> byId = reloaded.stream()
                .collect(Collectors.toMap(TradeObligation::getObligationId, Function.identity()));
        assertEquals("A", byId.get("ob-a-b").getPayerMemberId());
        assertEquals("B", byId.get("ob-a-b").getPayeeMemberId());
        assertEquals("B", byId.get("ob-b-c").getPayerMemberId());
        assertEquals("C", byId.get("ob-b-c").getPayeeMemberId());
        assertEquals("C", byId.get("ob-c-a").getPayerMemberId());
        assertEquals("A", byId.get("ob-c-a").getPayeeMemberId());

        Map<String, Member> members = Map.of(
                "A", new Member("A", "Bank A", MemberStatus.ACTIVE),
                "B", new Member("B", "Bank B", MemberStatus.ACTIVE),
                "C", new Member("C", "Bank C", MemberStatus.ACTIVE));

        List<NetPosition> positions =
                new MultilateralNettingService().net("run-1", "USD", reloaded, members);
        Map<String, BigDecimal> netByMember = positions.stream()
                .collect(Collectors.toMap(NetPosition::getMemberId, NetPosition::getNetAmount));

        // A pays 100 / receives 40 => net payable -60
        assertEquals(0, netByMember.get("A").compareTo(new BigDecimal("-60.00000000")));
        // B receives 100 / pays 60 => net receivable +40
        assertEquals(0, netByMember.get("B").compareTo(new BigDecimal("40.00000000")));
        // C receives 60 / pays 40 => net receivable +20
        assertEquals(0, netByMember.get("C").compareTo(new BigDecimal("20.00000000")));
    }
}
