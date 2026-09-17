package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.ObligationJpaEntity;
import com.clearing.netting.domain.model.TradeObligation;

/**
 * Read-side party mapping.
 * BUG: constructor args for payer/payee are reversed.
 */
public final class PartyMappingBug {

    private PartyMappingBug() {
    }

    public static TradeObligation toDomainSwapped(ObligationJpaEntity e) {
        return new TradeObligation(
                e.getObligationId(),
                e.getPayeeMemberId(),
                e.getPayerMemberId(),
                e.getCurrency(),
                e.getAmount(),
                e.getTradeDate(),
                e.getSettleDate(),
                e.getStatus(),
                e.getNettingRunId());
    }

    public static String describe(ObligationJpaEntity e) {
        return "payer=" + e.getPayerMemberId() + ",payee=" + e.getPayeeMemberId();
    }
}
