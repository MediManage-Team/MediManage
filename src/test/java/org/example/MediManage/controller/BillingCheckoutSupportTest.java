package org.example.MediManage.controller;

import org.example.MediManage.model.PaymentSplit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingCheckoutSupportTest {

    @Test
    void compositePaymentModeDefaultsToCashWhenNoSplitsExist() {
        assertEquals("Cash", BillingCheckoutSupport.compositePaymentMode(List.of()));
        assertEquals("Cash", BillingCheckoutSupport.compositePaymentMode(null));
    }

    @Test
    void compositePaymentModePreservesDistinctOrderForMultipleSplits() {
        List<PaymentSplit> splits = List.of(
                new PaymentSplit("Cash", 50.0),
                new PaymentSplit("UPI", 30.0),
                new PaymentSplit("Cash", 20.0),
                new PaymentSplit("Credit", 10.0));

        assertEquals("Cash+UPI+Credit", BillingCheckoutSupport.compositePaymentMode(splits));
    }

    @Test
    void hasMatchingTotalUsesPaymentTolerance() {
        List<PaymentSplit> matching = List.of(
                new PaymentSplit("Cash", 50.005),
                new PaymentSplit("UPI", 49.995));
        List<PaymentSplit> mismatched = List.of(
                new PaymentSplit("Cash", 40.0),
                new PaymentSplit("UPI", 50.0));

        assertTrue(BillingCheckoutSupport.hasMatchingTotal(matching, 100.0));
        assertFalse(BillingCheckoutSupport.hasMatchingTotal(mismatched, 100.0));
    }
}
