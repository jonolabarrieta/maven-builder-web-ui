package net.olaba.mvnbuilder.service.git;

/** Expected repository failures have an HTTP outcome and a user-readable recovery message. */
public class GitProblem extends RuntimeException {
    private final int status; private final String code;
    public GitProblem(int status, String message) { this(status, message, null); }
    public GitProblem(int status, String message, String code) { super(message); this.status = status; this.code = code; }
    public int status() { return status; }
    /** Stable machine-readable reason for clients, or {@code null}. */
    public String code() { return code; }
    /** The server has no record of the identity, for example after a restart; a new discovery may restore it. */
    public static GitProblem unknownRepository() { return new GitProblem(404, "Repository is not available in this workspace. Refresh repositories.", "unknown-repository"); }
    public static GitProblem stale() { return new GitProblem(409, "Repository contents changed. Refresh the comparison and select the changes again."); }
}
