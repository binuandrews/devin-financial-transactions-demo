package com.fraud.riskscorer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration constants for the fraud risk scoring engine.
 *
 * <p>Rules:
 * <ul>
 *   <li>Transactions above 10,000 are high risk.</li>
 *   <li>CASH_OUT and TRANSFER are higher risk transaction types.</li>
 *   <li>Transactions to new or previously unseen destination accounts are risky.</li>
 *   <li>Rapid sequence of transactions from the same account increases risk.</li>
 *   <li>Fraudulent transactions often involve high amounts followed by cash-out.</li>
 *   <li>Risk levels: LOW (&lt;40), MEDIUM (40-70), HIGH (&gt;70).</li>
 * </ul>
 */
public final class RiskConfig {

    public static final double HIGH_AMOUNT_THRESHOLD = 10_000;
    public static final double AMOUNT_SCALE_CAP = 500_000;

    public static final Set<String> HIGH_RISK_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("CASH_OUT", "TRANSFER")));

    public static final double WEIGHT_HIGH_AMOUNT_FLAG = 30;
    public static final double WEIGHT_AMOUNT_SCALE = 10;
    public static final double WEIGHT_TYPE = 20;
    public static final double WEIGHT_NEW_DEST = 15;
    public static final double WEIGHT_RAPID_TXN = 15;
    public static final double WEIGHT_HIGH_THEN_CASHOUT = 10;

    private RiskConfig() {
        // Utility class - prevent instantiation
    }
}
