package com.fraud.riskscorer;

/**
 * A transaction enriched with a computed risk score and risk category.
 */
public class ScoredTransaction {

    private final Transaction transaction;
    private final int riskScore;
    private final String riskCategory;

    public ScoredTransaction(Transaction transaction, int riskScore, String riskCategory) {
        this.transaction = transaction;
        this.riskScore = riskScore;
        this.riskCategory = riskCategory;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getRiskCategory() {
        return riskCategory;
    }
}
