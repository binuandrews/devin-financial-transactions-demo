package com.fraud.riskscorer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ReportGeneratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // -----------------------------------------------------------------------
    // generateReport
    // -----------------------------------------------------------------------

    @Test
    public void testGenerateReport_withRows() throws IOException {
        File outputFile = tempFolder.newFile("report.csv");
        Transaction txn1 = new Transaction(1, "PAYMENT", 5000, "C123",
                10000, 5000, "M456", 0, 0, 0, 0);
        Transaction txn2 = new Transaction(1, "CASH_OUT", 50000, "C789",
                60000, 10000, "C111", 50000, 100000, 1, 0);

        List<ScoredTransaction> scored = Arrays.asList(
                new ScoredTransaction(txn1, 15, "LOW"),
                new ScoredTransaction(txn2, 85, "HIGH")
        );

        ReportGenerator.generateReport(scored, outputFile.getAbsolutePath());

        String content = new String(Files.readAllBytes(outputFile.toPath()));
        // Verify header
        assertTrue(content.contains("step"));
        assertTrue(content.contains("risk_score"));
        assertTrue(content.contains("risk_category"));
        // Verify data rows
        assertTrue(content.contains("PAYMENT"));
        assertTrue(content.contains("CASH_OUT"));
        assertTrue(content.contains("LOW"));
        assertTrue(content.contains("HIGH"));
    }

    @Test
    public void testGenerateReport_emptyRows() throws IOException {
        File outputFile = tempFolder.newFile("empty_report.csv");
        long sizeBefore = outputFile.length();

        ReportGenerator.generateReport(Collections.<ScoredTransaction>emptyList(),
                outputFile.getAbsolutePath());

        // File should not have been written to (empty list returns early)
        assertEquals(sizeBefore, outputFile.length());
    }

    // -----------------------------------------------------------------------
    // printSummary
    // -----------------------------------------------------------------------

    @Test
    public void testPrintSummary_withRows() {
        Transaction txn1 = new Transaction(1, "PAYMENT", 5000, "C1", 0, 0, "M1", 0, 0, 0, 0);
        Transaction txn2 = new Transaction(1, "TRANSFER", 50000, "C2", 0, 0, "C3", 0, 0, 0, 0);
        Transaction txn3 = new Transaction(1, "CASH_OUT", 80000, "C4", 0, 0, "C5", 0, 0, 0, 0);

        List<ScoredTransaction> scored = Arrays.asList(
                new ScoredTransaction(txn1, 15, "LOW"),
                new ScoredTransaction(txn2, 55, "MEDIUM"),
                new ScoredTransaction(txn3, 85, "HIGH")
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ReportGenerator.printSummary(scored, ps);

        String output = baos.toString();
        assertTrue(output.contains("TRANSACTION RISK REPORT SUMMARY"));
        assertTrue(output.contains("Total transactions analysed : 3"));
        assertTrue(output.contains("HIGH"));
        assertTrue(output.contains("MEDIUM"));
        assertTrue(output.contains("LOW"));
        assertTrue(output.contains("33.3%"));
    }

    @Test
    public void testPrintSummary_emptyRows() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ReportGenerator.printSummary(Collections.<ScoredTransaction>emptyList(), ps);

        String output = baos.toString();
        assertTrue(output.contains("Total transactions analysed : 0"));
        assertTrue(output.contains("0.0%"));
    }

    @Test
    public void testPrintSummary_defaultStream() {
        // Just verify it doesn't throw when using System.out
        Transaction txn = new Transaction(1, "PAYMENT", 100, "C1", 0, 0, "M1", 0, 0, 0, 0);
        List<ScoredTransaction> scored = Collections.singletonList(
                new ScoredTransaction(txn, 10, "LOW"));

        // Redirect System.out to capture output
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            ReportGenerator.printSummary(scored);
            String output = baos.toString();
            assertTrue(output.contains("TRANSACTION RISK REPORT SUMMARY"));
        } finally {
            System.setOut(originalOut);
        }
    }

    // -----------------------------------------------------------------------
    // Private constructor
    // -----------------------------------------------------------------------

    @Test
    public void testPrivateConstructor() throws Exception {
        Constructor<ReportGenerator> constructor = ReportGenerator.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            fail("Private constructor should not throw: " + e.getCause());
        }
    }
}
