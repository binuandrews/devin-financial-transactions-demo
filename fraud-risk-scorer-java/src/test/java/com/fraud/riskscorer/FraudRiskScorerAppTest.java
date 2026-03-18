package com.fraud.riskscorer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import static org.junit.Assert.*;

public class FraudRiskScorerAppTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String CSV_HEADER =
            "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,"
                    + "nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n";

    private File createTestCsv() throws IOException {
        File csvFile = tempFolder.newFile("input.csv");
        try (Writer writer = new FileWriter(csvFile)) {
            writer.write(CSV_HEADER);
            writer.write("1,PAYMENT,9839.64,C123,170136,160296.36,M456,0,0,0,0\n");
            writer.write("1,TRANSFER,50000,C789,60000,10000,C456,0,0,0,0\n");
        }
        return csvFile;
    }

    @Test
    public void testRun() throws IOException {
        File inputFile = createTestCsv();
        File outputFile = tempFolder.newFile("output.csv");

        // Capture stdout
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            FraudRiskScorerApp.run(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
            String output = baos.toString();
            assertTrue(output.contains("TRANSACTION RISK REPORT SUMMARY"));
            assertTrue(output.contains("Detailed report written to:"));
        } finally {
            System.setOut(originalOut);
        }

        // Verify output file was created with content
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
    }

    @Test
    public void testMain_withTwoArgs() throws IOException {
        File inputFile = createTestCsv();
        File outputFile = tempFolder.newFile("output.csv");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            FraudRiskScorerApp.main(new String[]{
                    inputFile.getAbsolutePath(),
                    outputFile.getAbsolutePath()
            });
            String output = baos.toString();
            assertTrue(output.contains("TRANSACTION RISK REPORT SUMMARY"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testConstructor() {
        // Cover the default constructor
        new FraudRiskScorerApp();
    }

    @Test
    public void testMain_withOneArg() throws IOException {
        File inputFile = createTestCsv();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            // One arg: exercises args.length > 0 = true, args.length > 1 = false
            FraudRiskScorerApp.main(new String[]{inputFile.getAbsolutePath()});
        } catch (IOException e) {
            // Expected if the default output directory doesn't exist
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testMain_noArgs() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            // No args: exercises both args.length > 0 = false and args.length > 1 = false
            FraudRiskScorerApp.main(new String[]{});
        } catch (IOException e) {
            // Expected because default input file doesn't exist
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test(expected = IOException.class)
    public void testRun_invalidInput() throws IOException {
        FraudRiskScorerApp.run("/nonexistent/file.csv", "/tmp/output.csv");
    }
}
