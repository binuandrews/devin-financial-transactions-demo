package com.fraud.riskscorer;

import org.junit.Test;

import static org.junit.Assert.*;

public class TransactionTest {

    @Test
    public void testDefaultConstructor() {
        Transaction txn = new Transaction();
        assertEquals(0, txn.getStep());
        assertNull(txn.getType());
        assertEquals(0.0, txn.getAmount(), 0.0);
        assertNull(txn.getNameOrig());
        assertEquals(0.0, txn.getOldbalanceOrg(), 0.0);
        assertEquals(0.0, txn.getNewbalanceOrig(), 0.0);
        assertNull(txn.getNameDest());
        assertEquals(0.0, txn.getOldbalanceDest(), 0.0);
        assertEquals(0.0, txn.getNewbalanceDest(), 0.0);
        assertEquals(0, txn.getIsFraud());
        assertEquals(0, txn.getIsFlaggedFraud());
    }

    @Test
    public void testParameterizedConstructor() {
        Transaction txn = new Transaction(1, "PAYMENT", 9839.64, "C123",
                170136, 160296.36, "M456", 0, 0, 0, 0);

        assertEquals(1, txn.getStep());
        assertEquals("PAYMENT", txn.getType());
        assertEquals(9839.64, txn.getAmount(), 0.001);
        assertEquals("C123", txn.getNameOrig());
        assertEquals(170136.0, txn.getOldbalanceOrg(), 0.0);
        assertEquals(160296.36, txn.getNewbalanceOrig(), 0.001);
        assertEquals("M456", txn.getNameDest());
        assertEquals(0.0, txn.getOldbalanceDest(), 0.0);
        assertEquals(0.0, txn.getNewbalanceDest(), 0.0);
        assertEquals(0, txn.getIsFraud());
        assertEquals(0, txn.getIsFlaggedFraud());
    }

    @Test
    public void testSettersAndGetters() {
        Transaction txn = new Transaction();

        txn.setStep(2);
        assertEquals(2, txn.getStep());

        txn.setType("TRANSFER");
        assertEquals("TRANSFER", txn.getType());

        txn.setAmount(50000.0);
        assertEquals(50000.0, txn.getAmount(), 0.0);

        txn.setNameOrig("C999");
        assertEquals("C999", txn.getNameOrig());

        txn.setOldbalanceOrg(100000.0);
        assertEquals(100000.0, txn.getOldbalanceOrg(), 0.0);

        txn.setNewbalanceOrig(50000.0);
        assertEquals(50000.0, txn.getNewbalanceOrig(), 0.0);

        txn.setNameDest("C888");
        assertEquals("C888", txn.getNameDest());

        txn.setOldbalanceDest(200000.0);
        assertEquals(200000.0, txn.getOldbalanceDest(), 0.0);

        txn.setNewbalanceDest(250000.0);
        assertEquals(250000.0, txn.getNewbalanceDest(), 0.0);

        txn.setIsFraud(1);
        assertEquals(1, txn.getIsFraud());

        txn.setIsFlaggedFraud(1);
        assertEquals(1, txn.getIsFlaggedFraud());
    }
}
