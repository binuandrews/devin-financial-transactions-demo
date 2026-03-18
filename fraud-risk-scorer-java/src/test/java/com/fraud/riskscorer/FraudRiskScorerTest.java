package com.fraud.riskscorer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class FraudRiskScorerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // -----------------------------------------------------------------------
    // constructor
    // -----------------------------------------------------------------------

    @Test
    public void testConstructor() {
        // Cover the default constructor
        new FraudRiskScorer();
    }

    // -----------------------------------------------------------------------
    // highAmountFlag
    // -----------------------------------------------------------------------

    @Test
    public void testHighAmountFlag_aboveThreshold() {
        assertEquals(1.0, FraudRiskScorer.highAmountFlag(10_001), 0.0);
    }

    @Test
    public void testHighAmountFlag_atThreshold() {
        assertEquals(0.0, FraudRiskScorer.highAmountFlag(10_000), 0.0);
    }

    @Test
    public void testHighAmountFlag_belowThreshold() {
        assertEquals(0.0, FraudRiskScorer.highAmountFlag(5_000), 0.0);
    }

    // -----------------------------------------------------------------------
    // amountScale
    // -----------------------------------------------------------------------

    @Test
    public void testAmountScale_belowThreshold() {
        assertEquals(0.0, FraudRiskScorer.amountScale(5_000), 0.0);
    }

    @Test
    public void testAmountScale_atThreshold() {
        assertEquals(0.0, FraudRiskScorer.amountScale(10_000), 0.0);
    }

    @Test
    public void testAmountScale_atCap() {
        assertEquals(1.0, FraudRiskScorer.amountScale(500_000), 0.0);
    }

    @Test
    public void testAmountScale_aboveCap() {
        assertEquals(1.0, FraudRiskScorer.amountScale(1_000_000), 0.0);
    }

    @Test
    public void testAmountScale_midRange() {
        // midpoint: (255000 - 10000) / (500000 - 10000) = 245000/490000 = 0.5
        assertEquals(0.5, FraudRiskScorer.amountScale(255_000), 0.001);
    }

    // -----------------------------------------------------------------------
    // typeScore
    // -----------------------------------------------------------------------

    @Test
    public void testTypeScore_cashOut() {
        assertEquals(1.0, FraudRiskScorer.typeScore("CASH_OUT"), 0.0);
    }

    @Test
    public void testTypeScore_transfer() {
        assertEquals(1.0, FraudRiskScorer.typeScore("TRANSFER"), 0.0);
    }

    @Test
    public void testTypeScore_payment() {
        assertEquals(0.0, FraudRiskScorer.typeScore("PAYMENT"), 0.0);
    }

    @Test
    public void testTypeScore_debit() {
        assertEquals(0.0, FraudRiskScorer.typeScore("DEBIT"), 0.0);
    }

    // -----------------------------------------------------------------------
    // newDestinationScore
    // -----------------------------------------------------------------------

    @Test
    public void testNewDestinationScore_unseenDest() {
        Set<String> seen = new HashSet<>();
        assertEquals(1.0, FraudRiskScorer.newDestinationScore("M456", seen), 0.0);
    }

    @Test
    public void testNewDestinationScore_seenDest() {
        Set<String> seen = new HashSet<>(Collections.singletonList("M456"));
        assertEquals(0.0, FraudRiskScorer.newDestinationScore("M456", seen), 0.0);
    }

    // -----------------------------------------------------------------------
    // rapidTransactionScore
    // -----------------------------------------------------------------------

    @Test
    public void testRapidTransactionScore_noHistory() {
        Map<String, Integer> history = new HashMap<>();
        assertEquals(0.0, FraudRiskScorer.rapidTransactionScore("C123", 1, history), 0.0);
    }

    @Test
    public void testRapidTransactionScore_oneTransaction() {
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 1);
        assertEquals(0.0, FraudRiskScorer.rapidTransactionScore("C123", 1, history), 0.0);
    }

    @Test
    public void testRapidTransactionScore_twoTransactions() {
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 2);
        assertEquals(0.3, FraudRiskScorer.rapidTransactionScore("C123", 1, history), 0.0);
    }

    @Test
    public void testRapidTransactionScore_threeTransactions() {
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 3);
        assertEquals(0.6, FraudRiskScorer.rapidTransactionScore("C123", 1, history), 0.0);
    }

    @Test
    public void testRapidTransactionScore_fourPlusTransactions() {
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 5);
        assertEquals(1.0, FraudRiskScorer.rapidTransactionScore("C123", 1, history), 0.0);
    }

    // -----------------------------------------------------------------------
    // highAmountThenCashoutScore
    // -----------------------------------------------------------------------

    @Test
    public void testHighAmountThenCashout_matchingPattern() {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("C123", true);
        assertEquals(1.0, FraudRiskScorer.highAmountThenCashoutScore("CASH_OUT", "C123", flags), 0.0);
    }

    @Test
    public void testHighAmountThenCashout_cashOutNoFlag() {
        Map<String, Boolean> flags = new HashMap<>();
        assertEquals(0.0, FraudRiskScorer.highAmountThenCashoutScore("CASH_OUT", "C123", flags), 0.0);
    }

    @Test
    public void testHighAmountThenCashout_notCashOut() {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("C123", true);
        assertEquals(0.0, FraudRiskScorer.highAmountThenCashoutScore("TRANSFER", "C123", flags), 0.0);
    }

    @Test
    public void testHighAmountThenCashout_cashOutFlagFalse() {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("C123", false);
        assertEquals(0.0, FraudRiskScorer.highAmountThenCashoutScore("CASH_OUT", "C123", flags), 0.0);
    }

    // -----------------------------------------------------------------------
    // originHistoryKey
    // -----------------------------------------------------------------------

    @Test
    public void testOriginHistoryKey() {
        assertEquals("C123|1", FraudRiskScorer.originHistoryKey("C123", 1));
        assertEquals("C999|42", FraudRiskScorer.originHistoryKey("C999", 42));
    }

    // -----------------------------------------------------------------------
    // computeRiskScore
    // -----------------------------------------------------------------------

    @Test
    public void testComputeRiskScore_lowRiskPayment() {
        Set<String> seen = new HashSet<>(Collections.singletonList("M456"));
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 1);
        Map<String, Boolean> flags = new HashMap<>();

        // PAYMENT, small amount, seen dest, 1 txn, no flag
        // score = 0 + 0 + 0 + 0 + 0 + 0 = 0
        int score = FraudRiskScorer.computeRiskScore(5000, "PAYMENT", "C123", "M456",
                1, seen, history, flags);
        assertEquals(0, score);
    }

    @Test
    public void testComputeRiskScore_highRiskCashOut() {
        Set<String> seen = new HashSet<>();
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 4);
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("C123", true);

        // CASH_OUT, amount=50000 > 10000, unseen dest, 4 txns, has flag
        // highAmountFlag = 30 * 1.0 = 30
        // amountScale = 10 * (50000-10000)/(500000-10000) = 10 * 0.08163 ≈ 0.816
        // typeScore = 20 * 1.0 = 20
        // newDest = 15 * 1.0 = 15
        // rapidTxn = 15 * 1.0 = 15
        // highThenCashout = 10 * 1.0 = 10
        // total ≈ 30 + 0.816 + 20 + 15 + 15 + 10 = 90.816 → 91
        int score = FraudRiskScorer.computeRiskScore(50000, "CASH_OUT", "C123", "C999",
                1, seen, history, flags);
        assertEquals(91, score);
    }

    @Test
    public void testComputeRiskScore_mediumRisk() {
        Set<String> seen = new HashSet<>();
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 1);
        Map<String, Boolean> flags = new HashMap<>();

        // TRANSFER, amount=15000 > 10000, unseen dest, 1 txn, no flag
        // highAmountFlag = 30 * 1.0 = 30
        // amountScale = 10 * (15000-10000)/490000 = 10 * 0.01020 ≈ 0.102
        // typeScore = 20 * 1.0 = 20
        // newDest = 15 * 1.0 = 15
        // rapidTxn = 0
        // highThenCashout = 0
        // total ≈ 30 + 0.102 + 20 + 15 = 65.102 → 65
        int score = FraudRiskScorer.computeRiskScore(15000, "TRANSFER", "C123", "C999",
                1, seen, history, flags);
        assertEquals(65, score);
    }

    @Test
    public void testComputeRiskScore_clampedToMax100() {
        // This scenario shouldn't naturally exceed 100 given the weights sum to 100,
        // but verify the clamp works
        Set<String> seen = new HashSet<>();
        Map<String, Integer> history = new HashMap<>();
        history.put(FraudRiskScorer.originHistoryKey("C123", 1), 5);
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("C123", true);

        // Max possible: 30 + 10 + 20 + 15 + 15 + 10 = 100
        int score = FraudRiskScorer.computeRiskScore(600000, "CASH_OUT", "C123", "NEWDEST",
                1, seen, history, flags);
        assertEquals(100, score);
    }

    @Test
    public void testComputeRiskScore_clampedToMin0() {
        // All zero factors → 0
        Set<String> seen = new HashSet<>(Collections.singletonList("M456"));
        Map<String, Integer> history = new HashMap<>();
        Map<String, Boolean> flags = new HashMap<>();

        int score = FraudRiskScorer.computeRiskScore(1000, "PAYMENT", "C123", "M456",
                1, seen, history, flags);
        assertEquals(0, score);
    }

    // -----------------------------------------------------------------------
    // classifyRisk
    // -----------------------------------------------------------------------

    @Test
    public void testClassifyRisk_low() {
        assertEquals("LOW", FraudRiskScorer.classifyRisk(0));
        assertEquals("LOW", FraudRiskScorer.classifyRisk(39));
    }

    @Test
    public void testClassifyRisk_medium() {
        assertEquals("MEDIUM", FraudRiskScorer.classifyRisk(40));
        assertEquals("MEDIUM", FraudRiskScorer.classifyRisk(55));
        assertEquals("MEDIUM", FraudRiskScorer.classifyRisk(70));
    }

    @Test
    public void testClassifyRisk_high() {
        assertEquals("HIGH", FraudRiskScorer.classifyRisk(71));
        assertEquals("HIGH", FraudRiskScorer.classifyRisk(100));
    }

    // -----------------------------------------------------------------------
    // preprocess
    // -----------------------------------------------------------------------

    @Test
    public void testPreprocess_originHistoryCounting() {
        List<Transaction> txns = Arrays.asList(
                new Transaction(1, "PAYMENT", 5000, "C123", 0, 0, "M456", 0, 0, 0, 0),
                new Transaction(1, "PAYMENT", 3000, "C123", 0, 0, "M789", 0, 0, 0, 0),
                new Transaction(2, "PAYMENT", 1000, "C123", 0, 0, "M111", 0, 0, 0, 0)
        );

        FraudRiskScorer.PreprocessResult result = FraudRiskScorer.preprocess(txns);
        assertEquals(Integer.valueOf(2), result.getOriginHistory().get("C123|1"));
        assertEquals(Integer.valueOf(1), result.getOriginHistory().get("C123|2"));
    }

    @Test
    public void testPreprocess_highAmountFlag() {
        List<Transaction> txns = Arrays.asList(
                new Transaction(1, "TRANSFER", 50000, "C123", 0, 0, "C456", 0, 0, 0, 0),
                new Transaction(1, "CASH_OUT", 50000, "C789", 0, 0, "C111", 0, 0, 0, 0),
                new Transaction(1, "PAYMENT", 5000, "C222", 0, 0, "M333", 0, 0, 0, 0)
        );

        FraudRiskScorer.PreprocessResult result = FraudRiskScorer.preprocess(txns);
        // C123: TRANSFER with high amount → flagged
        assertTrue(result.getOriginHighAmountFlag().containsKey("C123"));
        assertTrue(result.getOriginHighAmountFlag().get("C123"));
        // C789: CASH_OUT with high amount → NOT flagged (CASH_OUT excluded)
        assertFalse(result.getOriginHighAmountFlag().containsKey("C789"));
        // C222: low amount → NOT flagged
        assertFalse(result.getOriginHighAmountFlag().containsKey("C222"));
    }

    @Test
    public void testPreprocess_emptyList() {
        FraudRiskScorer.PreprocessResult result = FraudRiskScorer.preprocess(
                Collections.<Transaction>emptyList());
        assertTrue(result.getOriginHistory().isEmpty());
        assertTrue(result.getOriginHighAmountFlag().isEmpty());
    }

    // -----------------------------------------------------------------------
    // scoreTransactions (List)
    // -----------------------------------------------------------------------

    @Test
    public void testScoreTransactions_listInput() {
        List<Transaction> txns = Arrays.asList(
                new Transaction(1, "PAYMENT", 5000, "C123", 10000, 5000, "M456", 0, 0, 0, 0),
                new Transaction(1, "TRANSFER", 50000, "C789", 60000, 10000, "C456", 0, 0, 0, 0),
                new Transaction(1, "CASH_OUT", 20000, "C789", 10000, 0, "C111", 50000, 70000, 0, 0)
        );

        List<ScoredTransaction> result = FraudRiskScorer.scoreTransactions(txns);
        assertEquals(3, result.size());

        // First transaction: PAYMENT, 5000, new dest → score = 15 (new dest only)
        assertEquals("LOW", result.get(0).getRiskCategory());

        // Second: TRANSFER, 50000, new dest → high amount + type + new dest
        assertTrue(result.get(1).getRiskScore() > 40);

        // Third: CASH_OUT, 20000, new dest, C789 had high amount transfer →
        //   high amount + type + new dest + high-then-cashout
        assertTrue(result.get(2).getRiskScore() > 40);
    }

    @Test
    public void testScoreTransactions_seenDestinationsTracked() {
        Transaction txn1 = new Transaction(1, "PAYMENT", 5000, "C123", 0, 0, "M456", 0, 0, 0, 0);
        Transaction txn2 = new Transaction(1, "PAYMENT", 5000, "C789", 0, 0, "M456", 0, 0, 0, 0);

        List<ScoredTransaction> result = FraudRiskScorer.scoreTransactions(Arrays.asList(txn1, txn2));
        // First sees M456 as new (score includes new dest weight)
        // Second sees M456 as seen (score does not include new dest weight)
        assertTrue(result.get(0).getRiskScore() > result.get(1).getRiskScore());
    }

    @Test
    public void testScoreTransactions_emptyList() {
        List<ScoredTransaction> result = FraudRiskScorer.scoreTransactions(
                Collections.<Transaction>emptyList());
        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // readTransactions + scoreTransactions (file)
    // -----------------------------------------------------------------------

    @Test
    public void testReadTransactions() throws IOException {
        File csvFile = tempFolder.newFile("test.csv");
        try (Writer writer = new FileWriter(csvFile)) {
            writer.write("step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
                    + "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n");
            writer.write("1,PAYMENT,9839.64,C1231006815,170136,160296.36,M1979787155,0,0,0,0\n");
            writer.write("1,CASH_OUT,181,C840083671,181,0,C38997010,21182,0,1,0\n");
        }

        List<Transaction> txns = FraudRiskScorer.readTransactions(csvFile.getAbsolutePath());
        assertEquals(2, txns.size());

        Transaction first = txns.get(0);
        assertEquals(1, first.getStep());
        assertEquals("PAYMENT", first.getType());
        assertEquals(9839.64, first.getAmount(), 0.01);
        assertEquals("C1231006815", first.getNameOrig());
        assertEquals(170136.0, first.getOldbalanceOrg(), 0.01);
        assertEquals(160296.36, first.getNewbalanceOrig(), 0.01);
        assertEquals("M1979787155", first.getNameDest());
        assertEquals(0.0, first.getOldbalanceDest(), 0.0);
        assertEquals(0.0, first.getNewbalanceDest(), 0.0);
        assertEquals(0, first.getIsFraud());
        assertEquals(0, first.getIsFlaggedFraud());

        Transaction second = txns.get(1);
        assertEquals("CASH_OUT", second.getType());
        assertEquals(181.0, second.getAmount(), 0.01);
        assertEquals(1, second.getIsFraud());
    }

    @Test
    public void testScoreTransactions_fileInput() throws IOException {
        File csvFile = tempFolder.newFile("input.csv");
        try (Writer writer = new FileWriter(csvFile)) {
            writer.write("step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
                    + "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n");
            writer.write("1,PAYMENT,9839.64,C123,170136,160296.36,M456,0,0,0,0\n");
            writer.write("1,TRANSFER,50000,C789,60000,10000,C456,0,0,0,0\n");
        }

        List<ScoredTransaction> result = FraudRiskScorer.scoreTransactions(
                csvFile.getAbsolutePath());
        assertEquals(2, result.size());
        assertNotNull(result.get(0).getRiskCategory());
        assertNotNull(result.get(1).getRiskCategory());
    }

    @Test(expected = IOException.class)
    public void testScoreTransactions_fileNotFound() throws IOException {
        FraudRiskScorer.scoreTransactions("/nonexistent/path.csv");
    }

    @Test(expected = IOException.class)
    public void testReadTransactions_fileNotFound() throws IOException {
        FraudRiskScorer.readTransactions("/nonexistent/path.csv");
    }
}
