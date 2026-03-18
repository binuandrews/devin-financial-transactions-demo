package com.fraud.riskscorer;

import java.io.IOException;
import java.util.List;

/**
 * Entry point for the Fraud Risk Scoring Engine.
 *
 * <p>Usage: {@code java -cp ... com.fraud.riskscorer.FraudRiskScorerApp [inputCsv] [outputCsv]}
 */
public class FraudRiskScorerApp {

    public static void main(String[] args) throws IOException {
        String inputCsv = args.length > 0 ? args[0] : "data/Example1.csv";
        String outputCsv = args.length > 1 ? args[1] : "data/transaction_risk_report.csv";
        run(inputCsv, outputCsv);
    }

    static void run(String inputCsv, String outputCsv) throws IOException {
        List<ScoredTransaction> scored = FraudRiskScorer.scoreTransactions(inputCsv);
        ReportGenerator.generateReport(scored, outputCsv);
        ReportGenerator.printSummary(scored);
        System.out.println("\nDetailed report written to: " + outputCsv);
    }
}
