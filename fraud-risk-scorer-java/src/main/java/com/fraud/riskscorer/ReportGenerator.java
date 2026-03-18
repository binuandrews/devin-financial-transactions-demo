package com.fraud.riskscorer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates CSV risk reports and prints summary statistics.
 */
public class ReportGenerator {

    private static final String[] REPORT_HEADERS = {
            "step", "type", "amount", "nameOrig", "oldbalanceOrg",
            "newbalanceOrig", "nameDest", "oldbalanceDest", "newbalanceDest",
            "isFraud", "isFlaggedFraud", "risk_score", "risk_category"
    };

    private static final String SEPARATOR = "============================================================";

    private ReportGenerator() {
        // Utility class - prevent instantiation
    }

    /**
     * Write the scored transactions to a CSV risk report.
     */
    public static void generateReport(List<ScoredTransaction> scoredRows,
                                      String outputPath) throws IOException {
        if (scoredRows.isEmpty()) {
            return;
        }

        try (Writer writer = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(REPORT_HEADERS)
                             .build())) {
            for (ScoredTransaction st : scoredRows) {
                Transaction t = st.getTransaction();
                printer.printRecord(
                        t.getStep(), t.getType(), t.getAmount(),
                        t.getNameOrig(), t.getOldbalanceOrg(), t.getNewbalanceOrig(),
                        t.getNameDest(), t.getOldbalanceDest(), t.getNewbalanceDest(),
                        t.getIsFraud(), t.getIsFlaggedFraud(),
                        st.getRiskScore(), st.getRiskCategory());
            }
        }
    }

    /**
     * Print a concise summary of the risk distribution to {@link System#out}.
     */
    public static void printSummary(List<ScoredTransaction> scoredRows) {
        printSummary(scoredRows, System.out);
    }

    /**
     * Print a concise summary of the risk distribution to the given stream.
     */
    public static void printSummary(List<ScoredTransaction> scoredRows, PrintStream out) {
        int total = scoredRows.size();
        Map<String, Integer> counts = new HashMap<>();
        counts.put("HIGH", 0);
        counts.put("MEDIUM", 0);
        counts.put("LOW", 0);

        for (ScoredTransaction st : scoredRows) {
            counts.merge(st.getRiskCategory(), 1, Integer::sum);
        }

        out.println(SEPARATOR);
        out.println("         TRANSACTION RISK REPORT SUMMARY");
        out.println(SEPARATOR);
        out.printf("  Total transactions analysed : %d%n", total);
        for (String cat : new String[]{"HIGH", "MEDIUM", "LOW"}) {
            double pct = total > 0 ? (counts.get(cat) * 100.0 / total) : 0;
            out.printf("  %-6s risk transactions    : %4d  (%5.1f%%)%n",
                    cat, counts.get(cat), pct);
        }
        out.println(SEPARATOR);
    }
}
