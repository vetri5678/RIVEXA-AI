package ai.riskvision.graveyard.util;

import ai.riskvision.graveyard.exception.GitHubValidationException;
import java.util.regex.Pattern;

public class GitHubValidator {

    private static final Pattern OWNER_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,100}$");
    private static final Pattern REPO_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]]{1,100}$".replace("]]", "]"));
    private static final Pattern SHA_PATTERN = Pattern.compile("^[a-fA-F0-9]{4,40}$");

    public static void validateOwner(String owner) {
        preventSsrf(owner, "owner");
        if (owner == null || owner.trim().isEmpty() || !OWNER_PATTERN.matcher(owner.trim()).matches()) {
            throw new GitHubValidationException("Invalid GitHub owner parameter: " + owner);
        }
    }

    public static void validateRepo(String repo) {
        preventSsrf(repo, "repo");
        if (repo == null || repo.trim().isEmpty()) {
            throw new GitHubValidationException("Invalid GitHub repository parameter: " + repo);
        }
        String cleanRepo = repo.trim().replaceAll("\\.git$", "");
        if (cleanRepo.isEmpty() || !REPO_PATTERN.matcher(cleanRepo).matches()) {
            throw new GitHubValidationException("Invalid GitHub repository parameter: " + repo);
        }
    }

    public static void validateOwnerAndRepo(String owner, String repo) {
        validateOwner(owner);
        validateRepo(repo);
    }

    public static void validateBranch(String branch) {
        preventSsrf(branch, "branch");
        if (branch == null || branch.trim().isEmpty() || branch.contains("..") || branch.contains("~") || branch.contains("^")) {
            throw new GitHubValidationException("Invalid GitHub branch parameter: " + branch);
        }
    }

    public static void validateSha(String sha) {
        preventSsrf(sha, "sha");
        if (sha == null || !SHA_PATTERN.matcher(sha.trim()).matches()) {
            throw new GitHubValidationException("Invalid GitHub commit SHA parameter: " + sha);
        }
    }

    public static void validateNumber(Integer number, String name) {
        if (number == null || number <= 0) {
            throw new GitHubValidationException("Invalid " + name + " parameter: " + number);
        }
    }

    private static void preventSsrf(String value, String paramName) {
        if (value == null) return;
        String lower = value.toLowerCase();
        if (lower.contains("://") || lower.contains("..") || lower.contains("%") || lower.contains("@") || lower.contains("localhost") || lower.contains("127.0.0.1")) {
            throw new GitHubValidationException("Potential SSRF attack attempt blocked on parameter: " + paramName);
        }
    }
}
