package com.example.jasper;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class JrxmlCompilerTest {

    @Test
    public void testCompile() throws Exception {

        File input =
                new File(
                        "sample/simple_test.jrxml"
                );

        File output =
                new File(
                        "target/test-output/simple_test.jasper"
                );

        assertTrue(
                "Sample JRXML does not exist",
                input.exists()
        );

        JrxmlCompiler.compile(
                input,
                output
        );

        assertTrue(
                "JASPER was not generated",
                output.exists()
        );
    }
}