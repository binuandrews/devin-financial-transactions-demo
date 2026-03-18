package com.fraud.riskscorer;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.*;

public class RiskConfigTest {

    @Test
    public void testHighAmountThreshold() {
        assertEquals(10_000.0, RiskConfig.HIGH_AMOUNT_THRESHOLD, 0.0);
    }

    @Test
    public void testAmountScaleCap() {
        assertEquals(500_000.0, RiskConfig.AMOUNT_SCALE_CAP, 0.0);
    }

    @Test
    public void testHighRiskTypesContainsCashOut() {
        assertTrue(RiskConfig.HIGH_RISK_TYPES.contains("CASH_OUT"));
    }

    @Test
    public void testHighRiskTypesContainsTransfer() {
        assertTrue(RiskConfig.HIGH_RISK_TYPES.contains("TRANSFER"));
    }

    @Test
    public void testHighRiskTypesDoesNotContainPayment() {
        assertFalse(RiskConfig.HIGH_RISK_TYPES.contains("PAYMENT"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHighRiskTypesIsUnmodifiable() {
        RiskConfig.HIGH_RISK_TYPES.add("DEBIT");
    }

    @Test
    public void testWeights() {
        assertEquals(30.0, RiskConfig.WEIGHT_HIGH_AMOUNT_FLAG, 0.0);
        assertEquals(10.0, RiskConfig.WEIGHT_AMOUNT_SCALE, 0.0);
        assertEquals(20.0, RiskConfig.WEIGHT_TYPE, 0.0);
        assertEquals(15.0, RiskConfig.WEIGHT_NEW_DEST, 0.0);
        assertEquals(15.0, RiskConfig.WEIGHT_RAPID_TXN, 0.0);
        assertEquals(10.0, RiskConfig.WEIGHT_HIGH_THEN_CASHOUT, 0.0);
    }

    @Test
    public void testPrivateConstructor() throws Exception {
        Constructor<RiskConfig> constructor = RiskConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            fail("Private constructor should not throw: " + e.getCause());
        }
    }
}
