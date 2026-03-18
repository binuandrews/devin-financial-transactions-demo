package com.fraud.riskscorer;

import org.junit.Test;

import static org.junit.Assert.*;

public class ScoredTransactionTest {

    @Test
    public void testConstructorAndGetters() {
        Transaction txn = new Transaction(1, "PAYMENT", 5000.0, "C123",
                10000, 5000, "M456", 0, 0, 0, 0);
        ScoredTransaction scored = new ScoredTransaction(txn, 45, "MEDIUM");

        assertSame(txn, scored.getTransaction());
        assertEquals(45, scored.getRiskScore());
        assertEquals("MEDIUM", scored.getRiskCategory());
    }

    @Test
    public void testLowRiskTransaction() {
        Transaction txn = new Transaction(1, "PAYMENT", 100.0, "C001",
                500, 400, "M001", 0, 0, 0, 0);
        ScoredTransaction scored = new ScoredTransaction(txn, 10, "LOW");

        assertEquals(10, scored.getRiskScore());
        assertEquals("LOW", scored.getRiskCategory());
    }

    @Test
    public void testHighRiskTransaction() {
        Transaction txn = new Transaction(1, "CASH_OUT", 50000.0, "C999",
                60000, 10000, "C888", 100000, 150000, 1, 1);
        ScoredTransaction scored = new ScoredTransaction(txn, 85, "HIGH");

        assertEquals(85, scored.getRiskScore());
        assertEquals("HIGH", scored.getRiskCategory());
    }
}
