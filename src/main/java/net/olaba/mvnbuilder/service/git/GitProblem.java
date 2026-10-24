package net.olaba.mvnbuilder.service.git;

/** Expected repository failures have an HTTP outcome and a user-readable recovery message. */
public class GitProblem extends RuntimeException {
    private final int status;
    public GitProblem(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static GitProblem stale() { return new GitProblem(409, "Repository contents changed. Refresh the comparison and select the changes again."); }
}
