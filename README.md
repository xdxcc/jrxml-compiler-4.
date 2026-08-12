# JRXML Compiler 6.20.0

基于 JasperReports 6.20.0 + Java 8 的独立报表编译 / 预览工具，新增 **Web 图形界面**。

## 构建

```bash
mvn clean package
```

产物：`target/jrxml-compiler-6.20.0.jar`（可执行 fat JAR）。

## 依赖分离分发版（推荐部署形态）

如需将项目 jar 与依赖 jar 分离存放、便于运维替换依赖，可使用 `build-dist.ps1`
（PowerShell）一键生成可分发包并汇总为 zip：

```powershell
powershell -ExecutionPolicy Bypass -File build-dist.ps1
```

前置条件：本机已安装 Maven（脚本会按 `mvnw` → `PATH` → 常见安装路径顺序探测），
并建议设置 `JAVA_HOME`（Windows 上默认回退到 `D:\APP\jdk_1.8`）。

脚本会执行：

1. `mvn clean package -Dshade.skip=true` 跳过 fat jar，产出**瘦 jar**；
2. `mvn dependency:copy-dependencies` 拷贝所有运行时依赖到 `lib/`；
3. 把项目主 jar 同时放入 `lib/` 与分发根目录；
4. 拷贝 `src/main/resources` 下非 `web/` 的资源到 `config/`；
5. 生成 `start.bat` 启动脚本与 `README.txt`；
6. 用 `Compress-Archive` 把整个目录压缩为 `dist/jrxml-compiler-6.20.0.zip`。

生成目录 `dist/jrxml-compiler-6.20.0/` 结构：

```
lib/         项目 jar + 所有第三方依赖 jar
config/      外部配置文件（运行时优先于 jar 内资源，可覆盖）
log/         日志输出目录（空占位，运行时写入）
jrxml-compiler.jar   项目主 jar（瘦 jar）
start.bat    Windows 启动脚本
README.txt   使用说明
```

启动分发版（依赖通过 `lib/*` 加载，`config/` 优先于 jar 内资源）：

```bat
start.bat                        启动 Web 服务（默认端口）
start.bat input.jrxml            编译 JRXML -> JASPER
start.bat --preview in.jasper    导出 JASPER -> PDF
start.bat --server 8080 .        指定端口与工作目录启动 Web 服务
```

> 说明：瘦 jar 内仍含 `web/index.html` 前端资源（程序以 classpath 资源读取，不对外置）；
> 仅配置文件类资源适合放到 `config/` 以便免重打包调整。

## 命令行用法

### 1. 编译 JRXML → JASPER

```bash
java -jar target/jrxml-compiler-6.20.0.jar input.jrxml
java -jar target/jrxml-compiler-6.20.0.jar input.jrxml output.jasper
```

### 2. 预览 JASPER → PDF（空数据源）

```bash
java -jar target/jrxml-compiler-6.20.0.jar --preview input.jasper
java -jar target/jrxml-compiler-6.20.0.jar --preview input.jasper output.pdf
```

### 3. 启动 Web 图形界面

```bash
java -jar target/jrxml-compiler-6.20.0.jar --server
java -jar target/jrxml-compiler-6.20.0.jar --server 8080 ./webwork
```

默认监听 `http://localhost:8080`。

## Web 图形界面功能

| 功能区 | 说明 |
|---|---|
| **JRXML 编辑器** | 支持在线编辑、拖拽上传、加载内置示例 |
| **报表参数** | 编译后自动解析 `<parameter>` 声明，生成类型感知的表单 |
| **编译 → JASPER** | 一键编译，实时输出日志 + 耗时 |
| **预览 → PDF** | 填入参数后预览 PDF，页面内嵌展示 |
| **下载产物** | 可下载 .jasper / .pdf |

## API

Web 模式提供以下 REST 接口（前端自动调用）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 前端页面 |
| POST | `/api/compile` | 编译 JRXML（body: `{"filename":"...","content":"..."}`） |
| POST | `/api/preview` | 填充 JASPER 导出 PDF（body: `{"file":"...jasper","params":{...}}`） |
| GET | `/api/params?file=xxx` | 提取 JRXML 参数定义 |
| GET | `/api/download?file=xxx` | 下载产物文件 |

## 项目结构

```
src/main/java/com/example/jasper/
├── JrxmlCompiler.java   # 主入口（--server / --preview / 编译）
├── JrxmlUtils.java      # JRXML 参数解析 + 类型转换
├── Json.java            # 零依赖轻量 JSON 解析 / 序列化
└── ReportWebServer.java # JDK 内置 HttpServer Web 服务

src/main/resources/web/
└── index.html           # 前端单页面（编辑器/参数/日志/PDF）
```

## 技术要点

- **零新增依赖**：Web 服务基于 JDK 内置 `com.sun.net.httpserver`，JSON 自己解析，不引入 Spring / Gson 等框架。
- **DTD 安全**：JRXML 参数解析关闭了外部实体加载，使用空 EntityResolver 避免网络访问。
- **退出码设计**：`main()` 调用 `System.exit()`，但 `run()` / `compile()` / `preview()` 方法不调用——方便 JUnit 测试。
- **路径安全**：产物下载限定在工作目录内，防止路径穿越。

## Signed JAR 修复

JasperReports 依赖签名 JAR。Shade 插件会排除 `META-INF/*.SF`、`.DSA`、`.RSA` 签名文件，避免 `SecurityException`。

构建后请使用新生成的 JAR，不要复用旧的。
