package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.CodeFindingEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CodeVisionAnalysisEngine {

    private final CodeVisionRecommendationEngine recommendationEngine;

    public CodeVisionAnalysisEngine(CodeVisionRecommendationEngine recommendationEngine) {
        this.recommendationEngine = recommendationEngine != null ? recommendationEngine : new CodeVisionRecommendationEngine();
    }

    public CodeVisionAnalysisEngine() {
        this.recommendationEngine = new CodeVisionRecommendationEngine();
    }

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "node_modules", "target", "build", "dist", "coverage", "vendor",
            ".venv", "venv", "__pycache__", ".git", ".idea", ".vscode", "bin", "out", ".next"
    );

    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".pdf", ".exe", ".dll",
            ".class", ".jar", ".zip", ".tar", ".gz", ".7z", ".mp4", ".webm", ".woff", ".ttf",
            ".eot", ".min.js", ".min.css", ".map", ".db", ".sqlite", ".pyc", ".o", ".so", ".a", ".dylib"
    );

    public boolean isBinaryContent(String content) {
        if (content == null) return false;
        int checkLen = Math.min(content.length(), 1000);
        int nullCount = 0;
        for (int i = 0; i < checkLen; i++) {
            if (content.charAt(i) == '\0') {
                nullCount++;
            }
        }
        return nullCount > 0;
    }

    private static final Map<String, String> EXTENSION_LANGUAGE_MAP = Map.ofEntries(
            Map.entry(".java", "Java"),
            Map.entry(".py", "Python"),
            Map.entry(".js", "JavaScript"),
            Map.entry(".jsx", "React JSX"),
            Map.entry(".ts", "TypeScript"),
            Map.entry(".tsx", "React TSX"),
            Map.entry(".go", "Go"),
            Map.entry(".cs", "C#"),
            Map.entry(".cpp", "C++"),
            Map.entry(".cc", "C++"),
            Map.entry(".cxx", "C++"),
            Map.entry(".c", "C"),
            Map.entry(".h", "C/C++ Header"),
            Map.entry(".hpp", "C/C++ Header"),
            Map.entry(".php", "PHP"),
            Map.entry(".rb", "Ruby"),
            Map.entry(".kt", "Kotlin"),
            Map.entry(".swift", "Swift"),
            Map.entry(".rs", "Rust"),
            Map.entry(".yml", "YAML"),
            Map.entry(".yaml", "YAML"),
            Map.entry(".xml", "XML"),
            Map.entry(".json", "JSON"),
            Map.entry(".gradle", "Gradle")
    );

    private static final Set<String> SUPPORTED_CONFIG_FILENAMES = Set.of(
            "pom.xml", "package.json", "requirements.txt", "dockerfile",
            "build.gradle", "build.gradle.kts", "settings.gradle"
    );

    private static final long MAX_FILE_SIZE_BYTES = 500 * 1024; // 500 KB

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileAnalysisResult {
        private String filePath;
        private String fileHash;
        private String language;
        private int linesOfCode;
        private int riskScore;
        private String severity;
        private int confidence;
        private String analysisType;
        private Map<String, Object> metrics;
        private List<CodeFindingEntity> findings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CodeSymbol {
        private String name;
        private String kind; // FUNCTION, METHOD, CLASS, COMPONENT
        private int startLine;
        private int endLine;
    }

    /**
     * Determines whether a file path should be analyzed.
     */
    public boolean shouldAnalyzeFile(String filePath, long sizeBytes) {
        if (filePath == null || filePath.isBlank()) return false;
        if (sizeBytes > MAX_FILE_SIZE_BYTES) return false;

        String lowerPath = filePath.toLowerCase().replace('\\', '/');
        for (String dir : EXCLUDED_DIRECTORIES) {
            if (lowerPath.contains("/" + dir + "/") || lowerPath.startsWith(dir + "/") || lowerPath.endsWith("/" + dir)) {
                return false;
            }
        }

        String fileName = getFileName(lowerPath);
        if (SUPPORTED_CONFIG_FILENAMES.contains(fileName) || lowerPath.contains(".github/workflows/")) {
            return true;
        }

        String ext = getFileExtension(lowerPath);
        if (EXCLUDED_EXTENSIONS.contains(ext)) return false;
        return EXTENSION_LANGUAGE_MAP.containsKey(ext);
    }

    public String detectLanguage(String filePath) {
        if (filePath == null) return "Unknown";
        String lower = filePath.toLowerCase().replace('\\', '/');
        String fileName = getFileName(lower);

        if (lower.contains(".github/workflows/") || fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return "YAML Config";
        }
        if ("dockerfile".equals(fileName)) return "Dockerfile";
        if ("pom.xml".equals(fileName)) return "Maven POM XML";
        if ("package.json".equals(fileName)) return "npm Package JSON";
        if ("requirements.txt".equals(fileName)) return "Python Requirements";
        if ("build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName)) return "Gradle Config";

        String ext = getFileExtension(lower);
        return EXTENSION_LANGUAGE_MAP.getOrDefault(ext, "Config/Source File");
    }

    private String getFileExtension(String path) {
        if (path == null) return "";
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length() - 1) return "";
        return path.substring(lastDot).toLowerCase();
    }

    private String getFileName(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash == -1) return path.toLowerCase();
        return path.substring(lastSlash + 1).toLowerCase();
    }

    public String computeSHA256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Executes multi-stage static, structural, and hybrid analysis on a source file content.
     */
    public FileAnalysisResult analyzeSourceFile(String filePath, String content, UUID runId, UUID fileAnalysisId) {
        String language = detectLanguage(filePath);
        String fileHash = computeSHA256(content != null ? content : "");
        String[] lines = content != null ? content.split("\r?\n") : new String[0];
        int loc = lines.length;

        List<CodeSymbol> symbols = extractCodeSymbols(lines, language);
        List<CodeFindingEntity> findings = new ArrayList<>();

        // Stage A: Static Pattern Analysis
        performStaticAnalysis(lines, language, filePath, symbols, runId, fileAnalysisId, findings);

        // Stage B: Structural Complexity Analysis
        Map<String, Object> metrics = calculateStructuralMetrics(lines, symbols, findings, content);

        // Stage C: Dynamic Code-Aware Recommendation Synthesis
        int maxNestingVal = 0;
        for (CodeFindingEntity f : findings) {
            String dynamicRec = recommendationEngine.generateRecommendation(
                    f.getFindingType(),
                    f.getTitle(),
                    f.getSymbolName(),
                    f.getStartLine() != null ? f.getStartLine() : 0,
                    f.getEndLine() != null ? f.getEndLine() : 0,
                    language,
                    f.getEvidence(),
                    filePath,
                    maxNestingVal,
                    loc
            );
            if (dynamicRec != null && !dynamicRec.isBlank()) {
                f.setRecommendation(dynamicRec);
            }
        }

        // Stage D: Hybrid Scoring & Localization
        int riskScore = calculateHybridRiskScore(metrics, findings);
        String severity = categorizeSeverity(riskScore);
        int confidence = calculateConfidence(findings, metrics);

        return FileAnalysisResult.builder()
                .filePath(filePath)
                .fileHash(fileHash)
                .language(language)
                .linesOfCode(loc)
                .riskScore(riskScore)
                .severity(severity)
                .confidence(confidence)
                .analysisType(findings.isEmpty() ? "STATIC" : "HYBRID")
                .metrics(metrics)
                .findings(findings)
                .build();
    }

    private List<CodeSymbol> extractCodeSymbols(String[] lines, String language) {
        List<CodeSymbol> symbols = new ArrayList<>();

        if ("Java".equals(language) || "C#".equals(language) || "Kotlin".equals(language)) {
            Pattern pattern = Pattern.compile("^\\s*(public|protected|private|static|final|abstract|synchronized|async|override|fun|val|var)*\\s*(class|interface|struct|enum|record|object|void|[A-Z]\\w*|<.*>)\\s+([a-zA-Z0-9_]+)\\s*\\(");
            Pattern classPattern = Pattern.compile("^\\s*(public|protected|private)*\\s*(class|interface|struct|enum|object)\\s+([a-zA-Z0-9_]+)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher cm = classPattern.matcher(line);
                if (cm.find()) {
                    symbols.add(CodeSymbol.builder().name(cm.group(3)).kind("CLASS").startLine(i + 1).endLine(Math.min(lines.length, i + 100)).build());
                }
                Matcher mm = pattern.matcher(line);
                if (mm.find() && !cm.find()) {
                    symbols.add(CodeSymbol.builder().name(mm.group(3) + "()").kind("METHOD").startLine(i + 1).endLine(Math.min(lines.length, i + 35)).build());
                }
            }
        } else if ("Python".equals(language)) {
            Pattern pyFunc = Pattern.compile("^\\s*def\\s+([a-zA-Z0-9_]+)\\s*\\(");
            Pattern pyClass = Pattern.compile("^\\s*class\\s+([a-zA-Z0-9_]+)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher cm = pyClass.matcher(line);
                if (cm.find()) {
                    symbols.add(CodeSymbol.builder().name(cm.group(1)).kind("CLASS").startLine(i + 1).endLine(Math.min(lines.length, i + 80)).build());
                }
                Matcher fm = pyFunc.matcher(line);
                if (fm.find()) {
                    symbols.add(CodeSymbol.builder().name(fm.group(1) + "()").kind("FUNCTION").startLine(i + 1).endLine(Math.min(lines.length, i + 30)).build());
                }
            }
        } else if ("Go".equals(language)) {
            Pattern goFunc = Pattern.compile("^\\s*func\\s+(\\([^)]+\\)\\s+)?([a-zA-Z0-9_]+)\\s*\\(");
            Pattern goStruct = Pattern.compile("^\\s*type\\s+([a-zA-Z0-9_]+)\\s+(struct|interface)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher sm = goStruct.matcher(line);
                if (sm.find()) {
                    symbols.add(CodeSymbol.builder().name(sm.group(1)).kind("TYPE").startLine(i + 1).endLine(Math.min(lines.length, i + 60)).build());
                }
                Matcher fm = goFunc.matcher(line);
                if (fm.find()) {
                    symbols.add(CodeSymbol.builder().name(fm.group(2) + "()").kind("FUNCTION").startLine(i + 1).endLine(Math.min(lines.length, i + 40)).build());
                }
            }
        } else if ("Ruby".equals(language)) {
            Pattern rbDef = Pattern.compile("^\\s*def\\s+([a-zA-Z0-9_?!]+)");
            Pattern rbClass = Pattern.compile("^\\s*(class|module)\\s+([a-zA-Z0-9_:]+)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher cm = rbClass.matcher(line);
                if (cm.find()) {
                    symbols.add(CodeSymbol.builder().name(cm.group(2)).kind(cm.group(1).toUpperCase()).startLine(i + 1).endLine(Math.min(lines.length, i + 80)).build());
                }
                Matcher dm = rbDef.matcher(line);
                if (dm.find()) {
                    symbols.add(CodeSymbol.builder().name(dm.group(1) + "()").kind("METHOD").startLine(i + 1).endLine(Math.min(lines.length, i + 35)).build());
                }
            }
        } else if ("Rust".equals(language)) {
            Pattern rsFunc = Pattern.compile("^\\s*(pub\\s+)?fn\\s+([a-zA-Z0-9_]+)\\s*");
            Pattern rsStruct = Pattern.compile("^\\s*(pub\\s+)?(struct|enum|trait|impl)\\s+([a-zA-Z0-9_]+)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher sm = rsStruct.matcher(line);
                if (sm.find()) {
                    symbols.add(CodeSymbol.builder().name(sm.group(3)).kind(sm.group(2).toUpperCase()).startLine(i + 1).endLine(Math.min(lines.length, i + 60)).build());
                }
                Matcher fm = rsFunc.matcher(line);
                if (fm.find()) {
                    symbols.add(CodeSymbol.builder().name(fm.group(2) + "()").kind("FUNCTION").startLine(i + 1).endLine(Math.min(lines.length, i + 35)).build());
                }
            }
        } else {
            // JS / TS / JSX / TSX / C / C++ / Swift / Config
            Pattern jsFunc = Pattern.compile("(function\\s+([a-zA-Z0-9_]+)|const\\s+([a-zA-Z0-9_]+)\\s*=\\s*\\([^)]*\\)\\s*=>|export\\s+(default\\s+)?function\\s+([a-zA-Z0-9_]+)|func\\s+([a-zA-Z0-9_]+)|[a-zA-Z0-9_:]+\\s+([a-zA-Z0-9_]+)\\s*\\()");
            Pattern jsClass = Pattern.compile("^\\s*(class|struct)\\s+([a-zA-Z0-9_]+)");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher cm = jsClass.matcher(line);
                if (cm.find()) {
                    symbols.add(CodeSymbol.builder().name(cm.group(2)).kind("CLASS").startLine(i + 1).endLine(Math.min(lines.length, i + 80)).build());
                }
                Matcher fm = jsFunc.matcher(line);
                if (fm.find()) {
                    String name = fm.group(2) != null ? fm.group(2) : (fm.group(3) != null ? fm.group(3) : (fm.group(5) != null ? fm.group(5) : (fm.group(6) != null ? fm.group(6) : fm.group(7))));
                    if (name != null && !name.equals("if") && !name.equals("for") && !name.equals("while") && !name.equals("switch")) {
                        String kind = Character.isUpperCase(name.charAt(0)) ? "COMPONENT" : "FUNCTION";
                        symbols.add(CodeSymbol.builder().name(name + (kind.equals("COMPONENT") ? "" : "()")).kind(kind).startLine(i + 1).endLine(Math.min(lines.length, i + 40)).build());
                    }
                }
            }
        }

        return symbols;
    }

    private void performStaticAnalysis(String[] lines, String language, String filePath, List<CodeSymbol> symbols, UUID runId, UUID fileAnalysisId, List<CodeFindingEntity> findings) {
        int maxNesting = 0;
        int currentNesting = 0;
        boolean inLoopScope = false;
        int loopDepth = 0;
        String fileName = getFileName(filePath);

        Pattern secretPattern = Pattern.compile("(?i)(password|secret|api_key|private_key|token|auth_token|access_token|ghp_[a-zA-Z0-9]{36}|sk_live_[a-zA-Z0-9]{24}|bearer_token)\\s*[:=]\\s*[\"'][^\"']{3,}[\"']");
        Pattern sqlInjectionPattern = Pattern.compile("(?i)(SELECT|INSERT|UPDATE|DELETE)\\s+.*\\+\\s*[a-zA-Z0-9_]+|\\.executeQuery\\s*\\(\\s*[\"'].*\\+|f[\"'].*(SELECT|INSERT|UPDATE|DELETE).*\\{");
        Pattern commandExecPattern = Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec|subprocess\\.(call|Popen|run)|os\\.system\\(|exec\\(|passthru\\(|shell_exec\\(");

        // ── Config File Analysis Rules ──
        if ("dockerfile".equals(fileName)) {
            boolean hasUserDirective = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.toUpperCase().startsWith("FROM ") && line.contains(":latest")) {
                    findings.add(CodeFindingEntity.builder()
                            .fileAnalysisId(fileAnalysisId).analysisRunId(runId)
                            .findingType("Config / Security Issue")
                            .severity("HIGH").confidence(92)
                            .symbolName("Dockerfile").startLine(i + 1).endLine(i + 1)
                            .title("Unpinned Docker Base Image (:latest)")
                            .description("Base image uses unpinned `:latest` tag. Unpinned base images introduce unpredictable build breaks and security regressions.")
                            .evidence("Line " + (i + 1) + ": " + truncate(line, 80))
                            .recommendation("Pin base image to specific digest or immutable version tag (e.g. `node:20.11-alpine`).")
                            .analysisSource("STATIC").build());
                }
                if (line.toUpperCase().startsWith("USER ")) {
                    hasUserDirective = true;
                }
            }
            if (!hasUserDirective && lines.length > 0) {
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId).analysisRunId(runId)
                        .findingType("Config / Security Issue")
                        .severity("MEDIUM").confidence(88)
                        .symbolName("Dockerfile").startLine(1).endLine(lines.length)
                        .title("Container Executed as Root User")
                        .description("Dockerfile lacks non-root USER instruction. Running containerized applications as root risks container escape vulnerabilities.")
                        .evidence("Missing `USER nonroot` or `USER 10001` directive")
                        .recommendation("Add unprivileged user (e.g., `RUN useradd -m appuser && USER appuser`).")
                        .analysisSource("STATIC").build());
            }
            return;
        }

        if ("package.json".equals(fileName)) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.matches(".*\"[^\"]+\"\\s*:\\s*\"\\*\"|.*\"[^\"]+\"\\s*:\\s*\">=.*\"")) {
                    findings.add(CodeFindingEntity.builder()
                            .fileAnalysisId(fileAnalysisId).analysisRunId(runId)
                            .findingType("Dependency Risk")
                            .severity("HIGH").confidence(90)
                            .symbolName("Package Dependencies").startLine(i + 1).endLine(i + 1)
                            .title("Unbounded Wildcard Dependency Version")
                            .description("Dependency specifies wildcard or unbounded version (`*` or `>=`). This allows unvetted breaking releases into builds.")
                            .evidence("Line " + (i + 1) + ": " + truncate(line, 80))
                            .recommendation("Lock dependency versions using exact semver ranges (e.g., `^1.2.3` or `=1.2.3`).")
                            .analysisSource("STATIC").build());
                }
            }
            return;
        }

        if ("requirements.txt".equals(fileName)) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty() && !line.startsWith("#") && !line.contains("==") && !line.contains(">=")) {
                    findings.add(CodeFindingEntity.builder()
                            .fileAnalysisId(fileAnalysisId).analysisRunId(runId)
                            .findingType("Dependency Risk")
                            .severity("MEDIUM").confidence(85)
                            .symbolName("Python Requirements").startLine(i + 1).endLine(i + 1)
                            .title("Unpinned Python Package Requirement")
                            .description("Python package requirement lacks exact version pin (`==`). Unpinned packages introduce build instability.")
                            .evidence("Line " + (i + 1) + ": " + truncate(line, 80))
                            .recommendation("Pin package to exact version (e.g., `requests==2.31.0`).")
                            .analysisSource("STATIC").build());
                }
            }
            return;
        }

        // ── Standard Source File Static Analysis ──
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int lineNum = i + 1;

            // Track loop scope for N+1 / query in loop performance risks
            if (trimmed.startsWith("for ") || trimmed.startsWith("for(") || trimmed.startsWith("while ") || trimmed.startsWith("while(") || trimmed.contains(".map(") || trimmed.contains(".forEach(")) {
                inLoopScope = true;
                loopDepth++;
            }
            if (loopDepth > 0 && (trimmed.equals("}") || trimmed.endsWith("end"))) {
                loopDepth--;
                if (loopDepth <= 0) inLoopScope = false;
            }

            // Track nesting
            for (char c : trimmed.toCharArray()) {
                if (c == '{') currentNesting++;
                else if (c == '}') currentNesting = Math.max(0, currentNesting - 1);
            }
            maxNesting = Math.max(maxNesting, currentNesting);

            // 1. Swallowed Exception / Empty Catch / Rescue
            boolean isCatchLine = trimmed.matches(".*catch\\s*\\(.*\\).*") || trimmed.equals("except:") || trimmed.startsWith("except ") || trimmed.startsWith("rescue ");
            boolean isEmptyBody = trimmed.contains("{}") || trimmed.endsWith("pass") ||
                    (i + 1 < lines.length && (lines[i + 1].trim().equals("}") || lines[i + 1].trim().equals("pass") || lines[i + 1].trim().isEmpty()));
            if (isCatchLine && isEmptyBody) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Exception Handling Issue")
                        .severity("HIGH")
                        .confidence(92)
                        .symbolName(sym != null ? sym.getName() : "Global Context")
                        .startLine(lineNum)
                        .endLine(Math.min(lines.length, lineNum + 3))
                        .title("Swallowed Exception Detected")
                        .description("An exception block is caught without logging, rethrowing, or error handling. Swallowed errors lead to silent failure modes.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Log the exception or rethrow a domain-specific Exception to prevent silent failures.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 2. Hardcoded Credentials / Secrets
            if (secretPattern.matcher(trimmed).find()) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Security Vulnerability")
                        .severity("CRITICAL")
                        .confidence(95)
                        .symbolName(sym != null ? sym.getName() : "Global Context")
                        .startLine(lineNum)
                        .endLine(lineNum)
                        .title("Potential Hardcoded Secret / Credential")
                        .description("Hardcoded token or secret detected directly in source code. Credentials in repositories expose infrastructure to unauthorized access.")
                        .evidence("Line " + lineNum + ": " + maskSecret(trimmed))
                        .recommendation("Extract secrets into environment variables (e.g. System.getenv(), os.getenv()) or secret management vaults.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 3. SQL Injection Risk
            if (sqlInjectionPattern.matcher(trimmed).find()) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Security Vulnerability")
                        .severity("CRITICAL")
                        .confidence(93)
                        .symbolName(sym != null ? sym.getName() : "Database Layer")
                        .startLine(lineNum)
                        .endLine(lineNum)
                        .title("Potential SQL Injection Vulnerability")
                        .description("Dynamic SQL query constructed using string concatenation or unescaped string formatting. This permits arbitrary SQL execution.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Use parameterized queries, PreparedStatement, or ORM parameter binding (e.g. PreparedStatement setString()).")
                        .analysisSource("STATIC")
                        .build());
            }

            // 4. Command Injection / Unsafe OS Execution
            if (commandExecPattern.matcher(trimmed).find()) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Security Vulnerability")
                        .severity("CRITICAL")
                        .confidence(94)
                        .symbolName(sym != null ? sym.getName() : "System Scope")
                        .startLine(lineNum)
                        .endLine(lineNum + 1)
                        .title("Unsafe Command Execution / Shell Injection")
                        .description("Invocation of OS command execution utility detected. Executing shell commands with dynamic input opens remote command injection vulnerabilities.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Avoid system shell invocations or strictly validate and sanitize arguments using argument arrays without shell interpolation.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 5. Dangerous Dynamic Code Execution
            if (trimmed.contains("eval(") || (trimmed.contains("exec(") && !trimmed.contains("execAsync")) || trimmed.contains("dangerouslySetInnerHTML")) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Security Vulnerability")
                        .severity("CRITICAL")
                        .confidence(94)
                        .symbolName(sym != null ? sym.getName() : "Global Context")
                        .startLine(lineNum)
                        .endLine(lineNum + 2)
                        .title("Unsafe Dynamic Execution / Injection Risk")
                        .description("Use of dynamic code evaluation (eval/exec/dangerouslySetInnerHTML) opens arbitrary code execution or Cross-Site Scripting (XSS) vulnerabilities.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Replace dynamic evaluation with static AST parsing, safe DOM sanitizers, or standard JSON parsers.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 6. Database / HTTP Query inside Loop (Performance N+1 Risk)
            if (inLoopScope && (trimmed.contains(".executeQuery") || trimmed.contains("select ") || trimmed.contains("fetch(") || trimmed.contains("axios.") || trimmed.contains("http.Get"))) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Performance Issue")
                        .severity("HIGH")
                        .confidence(89)
                        .symbolName(sym != null ? sym.getName() : "Loop Context")
                        .startLine(lineNum)
                        .endLine(lineNum)
                        .title("Potential N+1 Database / API Call in Loop")
                        .description("Database query or remote HTTP network request invoked inside an iterative loop scope. This creates severe performance bottlenecks.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Batch queries outside the loop or use SQL JOIN / IN clauses to fetch data in a single roundtrip.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 7. Potential Null / Undefined Dereference Risk
            if (("Java".equals(language) || "Kotlin".equals(language) || "Swift".equals(language) || "Rust".equals(language))
                    && (trimmed.contains("!!") || trimmed.contains(".unwrap()") || (trimmed.matches(".*\\b(get|find|lookup|search)[A-Z]\\w*\\(.*\\)\\.[a-zA-Z0-9_]+.*") && !trimmed.contains("Optional")))) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Null/Undefined Risk")
                        .severity("HIGH")
                        .confidence(88)
                        .symbolName(sym != null ? sym.getName() : "Method Scope")
                        .startLine(lineNum)
                        .endLine(lineNum + 1)
                        .title("Unchecked Null Dereference / Force Unwrap Risk")
                        .description("Direct force unwrap (`!!` or `.unwrap()`) or unchecked member access on a query return value without checking for presence.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Use safe call operators (`?.`), `match`, or explicit non-null checking before dereferencing.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 8. Unclosed Resource Leak (Java)
            if ("Java".equals(language) && (trimmed.contains("new FileInputStream") || trimmed.contains("new FileReader") || trimmed.contains("DriverManager.getConnection")) && !trimmed.contains("try (") && !trimmed.contains("try(")) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Resource Management Issue")
                        .severity("MEDIUM")
                        .confidence(85)
                        .symbolName(sym != null ? sym.getName() : "Method Scope")
                        .startLine(lineNum)
                        .endLine(lineNum + 5)
                        .title("Potential Resource Leak (Unclosed Handle)")
                        .description("Resource allocation detected outside try-with-resources statement. Unclosed I/O or database streams degrade memory & system handles.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Use Java try-with-resources (`try (BufferedReader br = ...) { ... }`) to guarantee automatic resource cleanup.")
                        .analysisSource("STATIC")
                        .build());
            }

            // 9. Technical Debt Markers
            if (trimmed.contains("TODO") || trimmed.contains("FIXME") || trimmed.contains("HACK") || trimmed.contains("XXX")) {
                CodeSymbol sym = findEnclosingSymbol(symbols, lineNum);
                findings.add(CodeFindingEntity.builder()
                        .fileAnalysisId(fileAnalysisId)
                        .analysisRunId(runId)
                        .findingType("Maintainability Risk")
                        .severity("LOW")
                        .confidence(90)
                        .symbolName(sym != null ? sym.getName() : "Inline Comment")
                        .startLine(lineNum)
                        .endLine(lineNum)
                        .title("Unresolved Technical Debt Comment")
                        .description("Inline marker (TODO/FIXME/HACK) signals incomplete implementation or deferred refactoring.")
                        .evidence("Line " + lineNum + ": " + truncate(trimmed, 80))
                        .recommendation("Resolve deferred task or convert comment into an actionable GitHub issue.")
                        .analysisSource("STATIC")
                        .build());
            }
        }

        // Deep Nesting Warning
        if (maxNesting >= 4) {
            findings.add(CodeFindingEntity.builder()
                    .fileAnalysisId(fileAnalysisId)
                    .analysisRunId(runId)
                    .findingType("Complexity Risk")
                    .severity("MEDIUM")
                    .confidence(87)
                    .symbolName("File Structural Nesting")
                    .startLine(1)
                    .endLine(Math.min(lines.length, 50))
                    .title("High Control Flow Nesting Depth (" + maxNesting + " levels)")
                    .description("Excessive nesting depth increases cognitive complexity and error rate during code maintenance.")
                    .evidence("Maximum indentation nesting depth: " + maxNesting + " levels")
                    .recommendation("Refactor deeply nested logic using guard clauses or break nested loops into helper methods.")
                    .analysisSource("STATIC")
                    .build());
        }
    }

    private Map<String, Object> calculateStructuralMetrics(String[] lines, List<CodeSymbol> symbols, List<CodeFindingEntity> findings, String content) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        int loc = lines.length;
        int cyclomaticComplexity = 1;
        int conditionalCount = 0;
        int loopCount = 0;

        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("if ") || t.startsWith("if(") || t.contains(" else if") || t.startsWith("switch ") || t.startsWith("case ")) {
                cyclomaticComplexity++;
                conditionalCount++;
            }
            if (t.startsWith("for ") || t.startsWith("for(") || t.startsWith("while ") || t.startsWith("while(") || t.contains(".map(") || t.contains(".forEach(")) {
                cyclomaticComplexity++;
                loopCount++;
            }
            if (t.contains("&&") || t.contains("||") || t.contains(" catch ")) {
                cyclomaticComplexity++;
            }
        }

        metrics.put("lines_of_code", loc);
        metrics.put("symbol_count", symbols.size());
        metrics.put("cyclomatic_complexity", cyclomaticComplexity);
        metrics.put("conditional_density", Math.round((double) conditionalCount / Math.max(1, loc) * 1000.0) / 10.0);
        metrics.put("loop_density", Math.round((double) loopCount / Math.max(1, loc) * 1000.0) / 10.0);
        metrics.put("finding_count", findings.size());

        if (content != null) {
            String sample = content;
            if (sample.length() > 50000) {
                sample = sample.substring(0, 50000) + "\n// ... [Source file truncated for viewer performance]";
            }
            metrics.put("code_sample", sample);
        }

        // Check for Large File / Oversized Methods
        if (loc > 350) {
            findings.add(CodeFindingEntity.builder()
                    .findingType("Maintainability Risk")
                    .severity("MEDIUM")
                    .confidence(89)
                    .symbolName("File Level Scope")
                    .startLine(1)
                    .endLine(loc)
                    .title("Oversized Source File (" + loc + " LOC)")
                    .description("Source file exceeds 350 lines of code. Large files violate Single Responsibility Principle and increase regression risks.")
                    .evidence("Total file lines: " + loc)
                    .recommendation("Split oversized class/component into smaller decoupled modules.")
                    .analysisSource("HYBRID")
                    .build());
        }

        return metrics;
    }

    private int calculateHybridRiskScore(Map<String, Object> metrics, List<CodeFindingEntity> findings) {
        int baseScore = 10;
        int findingImpact = 0;

        for (CodeFindingEntity f : findings) {
            switch (f.getSeverity()) {
                case "CRITICAL" -> findingImpact += 35;
                case "HIGH" -> findingImpact += 20;
                case "MEDIUM" -> findingImpact += 10;
                case "LOW" -> findingImpact += 4;
            }
        }

        int complexity = (int) metrics.getOrDefault("cyclomatic_complexity", 1);
        int loc = (int) metrics.getOrDefault("lines_of_code", 0);

        int complexityImpact = (int) Math.min(25, (complexity * 1.5));
        int locImpact = (int) Math.min(15, (loc / 50.0));

        int total = baseScore + findingImpact + complexityImpact + locImpact;
        return Math.min(100, Math.max(0, total));
    }

    private String categorizeSeverity(int riskScore) {
        if (riskScore >= 75) return "CRITICAL";
        if (riskScore >= 50) return "HIGH";
        if (riskScore >= 25) return "MEDIUM";
        return "LOW";
    }

    private int calculateConfidence(List<CodeFindingEntity> findings, Map<String, Object> metrics) {
        if (findings.isEmpty()) return 85;
        double sum = 0;
        for (CodeFindingEntity f : findings) {
            sum += (f.getConfidence() != null ? f.getConfidence() : 85);
        }
        return (int) Math.round(sum / findings.size());
    }

    private CodeSymbol findEnclosingSymbol(List<CodeSymbol> symbols, int lineNum) {
        for (CodeSymbol sym : symbols) {
            if (lineNum >= sym.getStartLine() && lineNum <= sym.getEndLine()) {
                return sym;
            }
        }
        return null;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private String maskSecret(String line) {
        return line.replaceAll("(?i)(password|secret|api_key|private_key|token)\\s*=\\s*[\"'][^\"']+[\"']", "$1 = \"********\"");
    }
}
