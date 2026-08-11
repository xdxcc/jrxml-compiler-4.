@echo off

set JAVA_HOME=D:\APP\jdk_1.8

"%JAVA_HOME%\bin\java.exe" ^
-cp "target\jrxml-compiler-1.0.0.jar;target\lib\*" ^
com.example.jasper.JrxmlCompiler %*



pause