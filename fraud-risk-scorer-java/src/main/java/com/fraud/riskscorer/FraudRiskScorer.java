package com.fraud.riskscorer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fraud Risk Scoring Engine.
 *
 * <p>Assigns fraud risk scores (0-100) to financial transactions based on
 * configurable rules and generates a transaction-level risk report.
 */
public class FraudRiskScorer {

    /**
     * Binary flag: 1.0 if amount exceeds the high-amount threshold, else 0.0.
     */
    static double highAmountFlag(double amount) {
        return amount > RiskConfig.HIGH_AMOUNT_THRESHOLD ? 1.0 : 0.0;
    }

    /**
     * Graduated score for very large amounts (0-1 scale).
     * <ul>
     *   <li>0 for amount &lt;= 10,000</li>
     *   <li>1.0 for amount &gt;= 500,000 (cap)</li>
     * </ul>
     */
    static double amountScale(double amount) {
        if (amount <= RiskConfig.HIGH_AMOUNT_THRESHOLD) {
            return 0.0;
        }
        if (amount >= RiskConfig.AMOUNT_SCALE_CAP) {
            return 1.0;
        }
        return (amount - RiskConfig.HIGH_AMOUNT_THRESHOLD)
                / (RiskConfig.AMOUNT_SCALE_CAP - RiskConfig.HIGH_AMOUNT_THRESHOLD);
    }

    /**
     * Score based on transaction type. 1.0 for risky types, 0.0 otherwise.
     */
    static double typeScore(String txnType) {
        return RiskConfig.HIGH_RISK_TYPES.contains(txnType) ? 1.0 : 0.0;
    }

    /**
     * Score 1.0 if the destination has never been seen before, else 0.0.
     */
    static double newDestinationScore(String nameDest, Set<String> seenDestinations) {
        return seenDestinations.contains(nameDest) ? 0.0 : 1.0;
    }

    /**
     * Score based on how many transactions the same origin has made in the
     * current time-step. More transactions in the same step means higher score.
     *
     * <p>Scale: 1 txn = 0.0, 2 txns = 0.3, 3 txns = 0.6, 4+ txns = 1.0
     */
    static double rapidTransactionScore(String nameOrig, int step,
                                        Map<String, Integer> originHistory) {
        int count = originHistory.getOrDefault(originHistoryKey(nameOrig, step), 0);
        if (count <= 1) {
            return 0.0;
        }
        if (count == 2) {
            return 0.3;
        }
        if (count == 3) {
            return 0.6;
        }
        return 1.0;
    }

    /**
     * Detects the pattern: a prior high-amount transaction from the same
     * origin, followed by a CASH_OUT. Returns 1.0 when the pattern matches.
     */
    static double highAmountThenCashoutScore(String txnType, String nameOrig,
                                             Map<String, Boolean> originHighAmountFlag) {
        if ("CASH_OUT".equals(txnType) && Boolean.TRUE.equals(originHighAmountFlag.get(nameOrig))) {
            return 1.0;
        }
        return 0.0;
    }

    /**
     * Compute the composite risk score (0-100) for a single transaction.
     */
    public static int computeRiskScore(double amount, String txnType,
                                       String nameOrig, String nameDest, int step,
                                       Set<String> seenDestinations,
                                       Map<String, Integer> originHistory,
                                       Map<String, Boolean> originHighAmountFlag) {
        double score = 0.0;
        score += RiskConfig.WEIGHT_HIGH_AMOUNT_FLAG * highAmountFlag(amount);
        score += RiskConfig.WEIGHT_AMOUNT_SCALE * amountScale(amount);
        score += RiskConfig.WEIGHT_TYPE * typeScore(txnType);
        score += RiskConfig.WEIGHT_NEW_DEST * newDestinationScore(nameDest, seenDestinations);
        score += RiskConfig.WEIGHT_RAPID_TXN * rapidTransactionScore(nameOrig, step, originHistory);
        score += RiskConfig.WEIGHT_HIGH_THEN_CASHOUT * highAmountThenCashoutScore(
                txnType, nameOrig, originHighAmountFlag);
        // Clamp to 0-100
        return (int) Math.min(Math.max(Math.round(score), 0L), 100L);
    }

    /**
     * Map a numeric score to a risk category.
     */
    public static String classifyRisk(int score) {
        if (score < 40) {
            return "LOW";
        }
        if (score <= 70) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    /**
     * Build a composite key for the origin-history map.
     */
    static String originHistoryKey(String nameOrig, int step) {
        return nameOrig + "|" + step;
    }

    /**
     * Result of the pre-processing pass over all transactions.
     */
    static class PreprocessResult {
        private final Map<String, Integer> originHistory;
        private final Map<String, Boolean> originHighAmountFlag;

        PreprocessResult(Map<String, Integer> originHistory,
                         Map<String, Boolean> originHighAmountFlag) {
            this.originHistory = originHistory;
            this.originHighAmountFlag = originHighAmountFlag;
        }

        Map<String, Integer> getOriginHistory() {
            return originHistory;
        }

        Map<String, Boolean> getOriginHighAmountFlag() {
            return originHighAmountFlag;
        }
    }

    /**
     * Scan the full transaction list to build look-ahead structures:
     * <ol>
     *   <li>origin_history: how many transactions each origin made per time-step.</li>
     *   <li>origin_high_amount_flag: whether the origin ever sent an amount &gt;
     *       HIGH_AMOUNT_THRESHOLD before any CASH_OUT from that origin.</li>
     * </ol>
     */
    static PreprocessResult preprocess(List<Transaction> rows) {
        Map<String, Integer> originHistory = new HashMap<>();
        Map<String, Boolean> originHighAmountFlag = new HashMap<>();

        for (Transaction row : rows) {
            int step = row.getStep();
            String nameOrig = row.getNameOrig();
            double amount = row.getAmount();
            String txnType = row.getType();

            String key = originHistoryKey(nameOrig, step);
            originHistory.merge(key, 1, Integer::sum);

            if (amount > RiskConfig.HIGH_AMOUNT_THRESHOLD && !"CASH_OUT".equals(txnType)) {
                originHighAmountFlag.put(nameOrig, true);
            }
        }

        return new PreprocessResult(originHistory, originHighAmountFlag);
    }

    /**
     * Read transactions from the given CSV file path.
     */
    public static List<Transaction> readTransactions(String inputPath) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        try (Reader reader = new FileReader(inputPath);
             CSVParser parser = new CSVParser(reader,
                     CSVFormat.DEFAULT.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .build())) {
            for (CSVRecord record : parser) {
                Transaction txn = new Transaction();
                txn.setStep(Integer.parseInt(record.get("step")));
                txn.setType(record.get("type"));
                txn.setAmount(Double.parseDouble(record.get("amount")));
                txn.setNameOrig(record.get("nameOrig"));
                txn.setOldbalanceOrg(Double.parseDouble(record.get("oldbalanceOrg")));
                txn.setNewbalanceOrig(Double.parseDouble(record.get("newbalanceOrig")));
                txn.setNameDest(record.get("nameDest"));
                txn.setOldbalanceDest(Double.parseDouble(record.get("oldbalanceDest")));
                txn.setNewbalanceDest(Double.parseDouble(record.get("newbalanceDest")));
                txn.setIsFraud(Integer.parseInt(record.get("isFraud")));
                txn.setIsFlaggedFraud(Integer.parseInt(record.get("isFlaggedFraud")));
                transactions.add(txn);
            }
        }
        return transactions;
    }

    /**
     * Score a list of transactions and return scored results.
     */
    public static List<ScoredTransaction> scoreTransactions(List<Transaction> transactions) {
        PreprocessResult preprocessResult = preprocess(transactions);
        Set<String> seenDestinations = new HashSet<>();
        List<ScoredTransaction> scoredTransactions = new ArrayList<>();

        for (Transaction txn : transactions) {
            int riskScore = computeRiskScore(
                    txn.getAmount(), txn.getType(), txn.getNameOrig(),
                    txn.getNameDest(), txn.getStep(), seenDestinations,
                    preprocessResult.getOriginHistory(),
                    preprocessResult.getOriginHighAmountFlag());
            String riskCategory = classifyRisk(riskScore);
            scoredTransactions.add(new ScoredTransaction(txn, riskScore, riskCategory));
            seenDestinations.add(txn.getNameDest());
        }

        return scoredTransactions;
    }

    /**
     * Read transactions from a CSV file, score each one, and return the
     * enriched list with risk_score and risk_category.
     */
    public static List<ScoredTransaction> scoreTransactions(String inputPath) throws IOException {
        List<Transaction> transactions = readTransactions(inputPath);
        return scoreTransactions(transactions);
    }
}
