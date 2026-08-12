package com.example.jasper;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * JasperReports 6.20.0 command line utility.
 *
 * 支持：
 *
 * 1. JRXML -> JASPER
 *
 *    java -jar jrxml-compiler-6.20.0.jar test.jrxml
 *
 *
 * 2. JRXML -> 指定 JASPER
 *
 *    java -jar jrxml-compiler-6.20.0.jar test.jrxml test.jasper
 *
 *
 * 3. JASPER -> PDF
 *
 *    java -jar jrxml-compiler-6.20.0.jar --preview test.jasper
 *
 *
 * 4. JASPER -> 指定 PDF
 *
 *    java -jar jrxml-compiler-6.20.0.jar \
 *        --preview test.jasper test.pdf
 *
 *
 * 注意：
 *
 * --preview 使用 JREmptyDataSource。
 *
 * 因此适合：
 *
 * - 静态报表
 * - 不依赖数据库数据的报表
 * - 布局预览
 *
 * 如果 JRXML/JASPER 依赖数据库、Bean、Map 等数据，
 * 则需要由业务程序提供真实 JRDataSource。
 */
public final class JrxmlCompiler {

    private JrxmlCompiler() {
    }


    /**
     * 程序入口。
     */
    public static void main(String[] args) {

        // Web 服务模式：阻塞运行，不返回
        if (args != null
                && args.length > 0
                && ("--server".equalsIgnoreCase(args[0])
                || "-s".equalsIgnoreCase(args[0]))) {

            try {

                String[] rest = new String[args.length - 1];

                System.arraycopy(
                        args, 1, rest, 0, rest.length);

                ReportWebServer.start(rest);

                return;

            } catch (Exception e) {

                System.err.println(
                        "Web server failed to start: "
                                + e.getMessage()
                );

                e.printStackTrace(System.err);

                System.exit(1);
            }
        }

        int exitCode = run(args);

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }


    /**
     * 执行主逻辑。
     *
     * 注意：
     *
     * 这里没有在业务逻辑中直接 System.exit。
     *
     * 这样 Maven/JUnit 测试时不会因为 System.exit
     * 导致 Surefire JVM 被杀掉。
     */
    static int run(String[] args) {

        if (args == null || args.length == 0) {

            printUsage();

            return 1;
        }


        try {

            /*
             * =========================================
             * JASPER -> PDF
             * =========================================
             */

            if ("--preview".equalsIgnoreCase(args[0])
                    || "-p".equalsIgnoreCase(args[0])) {

                if (args.length < 2 || args.length > 3) {

                    printUsage();

                    return 1;
                }


                File jasper =
                        new File(args[1]).getAbsoluteFile();


                File pdf;

                if (args.length == 3) {

                    pdf =
                            new File(args[2]).getAbsoluteFile();

                } else {

                    pdf =
                            defaultPdf(jasper);
                }


                preview(jasper, pdf);

                return 0;
            }


            /*
             * =========================================
             * JRXML -> JASPER
             * =========================================
             */

            if (args.length > 2) {

                printUsage();

                return 1;
            }


            File input =
                    new File(args[0]).getAbsoluteFile();


            if (!input.exists()) {

                System.err.println(
                        "JRXML file does not exist: "
                                + input.getAbsolutePath()
                );

                return 2;
            }


            if (!input.isFile()) {

                System.err.println(
                        "Input path is not a file: "
                                + input.getAbsolutePath()
                );

                return 3;
            }


            File output;

            if (args.length == 2) {

                output =
                        new File(args[1]).getAbsoluteFile();

            } else {

                output =
                        defaultJasper(input);
            }


            compile(input, output);

            return 0;

        } catch (Exception e) {

            System.err.println();

            System.err.println(
                    "Operation failed."
            );

            System.err.println(
                    e.getClass().getName()
                            + ": "
                            + e.getMessage()
            );

            e.printStackTrace(System.err);

            return 10;
        }
    }


    /**
     * JRXML -> JASPER
     */
    public static void compile(
            File input,
            File output) throws Exception {


        if (input == null) {

            throw new IllegalArgumentException(
                    "Input file cannot be null."
            );
        }


        if (!input.exists()) {

            throw new IllegalArgumentException(
                    "JRXML file does not exist: "
                            + input.getAbsolutePath()
            );
        }


        if (!input.isFile()) {

            throw new IllegalArgumentException(
                    "Input path is not a file: "
                            + input.getAbsolutePath()
            );
        }


        if (output == null) {

            throw new IllegalArgumentException(
                    "Output file cannot be null."
            );
        }


        createParent(output);


        System.out.println(
                "======================================"
        );

        System.out.println(
                "JRXML -> JASPER"
        );

        System.out.println(
                "JasperReports 6.20.0"
        );

        System.out.println(
                "======================================"
        );


        System.out.println(
                "Input : "
                        + input.getAbsolutePath()
        );

        System.out.println(
                "Output: "
                        + output.getAbsolutePath()
        );

        System.out.println(
                "Compiling..."
        );


        long start =
                System.currentTimeMillis();


        JasperCompileManager.compileReportToFile(
                input.getAbsolutePath(),
                output.getAbsolutePath()
        );


        long cost =
                System.currentTimeMillis()
                        - start;


        System.out.println(
                "Compile successful."
        );

        System.out.println(
                "JASPER: "
                        + output.getAbsolutePath()
        );

        System.out.println(
                "Time  : "
                        + cost
                        + " ms"
        );


        System.out.println(
                "======================================"
        );
    }


    /**
     * JASPER -> PDF。
     *
     * 使用 JREmptyDataSource。
     *
     * 适合静态报表预览。
     */
    public static void preview(
            File jasper,
            File pdf) throws Exception {

        preview(jasper, pdf, new HashMap());
    }


    /**
     * JASPER -> PDF（带报表参数）。
     *
     * 参数类型请与 JRXML 中声明的 {@code <parameter>} 一致。
     *
     * 数据源仍使用 JREmptyDataSource，
     * 适合静态报表 / 布局预览。
     */
    public static void preview(
            File jasper,
            File pdf,
            Map parameters) throws Exception {


        if (jasper == null) {

            throw new IllegalArgumentException(
                    "JASPER file cannot be null."
            );
        }


        if (!jasper.exists()) {

            throw new IllegalArgumentException(
                    "JASPER file does not exist: "
                            + jasper.getAbsolutePath()
            );
        }


        if (!jasper.isFile()) {

            throw new IllegalArgumentException(
                    "JASPER path is not a file: "
                            + jasper.getAbsolutePath()
            );
        }


        if (pdf == null) {

            throw new IllegalArgumentException(
                    "PDF file cannot be null."
            );
        }


        createParent(pdf);


        System.out.println(
                "======================================"
        );

        System.out.println(
                "JASPER -> PDF"
        );

        System.out.println(
                "JasperReports 6.20.0"
        );

        System.out.println(
                "======================================"
        );


        System.out.println(
                "Input : "
                        + jasper.getAbsolutePath()
        );

        System.out.println(
                "Output: "
                        + pdf.getAbsolutePath()
        );

        System.out.println(
                "Filling report..."
        );


        long start =
                System.currentTimeMillis();


        /*
         * 使用空数据源。
         *
         * 主要用于静态报表。
         */
        JasperPrint jasperPrint =
                JasperFillManager.fillReport(
                        jasper.getAbsolutePath(),
                        parameters,
                        new JREmptyDataSource()
                );


        /*
         * 导出 PDF。
         */
        JasperExportManager.exportReportToPdfFile(
                jasperPrint,
                pdf.getAbsolutePath()
        );


        long cost =
                System.currentTimeMillis()
                        - start;


        System.out.println(
                "PDF generated successfully."
        );

        System.out.println(
                "PDF   : "
                        + pdf.getAbsolutePath()
        );

        System.out.println(
                "Pages : "
                        + jasperPrint
                        .getPages()
                        .size()
        );

        System.out.println(
                "Time  : "
                        + cost
                        + " ms"
        );


        System.out.println(
                "======================================"
        );
    }


    /**
     * 默认 JASPER 输出路径。
     *
     * test.jrxml
     *
     * ->
     *
     * test.jasper
     */
    private static File defaultJasper(
            File input) {


        String name =
                input.getName();


        if (name.toLowerCase()
                .endsWith(".jrxml")) {

            name =
                    name.substring(
                            0,
                            name.length()
                                    - ".jrxml".length()
                    );
        }


        return new File(
                input.getParentFile(),
                name + ".jasper"
        );
    }


    /**
     * 默认 PDF 输出路径。
     *
     * test.jasper
     *
     * ->
     *
     * test.pdf
     */
    private static File defaultPdf(
            File input) {


        String name =
                input.getName();


        if (name.toLowerCase()
                .endsWith(".jasper")) {

            name =
                    name.substring(
                            0,
                            name.length()
                                    - ".jasper".length()
                    );
        }


        return new File(
                input.getParentFile(),
                name + ".pdf"
        );
    }


    /**
     * 创建输出目录。
     */
    private static void createParent(
            File file) {


        File parent =
                file.getParentFile();


        if (parent != null
                && !parent.exists()
                && !parent.mkdirs()
                && !parent.exists()) {


            throw new IllegalStateException(
                    "Unable to create output directory: "
                            + parent.getAbsolutePath()
            );
        }
    }


    /**
     * 打印帮助。
     */
    private static void printUsage() {

        System.out.println();

        System.out.println(
                "JRXML Compiler / Jasper Preview"
        );

        System.out.println(
                "JasperReports 6.20.0"
        );

        System.out.println();


        System.out.println(
                "Compile JRXML:"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "input.jrxml"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "input.jrxml output.jasper"
        );


        System.out.println();


        System.out.println(
                "Preview JASPER as PDF:"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "--preview input.jasper"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "--preview input.jasper output.pdf"
        );


        System.out.println();


        System.out.println(
                "Start Web UI:"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "--server"
        );

        System.out.println(
                "  java -jar "
                        + "jrxml-compiler-6.20.0.jar "
                        + "--server 8080 ./webwork"
        );


        System.out.println();


        System.out.println(
                "Note:"
        );

        System.out.println(
                "  --preview uses JREmptyDataSource."
        );

        System.out.println(
                "  It is intended for static reports."
        );

        System.out.println();
    }
}