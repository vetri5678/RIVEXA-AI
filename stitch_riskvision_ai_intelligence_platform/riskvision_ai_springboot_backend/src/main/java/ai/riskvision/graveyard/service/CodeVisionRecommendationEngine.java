package ai.riskvision.graveyard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CodeVisionRecommendationEngine {

    /**
     * Generates a dynamic, code-aware recommendation specific to the programming language,
     * affected code symbol, line numbers, evidence snippet, and finding category.
     */
    public String generateRecommendation(
            String findingType,
            String title,
            String symbolName,
            int startLine,
            int endLine,
            String language,
            String evidence,
            String filePath,
            int maxNesting,
            int linesOfCode
    ) {
        String sym = (symbolName != null && !symbolName.isBlank() && !"Global Context".equals(symbolName) && !"Scope".equals(symbolName))
                ? "`" + symbolName + "`"
                : "the enclosing scope";

        String linesStr = (startLine > 0 && endLine > 0 && startLine != endLine)
                ? "lines " + startLine + "–" + endLine
                : (startLine > 0 ? "line " + startLine : "this region");

        String fileName = getFileName(filePath);
        String lang = (language != null && !language.isBlank()) ? language : "Source Code";

        // 1. High Control Flow Nesting Depth
        if ("Complexity Risk".equalsIgnoreCase(findingType) || (title != null && title.toLowerCase().contains("nesting"))) {
            return generateNestingRecommendation(sym, linesStr, lang, maxNesting, fileName);
        }

        // 2. Swallowed Exception / Empty Catch
        if ("Exception Handling Issue".equalsIgnoreCase(findingType) || (title != null && title.toLowerCase().contains("exception"))) {
            return generateExceptionRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 3. Hardcoded Secret / Credential
        if (title != null && (title.toLowerCase().contains("secret") || title.toLowerCase().contains("credential"))) {
            return generateSecretRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 4. SQL Injection
        if (title != null && title.toLowerCase().contains("sql injection")) {
            return generateSqlInjectionRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 5. Command Injection / Shell Exec
        if (title != null && (title.toLowerCase().contains("command execution") || title.toLowerCase().contains("shell"))) {
            return generateCommandInjectionRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 6. Dynamic Evaluation (eval/exec/dangerouslySetInnerHTML)
        if (title != null && (title.toLowerCase().contains("dynamic execution") || title.toLowerCase().contains("eval"))) {
            return generateDynamicEvalRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 7. N+1 Database / API Query in Loop
        if ("Performance Issue".equalsIgnoreCase(findingType) || (title != null && title.toLowerCase().contains("loop"))) {
            return generateN1LoopRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 8. Null / Undefined Dereference / Force Unwrap
        if ("Null/Undefined Risk".equalsIgnoreCase(findingType) || (title != null && title.toLowerCase().contains("null"))) {
            return generateNullDereferenceRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 9. Resource Leak / Unclosed Handle
        if ("Resource Management Issue".equalsIgnoreCase(findingType) || (title != null && title.toLowerCase().contains("resource leak"))) {
            return generateResourceLeakRecommendation(sym, linesStr, lang, evidence, fileName);
        }

        // 10. Oversized Source File / Scope
        if (title != null && title.toLowerCase().contains("oversized")) {
            return "Refactor `" + fileName + "` (" + linesOfCode + " LOC) by decoupling cohesive responsibilities into dedicated helper modules or sub-components.";
        }

        // 11. Config & Dependency Issues (Dockerfile, package.json, requirements.txt)
        if ("Config / Security Issue".equalsIgnoreCase(findingType) || "Dependency Risk".equalsIgnoreCase(findingType)) {
            return generateConfigRecommendation(fileName, title, evidence, linesStr);
        }

        // Fallback Code-Aware Recommendation
        return "Inspect " + sym + " at " + linesStr + " in `" + fileName + "` (" + lang + "). " +
                "Refactor the detected " + (title != null ? title.toLowerCase() : "pattern issue") + " to adhere to " + lang + " standard safety practices.";
    }

    private String generateNestingRecommendation(String sym, String linesStr, String lang, int maxNesting, String fileName) {
        if ("Java".equalsIgnoreCase(lang) || "C#".equalsIgnoreCase(lang) || "Kotlin".equalsIgnoreCase(lang)) {
            return "Method " + sym + " at " + linesStr + " in `" + fileName + "` contains " + maxNesting + " nested control-flow levels. " +
                    "Reduce cognitive complexity by applying early-exit guard clauses (e.g. `if (!valid) return;`) and extracting inner loop logic into a helper method.";
        } else if ("Python".equalsIgnoreCase(lang)) {
            return "Function " + sym + " at " + linesStr + " in `" + fileName + "` reaches an indentation depth of " + maxNesting + " levels. " +
                    "Simplify control flow by returning early on invalid conditions and refactoring nested blocks into helper functions or generator expressions.";
        } else if ("TypeScript".equalsIgnoreCase(lang) || "JavaScript".equalsIgnoreCase(lang) || "React TSX".equalsIgnoreCase(lang) || "React JSX".equalsIgnoreCase(lang)) {
            return "Component/Function " + sym + " at " + linesStr + " in `" + fileName + "` has " + maxNesting + " nested branching levels. " +
                    "Flatten nested logic using early returns, optional chaining (`?.`), or sub-component composition.";
        }
        return "Refactor " + sym + " at " + linesStr + " in `" + fileName + "` (" + maxNesting + " nesting levels) using guard clauses to reduce nested control-flow depth.";
    }

    private String generateExceptionRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if ("Java".equalsIgnoreCase(lang) || "Kotlin".equalsIgnoreCase(lang)) {
            return "Exception caught at " + linesStr + " in " + sym + " in `" + fileName + "` is swallowed without action. " +
                    "Log the error using `@Slf4j log.error(\"Context failed in {}\", ex)` or wrap and rethrow a domain-specific RuntimeException.";
        } else if ("Python".equalsIgnoreCase(lang)) {
            return "Empty `except:` or `pass` block at " + linesStr + " in " + sym + " in `" + fileName + "` suppresses runtime failures. " +
                    "Catch explicit exception types (e.g. `except ValueError as err:`) and log via `logging.exception(err)`.";
        } else if ("TypeScript".equalsIgnoreCase(lang) || "JavaScript".equalsIgnoreCase(lang)) {
            return "Silent catch block at " + linesStr + " in " + sym + " in `" + fileName + "` hides errors. " +
                    "Log via `console.error('Operation failed:', err)` or propagate the error to global boundary state.";
        }
        return "Add explicit error logging or rethrow logic in " + sym + " at " + linesStr + " in `" + fileName + "` to prevent silent runtime failures.";
    }

    private String generateSecretRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        String secretVar = extractVarName(evidence);
        String varRef = secretVar != null ? "`" + secretVar + "`" : "credential";

        if ("Java".equalsIgnoreCase(lang) || "Kotlin".equalsIgnoreCase(lang)) {
            return "Hardcoded secret " + varRef + " detected in " + sym + " at " + linesStr + " in `" + fileName + "`. " +
                    "Revoke and rotate this secret immediately. Load the replacement via `System.getenv(\"" + (secretVar != null ? secretVar.toUpperCase() : "SECRET_KEY") + "\")` or Spring `@Value(\"${app.secret}\")`.";
        } else if ("Python".equalsIgnoreCase(lang)) {
            return "Hardcoded credential " + varRef + " exposed in " + sym + " at " + linesStr + " in `" + fileName + "`. " +
                    "Rotate the credential and replace inline value with `os.getenv(\"" + (secretVar != null ? secretVar.toUpperCase() : "SECRET_KEY") + "\")`.";
        } else if ("TypeScript".equalsIgnoreCase(lang) || "JavaScript".equalsIgnoreCase(lang) || lang.contains("React")) {
            return "Hardcoded key " + varRef + " found in " + sym + " at " + linesStr + " in `" + fileName + "`. " +
                    "Remove secret from repository, rotate key, and access via `process.env." + (secretVar != null ? secretVar.toUpperCase() : "API_KEY") + "` or runtime secret manager.";
        }
        return "Remove hardcoded " + varRef + " from " + sym + " at " + linesStr + " in `" + fileName + "`. Store secret in environment variables or a secret vault.";
    }

    private String generateSqlInjectionRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if ("Java".equalsIgnoreCase(lang)) {
            return "Dynamic SQL string concatenation in " + sym + " at " + linesStr + " in `" + fileName + "` permits SQL injection. " +
                    "Replace concatenation with a `PreparedStatement` using `?` placeholders (e.g. `stmt.setString(1, param)`).";
        } else if ("Python".equalsIgnoreCase(lang)) {
            return "Dynamic SQL string formatting in " + sym + " at " + linesStr + " in `" + fileName + "` poses SQL injection risk. " +
                    "Use parameterized query tuples (e.g., `cursor.execute(\"SELECT * FROM tbl WHERE id = %s\", (user_id,))`).";
        } else if ("TypeScript".equalsIgnoreCase(lang) || "JavaScript".equalsIgnoreCase(lang)) {
            return "Unsanitized SQL construction in " + sym + " at " + linesStr + " in `" + fileName + "` allows SQL injection. " +
                    "Use parameterized database queries (e.g. `db.query('SELECT * FROM tbl WHERE id = $1', [id])`) or ORM parameter binding.";
        }
        return "Replace raw SQL string concatenation in " + sym + " at " + linesStr + " in `" + fileName + "` with parameterized database binding.";
    }

    private String generateCommandInjectionRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if ("Java".equalsIgnoreCase(lang)) {
            return "OS command invocation in " + sym + " at " + linesStr + " in `" + fileName + "` opens shell injection vulnerabilities. " +
                    "Pass arguments as String array using `ProcessBuilder(List.of(\"cmd\", arg1, arg2))` without shell expansion.";
        } else if ("Python".equalsIgnoreCase(lang)) {
            return "System command execution in " + sym + " at " + linesStr + " in `" + fileName + "` exposes command injection. " +
                    "Use `subprocess.run([\"cmd\", arg1], shell=False)` to prevent shell string interpolation.";
        }
        return "Avoid invocation of OS shell commands in " + sym + " at " + linesStr + " in `" + fileName + "`. Validate inputs and execute binaries with explicit argument lists.";
    }

    private String generateDynamicEvalRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if (evidence != null && evidence.contains("dangerouslySetInnerHTML")) {
            return "Use of `dangerouslySetInnerHTML` in " + sym + " at " + linesStr + " in `" + fileName + "` invites Cross-Site Scripting (XSS). " +
                    "Sanitize HTML content using DOMPurify (`DOMPurify.sanitize(content)`) before rendering.";
        }
        return "Avoid dynamic code evaluation (`eval`/`exec`) in " + sym + " at " + linesStr + " in `" + fileName + "`. Replace with static AST parsing or standard JSON parsers.";
    }

    private String generateN1LoopRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        return "Query or network request executed inside loop at " + linesStr + " in " + sym + " in `" + fileName + "` creates an N+1 performance bottleneck. " +
                "Batch query arguments into a single SQL `WHERE id IN (...)` statement or REST bulk API call outside the loop scope.";
    }

    private String generateNullDereferenceRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if ("Java".equalsIgnoreCase(lang)) {
            return "Unchecked null dereference risk at " + linesStr + " in " + sym + " in `" + fileName + "`. " +
                    "Wrap query result in `Optional.ofNullable()` or insert explicit non-null checks (`if (obj != null)`) before accessing properties.";
        } else if ("TypeScript".equalsIgnoreCase(lang) || "JavaScript".equalsIgnoreCase(lang) || "Kotlin".equalsIgnoreCase(lang) || "Swift".equalsIgnoreCase(lang)) {
            return "Force unwrap or unchecked access at " + linesStr + " in " + sym + " in `" + fileName + "`. " +
                    "Use safe navigation operators (`?.`) or optional chaining (e.g. `obj?.property?.method()`).";
        } else if ("Rust".equalsIgnoreCase(lang)) {
            return "Unchecked `.unwrap()` call at " + linesStr + " in " + sym + " in `" + fileName + "` causes thread panic on None/Err. " +
                    "Use pattern matching (`if let Some(val) = option`) or the `?` operator for safe error handling.";
        }
        return "Add explicit non-null checks or safe call operators in " + sym + " at " + linesStr + " in `" + fileName + "` before property access.";
    }

    private String generateResourceLeakRecommendation(String sym, String linesStr, String lang, String evidence, String fileName) {
        if ("Java".equalsIgnoreCase(lang)) {
            return "Unclosed stream or database connection allocated in " + sym + " at " + linesStr + " in `" + fileName + "`. " +
                    "Refactor stream allocation into Java try-with-resources statement (`try (InputStream is = ...) { ... }`) for guaranteed closing.";
        }
        return "Ensure resource handles allocated at " + linesStr + " in " + sym + " in `" + fileName + "` are closed in a `finally` block or context manager.";
    }

    private String generateConfigRecommendation(String fileName, String title, String evidence, String linesStr) {
        if ("Dockerfile".equalsIgnoreCase(fileName)) {
            if (title != null && title.contains("latest")) {
                return "In `" + fileName + "` at " + linesStr + ", replace unpinned `:latest` image tag with an immutable digest or explicit version (e.g. `node:20.11-alpine`).";
            }
            return "In `" + fileName + "`, add an unprivileged user directive (`RUN useradd -m appuser && USER appuser`) to avoid running container processes as root.";
        }
        if ("package.json".equalsIgnoreCase(fileName)) {
            return "In `" + fileName + "` at " + linesStr + ", lock wildcard dependency range (`*` or `>=`) to an exact version or standard semver range (e.g. `^1.2.3`).";
        }
        if ("requirements.txt".equalsIgnoreCase(fileName)) {
            return "In `" + fileName + "` at " + linesStr + ", pin Python package requirement to an exact version (e.g., `package_name==2.1.0`) to avoid build breaks.";
        }
        return "Fix configuration issue in `" + fileName + "` at " + linesStr + " to enforce immutable and secure build specifications.";
    }

    private String extractVarName(String evidence) {
        if (evidence == null) return null;
        Matcher m = Pattern.compile("(?i)([a-zA-Z0-9_]+)\\s*[:=]\\s*[\"']").matcher(evidence);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String getFileName(String path) {
        if (path == null) return "file";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash == -1) return path;
        return path.substring(lastSlash + 1);
    }
}
