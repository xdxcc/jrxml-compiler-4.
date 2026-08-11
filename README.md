# JRXML Compiler 1.0.0

Standalone JRXML -> JASPER compiler using JasperReports 4.0.1 and Java 8.

Build:
```bash
mvn clean package
```

Run:
```bash
java -jar target/jrxml-compiler-1.0.0.jar sample/simple_test.jrxml
```

Or:
```bash
java -jar target/jrxml-compiler-1.0.0.jar input.jrxml output.jasper
```

The compiler separates command-line exit handling from the reusable `compile()` method, so JUnit/Surefire will not be killed by `System.exit()`.

The Shade plugin creates an executable fat JAR and sets:
`Main-Class: com.example.jasper.JrxmlCompiler`

This project intentionally does not configure Maven `deploy`; use `mvn clean package` to produce the JAR.


## Important: BouncyCastle / signed JAR fix

JasperReports 4.0.1 may pull old signed dependencies such as BouncyCastle.
A normal shaded JAR can fail at startup with:

```text
java.lang.SecurityException:
no manifest section for signature file entry ...
```

The Shade configuration in this version removes `META-INF/*.SF`,
`META-INF/*.DSA`, and `META-INF/*.RSA` signature metadata while preserving
the actual classes. This is required because the classes are repackaged into
a new fat JAR and the original dependency signatures are no longer valid.

After replacing the project, run:

```bash
mvn clean package
```

Do not copy the old JAR from `target`; use the newly generated JAR.
