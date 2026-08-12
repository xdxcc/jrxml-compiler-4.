package com.example.jasper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 基于 JDK 内置 HttpServer 的报表 Web 工具。
 *
 * <p>提供：
 *
 * <ul>
 *   <li>GET  /                    → 前端页面（index.html）</li>
 *   <li>POST /api/compile         → 编译 JRXML -> JASPER</li>
 *   <li>POST /api/preview         → 填充 JASPER 并导出 PDF</li>
 *   <li>GET  /api/params?file=... → 提取 JRXML 参数定义</li>
 *   <li>GET  /api/download?file=  → 下载工作目录内的产物</li>
 * </ul>
 *
 * <p>零第三方依赖。文件只允许在工作目录内读写，防止路径穿越。
 */
public final class ReportWebServer {

    /** 前端页面编码。 */
    private static final Charset UTF_8 =
            Charset.forName("UTF-8");


    private final File workDir;

    private final File uploadDir;

    private final File outDir;

    private final HttpServer server;


    private ReportWebServer(
            int port,
            File workDir) throws IOException {

        this.workDir = workDir;

        this.uploadDir =
                new File(workDir, "upload");

        this.outDir =
                new File(workDir, "out");

        this.server =
                HttpServer.create(
                        new InetSocketAddress(port),
                        0
                );

        server.createContext(
                "/",
                new Handler()
        );

        server.setExecutor(
                Executors.newFixedThreadPool(4)
        );
    }


    /**
     * 启动 Web 服务（阻塞当前线程）。
     *
     * @param args --server 之后的参数：[port] [workdir]
     */
    public static void start(String[] args) throws Exception {

        int port = 8080;

        File workDir =
                new File(".", "webwork");

        if (args != null) {

            if (args.length > 0
                    && args[0] != null
                    && args[0].trim().length() > 0) {

                port = Integer.parseInt(args[0].trim());
            }

            if (args.length > 1
                    && args[1] != null
                    && args[1].trim().length() > 0) {

                workDir = new File(args[1].trim());
            }
        }

        ReportWebServer s =
                new ReportWebServer(port, workDir);

        s.init();

        s.server.start();

        System.out.println();
        System.out.println(
                "======================================"
        );
        System.out.println(
                "JRXML Compiler Web Server"
        );
        System.out.println(
                "JasperReports 6.20.0"
        );
        System.out.println(
                "======================================"
        );
        System.out.println(
                "Work dir : "
                        + s.workDir.getAbsolutePath()
        );
        System.out.println(
                "URL      : http://localhost:"
                        + port
        );
        System.out.println(
                "Press Ctrl+C to stop."
        );
        System.out.println(
                "======================================"
        );
    }


    private void init() throws IOException {

        if (!uploadDir.exists() && !uploadDir.mkdirs()) {

            throw new IOException(
                    "Cannot create upload dir: "
                            + uploadDir
            );
        }

        if (!outDir.exists() && !outDir.mkdirs()) {

            throw new IOException(
                    "Cannot create output dir: "
                            + outDir
            );
        }
    }


    // =================================================================
    // 请求分发
    // =================================================================

    private final class Handler implements HttpHandler {

        public void handle(HttpExchange ex) throws IOException {

            try {

                String path = ex.getRequestURI().getPath();

                String method = ex.getRequestMethod();

                if ("/".equals(path)
                        || "/index.html".equals(path)) {

                    sendResource(ex, "/web/index.html",
                            "text/html; charset=utf-8");

                } else if ("/api/compile".equals(path)
                        && "POST".equals(method)) {

                    handleCompile(ex);

                } else if ("/api/preview".equals(path)
                        && "POST".equals(method)) {

                    handlePreview(ex);

                } else if ("/api/params".equals(path)
                        && "GET".equals(method)) {

                    handleParams(ex);

                } else if ("/api/download".equals(path)
                        && "GET".equals(method)) {

                    handleDownload(ex);

                } else {

                    sendText(ex, 404,
                            "Not Found: " + path,
                            "text/plain; charset=utf-8");
                }

            } catch (Exception e) {

                Map<String, Object> err =
                        new LinkedHashMap<String, Object>();

                err.put("ok", Boolean.FALSE);

                err.put(
                        "error",
                        (e.getMessage() == null
                                ? e.getClass().getName()
                                : e.getMessage())
                );

                try {

                    sendJson(ex, 500, err);

                } catch (IOException io) {
                    // 客户端已断开，忽略
                }
            }
        }
    }


    // =================================================================
    // POST /api/compile
    // =================================================================

    private void handleCompile(
            HttpExchange ex) throws Exception {

        Map<String, Object> body =
                Json.parseObject(readBody(ex));

        String filename =
                str(body.get("filename"));

        String content =
                str(body.get("content"));

        if (filename == null
                || filename.length() == 0) {

            throw new IllegalArgumentException(
                    "filename is required"
            );
        }

        if (content == null) {

            throw new IllegalArgumentException(
                    "content is required"
            );
        }

        String base = sanitizeBaseName(filename);

        File jrxml =
                new File(uploadDir, base + ".jrxml");

        writeTextFile(jrxml, content);

        File jasper =
                new File(outDir, base + ".jasper");

        long start = System.currentTimeMillis();

        String log = runWithCapturedOutput(
                new Action() {
                    public void run() throws Exception {
                        JrxmlCompiler.compile(jrxml, jasper);
                    }
                }
        );

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result =
                new LinkedHashMap<String, Object>();

        result.put("ok", Boolean.TRUE);

        result.put("name", base);

        result.put("log", log);

        result.put("elapsed", Long.valueOf(elapsed));

        result.put(
                "jasper",
                "/api/download?file="
                        + encodeUrl(jasper.getName())
        );

        sendJson(ex, 200, result);
    }


    // =================================================================
    // POST /api/preview
    // =================================================================

    private void handlePreview(
            HttpExchange ex) throws Exception {

        Map<String, Object> body =
                Json.parseObject(readBody(ex));

        String file = str(body.get("file"));

        if (file == null || file.length() == 0) {

            throw new IllegalArgumentException(
                    "file is required"
            );
        }

        File jasper = resolveInWorkDir(file);

        if (!jasper.isFile()
                || !jasper.getName().toLowerCase()
                .endsWith(".jasper")) {

            throw new IllegalArgumentException(
                    "Not a JASPER file: " + file
            );
        }

        // 参数转换
        Map<String, Object> params =
                new HashMap<String, Object>();

        Object raw = body.get("params");

        if (raw instanceof Map) {

            @SuppressWarnings("unchecked")
            Map<String, Object> input =
                    (Map<String, Object>) raw;

            List<JrxmlUtils.ParameterDef> defs =
                    new ArrayList<JrxmlUtils.ParameterDef>();

            // 优先尝试从同名 JRXML 读取类型信息
            File jrxml = new File(
                    uploadDir,
                    jasper.getName().replace(
                            ".jasper", ".jrxml")
            );

            if (jrxml.isFile()) {

                try {

                    defs =
                            JrxmlUtils.extractParameters(jrxml);

                } catch (Exception ignore) {
                    defs = new ArrayList<JrxmlUtils.ParameterDef>();
                }
            }

            for (Map.Entry<String, Object> e :
                    input.entrySet()) {

                String className = null;

                for (JrxmlUtils.ParameterDef d : defs) {

                    if (d.name.equals(e.getKey())) {

                        className = d.className;

                        break;
                    }
                }

                Object v = e.getValue();

                if (v == null) {

                    continue;
                }

                if (v instanceof String) {

                    params.put(
                            e.getKey(),
                            JrxmlUtils.convertValue(
                                    (String) v, className)
                    );

                } else if (v instanceof Number
                        || v instanceof Boolean) {

                    params.put(e.getKey(), v);

                } else {

                    params.put(
                            e.getKey(),
                            String.valueOf(v)
                    );
                }
            }
        }

        File pdf = new File(
                outDir,
                jasper.getName().replace(
                        ".jasper", ".pdf")
        );

        long start = System.currentTimeMillis();

        String log = runWithCapturedOutput(
                new Action() {
                    public void run() throws Exception {
                        JrxmlCompiler.preview(
                                jasper, pdf, params);
                    }
                }
        );

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result =
                new LinkedHashMap<String, Object>();

        result.put("ok", Boolean.TRUE);

        result.put("log", log);

        result.put("elapsed", Long.valueOf(elapsed));

        result.put(
                "pdf",
                "/api/download?file="
                        + encodeUrl(pdf.getName())
        );

        sendJson(ex, 200, result);
    }


    // =================================================================
    // GET /api/params?file=xxx.jrxml
    // =================================================================

    private void handleParams(
            HttpExchange ex) throws Exception {

        String file = queryParam(ex, "file");

        if (file == null || file.length() == 0) {

            throw new IllegalArgumentException(
                    "file is required"
            );
        }

        File f = resolveInWorkDir(file);

        if (!f.isFile()) {

            throw new IllegalArgumentException(
                    "File not found: " + file
            );
        }

        List<JrxmlUtils.ParameterDef> defs;

        try {

            defs = JrxmlUtils.extractParameters(f);

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Cannot parse JRXML: " + e.getMessage()
            );
        }

        List<Map<String, Object>> items =
                new ArrayList<Map<String, Object>>();

        for (JrxmlUtils.ParameterDef d : defs) {

            Map<String, Object> item =
                    new LinkedHashMap<String, Object>();

            item.put("name", d.name);

            item.put("className", d.className);

            item.put("hasDefault", Boolean.valueOf(
                    d.hasDefault));

            if (d.hasDefault) {

                item.put("defaultValue", d.defaultValue);
            }

            items.add(item);
        }

        Map<String, Object> result =
                new LinkedHashMap<String, Object>();

        result.put("ok", Boolean.TRUE);

        result.put("parameters", items);

        sendJson(ex, 200, result);
    }


    // =================================================================
    // GET /api/download?file=xxx
    // =================================================================

    private void handleDownload(
            HttpExchange ex) throws Exception {

        String file = queryParam(ex, "file");

        if (file == null || file.length() == 0) {

            throw new IllegalArgumentException(
                    "file is required"
            );
        }

        File f = resolveInWorkDir(file);

        if (!f.isFile()) {

            throw new IllegalArgumentException(
                    "File not found: " + file
            );
        }

        String name = f.getName();

        String contentType;

        if (name.toLowerCase().endsWith(".jasper")) {

            contentType =
                    "application/octet-stream";

        } else if (name.toLowerCase().endsWith(".pdf")) {

            contentType = "application/pdf";

        } else if (name.toLowerCase().endsWith(".jrxml")) {

            contentType =
                    "text/xml; charset=utf-8";

        } else {

            contentType =
                    "application/octet-stream";
        }

        ex.getResponseHeaders().set(
                "Content-Type", contentType);

        ex.getResponseHeaders().set(
                "Content-Disposition",
                "attachment; filename=\""
                        + name + "\"");

        ex.sendResponseHeaders(200, f.length());

        InputStream in = null;
        OutputStream os = null;

        try {

            in = new java.io.FileInputStream(f);

            os = ex.getResponseBody();

            byte[] buf = new byte[8192];

            int n;

            while ((n = in.read(buf)) > 0) {

                os.write(buf, 0, n);
            }

        } finally {

            if (in != null) {
                in.close();
            }

            if (os != null) {
                os.close();
            }
        }
    }


    // =================================================================
    // 工具方法
    // =================================================================

    /** 执行动作并捕获 System.out / System.err。 */
    private synchronized String runWithCapturedOutput(
            Action action) throws Exception {

        ByteArrayOutputStream buf =
                new ByteArrayOutputStream();

        PrintStream capture =
                new PrintStream(buf, true, "UTF-8");

        PrintStream oldOut = System.out;

        PrintStream oldErr = System.err;

        try {

            System.setOut(capture);

            System.setErr(capture);

            action.run();

        } finally {

            System.setOut(oldOut);

            System.setErr(oldErr);

            capture.flush();
        }

        return new String(
                buf.toByteArray(), UTF_8);
    }


    private void writeTextFile(
            File file,
            String content) throws IOException {

        java.io.OutputStreamWriter writer =
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(file),
                        UTF_8
                );

        try {

            writer.write(content);

        } finally {

            writer.close();
        }
    }


    private File resolveInWorkDir(String rel)
            throws IOException {

        String decoded =
                URLDecoder.decode(rel, "UTF-8");

        File base = workDir.getCanonicalFile();

        File f = new File(
                base, decoded).getCanonicalFile();

        if (!f.getPath().equals(base.getPath())
                && !f.getPath().startsWith(
                base.getPath() + File.separator)) {

            throw new IOException(
                    "Path outside work dir: " + rel
            );
        }

        return f;
    }


    /**
     * 清洗文件名：
     *
     * 只保留 字母/数字/_/-/./中文，并强制 .jrxml 扩展名。
     */
    private String sanitizeBaseName(String filename) {

        String name = filename;

        int slash = Math.max(
                name.lastIndexOf('/'),
                name.lastIndexOf('\\'));

        if (slash >= 0) {

            name = name.substring(slash + 1);
        }

        if (name.toLowerCase().endsWith(".jrxml")) {

            name = name.substring(
                    0, name.length() - 6);
        }

        StringBuilder sb = new StringBuilder(name.length());

        for (int i = 0; i < name.length(); i++) {

            char c = name.charAt(i);

            if (Character.isLetterOrDigit(c)
                    || c == '_' || c == '-'
                    || c == '.' || c > 127) {

                sb.append(c);
            }
        }

        String base = sb.toString();

        if (base.length() == 0) {

            base = "report_" + System.currentTimeMillis();
        }

        return base;
    }


    private static String encodeUrl(String s)
            throws IOException {

        // 简单编码：中文等特殊字符转 UTF-8 URL 编码
        StringBuilder sb = new StringBuilder();

        for (byte b : s.getBytes(UTF_8)) {

            int v = b & 0xFF;

            if ((v >= 'a' && v <= 'z')
                    || (v >= 'A' && v <= 'Z')
                    || (v >= '0' && v <= '9')
                    || v == '-' || v == '_'
                    || v == '.' || v == '~') {

                sb.append((char) v);

            } else {

                sb.append('%');

                String h = Integer.toHexString(v)
                        .toUpperCase();

                if (h.length() < 2) {

                    sb.append('0');
                }

                sb.append(h);
            }
        }

        return sb.toString();
    }


    private static String queryParam(
            HttpExchange ex,
            String key) {

        String query = ex.getRequestURI().getRawQuery();

        if (query == null) {

            return null;
        }

        for (String pair : query.split("&")) {

            int eq = pair.indexOf('=');

            if (eq < 0) {

                continue;
            }

            String k = pair.substring(0, eq);

            if (k.equals(key)) {

                try {

                    return URLDecoder.decode(
                            pair.substring(eq + 1), "UTF-8");

                } catch (Exception e) {

                    return pair.substring(eq + 1);
                }
            }
        }

        return null;
    }


    private static String str(Object o) {

        return o == null ? null : String.valueOf(o);
    }


    private static String readBody(
            HttpExchange ex) throws IOException {

        ByteArrayOutputStream bos =
                new ByteArrayOutputStream();

        InputStream is = ex.getRequestBody();

        try {

            byte[] buf = new byte[8192];

            int n;

            while ((n = is.read(buf)) > 0) {

                bos.write(buf, 0, n);
            }

        } finally {

            is.close();
        }

        return new String(
                bos.toByteArray(), UTF_8);
    }


    private static void sendJson(
            HttpExchange ex,
            int code,
            Object obj) throws IOException {

        sendText(
                ex, code,
                Json.stringify(obj),
                "application/json; charset=utf-8"
        );
    }


    private static void sendText(
            HttpExchange ex,
            int code,
            String text,
            String contentType) throws IOException {

        byte[] body = text.getBytes(UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", contentType);

        ex.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff");

        ex.sendResponseHeaders(code, body.length);

        OutputStream os = ex.getResponseBody();

        try {

            os.write(body);

        } finally {

            os.close();
        }
    }


    /**
     * 从 classpath 读取资源并发送。
     */
    private static void sendResource(
            HttpExchange ex,
            String resource,
            String contentType) throws IOException {

        InputStream in =
                ReportWebServer.class
                        .getResourceAsStream(resource);

        if (in == null) {

            sendText(ex, 404,
                    "Resource not found: " + resource,
                    "text/plain; charset=utf-8");

            return;
        }

        ByteArrayOutputStream bos =
                new ByteArrayOutputStream();

        byte[] buf = new byte[8192];

        int n;

        try {

            while ((n = in.read(buf)) > 0) {

                bos.write(buf, 0, n);
            }

        } finally {

            in.close();
        }

        sendText(
                ex, 200,
                new String(bos.toByteArray(), UTF_8),
                contentType
        );
    }


    /** 可抛异常的 action。 */
    private interface Action {

        void run() throws Exception;
    }
}
