package ai.riskvision.graveyard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing GitHub repository URLs into owner/repo components.
 * Supports both HTTPS and SSH URL formats.
 *
 * Examples:
 *   https://github.com/owner/repo        → owner="owner", repo="repo"
 *   https://github.com/owner/repo.git    → owner="owner", repo="repo"
 *   git@github.com:owner/repo.git        → owner="owner", repo="repo"
 *   https://github.com/owner/repo/tree/main → owner="owner", repo="repo"
 */
public final class GitHubUrlParser {

    // HTTPS: https://github.com/owner/repo  (optional trailing path, optional .git)
    private static final Pattern HTTPS_PATTERN =
            Pattern.compile("https?://github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?(?:/.*)?$");

    // SSH: git@github.com:owner/repo.git
    private static final Pattern SSH_PATTERN =
            Pattern.compile("git@github\\.com:([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?$");

    private GitHubUrlParser() {
        // utility class — no instantiation
    }

    /**
     * Parsed result carrying owner and repository name.
     */
    public static final class ParsedRepo {
        private final String owner;
        private final String repo;

        private ParsedRepo(String owner, String repo) {
            this.owner = owner;
            this.repo = repo;
        }

        public String getOwner() { return owner; }
        public String getRepo()  { return repo; }

        @Override
        public String toString() {
            return owner + "/" + repo;
        }
    }

    /**
     * Parses a GitHub repository URL and returns a {@link ParsedRepo}.
     *
     * @param url the raw GitHub URL string
     * @return a {@link ParsedRepo} with owner and repo populated
     * @throws IllegalArgumentException if the URL cannot be parsed as a valid GitHub repository URL
     */
    public static ParsedRepo parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub URL must not be blank. Example: https://github.com/owner/repo");
        }

        String trimmed = url.trim();

        Matcher httpsMatcher = HTTPS_PATTERN.matcher(trimmed);
        if (httpsMatcher.find()) {
            String owner = httpsMatcher.group(1);
            String repo  = httpsMatcher.group(2);
            validateComponents(owner, repo, trimmed);
            return new ParsedRepo(owner, repo);
        }

        Matcher sshMatcher = SSH_PATTERN.matcher(trimmed);
        if (sshMatcher.find()) {
            String owner = sshMatcher.group(1);
            String repo  = sshMatcher.group(2);
            validateComponents(owner, repo, trimmed);
            return new ParsedRepo(owner, repo);
        }

        throw new IllegalArgumentException(
                "Invalid GitHub URL: \"" + trimmed + "\". " +
                "Expected format: https://github.com/{owner}/{repo} " +
                "or git@github.com:{owner}/{repo}.git");
    }

    /**
     * Returns {@code true} if the given URL is a recognisable GitHub repository URL.
     */
    public static boolean isValidGitHubUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        return HTTPS_PATTERN.matcher(trimmed).find() || SSH_PATTERN.matcher(trimmed).find();
    }

    // ── private helpers ──────────────────────────────────────────────────────────

    private static void validateComponents(String owner, String repo, String rawUrl) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Could not extract a valid owner from URL: " + rawUrl);
        }
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("Could not extract a valid repository name from URL: " + rawUrl);
        }
    }
}
