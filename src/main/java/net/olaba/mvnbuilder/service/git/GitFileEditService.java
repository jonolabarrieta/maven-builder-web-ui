package net.olaba.mvnbuilder.service.git;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/** Reads and writes working-tree text of changed files; the index and history are never touched. */
@Service
public class GitFileEditService {
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private final GitCommandRunner git;
    private final GitChangeService changes;
    @Value("${workspace.git.file-limit-bytes:1048576}") private int fileLimit = 1048576;
    public GitFileEditService(GitCommandRunner git, GitChangeService changes) { this.git = git; this.changes = changes; }
    private record Source(byte[] bytes, String reason) { }
    /** The editor receives LF line breaks; the original line-ending style and byte-order mark are restored on save. */
    public FileContent read(Resolved repository, String path) {
        Source source = source(repository, path);
        if (source.reason() != null) return new FileContent(path, null, null, null, false, false, false, source.reason());
        byte[] bytes = source.bytes(); boolean bom = startsWithBom(bytes);
        String text = new String(bytes, bom ? 3 : 0, bytes.length - (bom ? 3 : 0), StandardCharsets.UTF_8);
        boolean crlf = text.contains("\r\n");
        String normalized = text.replace("\r\n", "\n");
        if (crlf && normalized.indexOf('\n') >= 0 && countCrlf(text) != count(normalized, '\n'))
            return new FileContent(path, null, null, null, false, false, false, "This file mixes CRLF and LF line endings. Edit it in your editor so they are kept exactly.");
        if (normalized.indexOf('\r') >= 0)
            return new FileContent(path, null, null, null, false, false, false, "This file contains carriage returns outside line endings. Edit it in your editor.");
        return new FileContent(path, normalized, GitChangeService.fileHash(target(repository, path)), crlf ? "crlf" : "lf", bom, normalized.endsWith("\n"), true, null);
    }
    /** Writes atomically only while the file still has the content the editor was opened with. */
    public FileContent write(Resolved repository, String path, String content, String hash) {
        if (content == null || hash == null) throw new GitProblem(400, "Send the edited content and the identity it was opened with.");
        return GitMutationCoordinator.mutate(repository.commonDir(), () -> {
            FileContent current = read(repository, path);
            if (!current.editable()) throw new GitProblem(422, current.reason());
            if (!current.hash().equals(hash)) throw new GitProblem(409, "The file changed on disk after it was opened. Reload it or copy your edits before saving again.");
            String text = content.replace("\r\n", "\n");
            if (current.eol().equals("crlf")) text = text.replace("\n", "\r\n");
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = current.bom() ? concat(BOM, encoded) : encoded;
            if (bytes.length > fileLimit) throw new GitProblem(413, "The edited file exceeds workspace.git.file-limit-bytes.");
            Path file = target(repository, path);
            Path temporary = file.resolveSibling("." + file.getFileName() + ".mvnbuilder-" + UUID.randomUUID());
            try {
                Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                try { Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)); }
                catch (UnsupportedOperationException ignored) { /* Non-POSIX file systems keep default permissions. */ }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) { throw new GitProblem(422, "This file system cannot replace the file atomically; edit it in your editor.");
            } catch (IOException e) { throw new GitProblem(422, "Cannot save " + path + ": " + e.getMessage());
            } finally { try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
            return read(repository, path);
        });
    }
    private Path target(Resolved repository, String path) { return WorkspaceGitDiscoveryService.safePath(repository, path); }
    /** Editing is offered for regular UTF-8 text files listed under Changes that Git stores without content conversion. */
    private Source source(Resolved repository, String path) {
        Path file = target(repository, path);
        FileChange entry;
        try { entry = GitChangeService.find(changes.status(repository), path, "changes"); }
        catch (GitProblem e) { return new Source(null, "Only files listed under Changes can be edited here."); }
        if (Set.of("conflict", "gitlink", "symlink", "untracked-dir").contains(entry.kind())) return new Source(null, "Conflicts, submodules, links and folders cannot be edited here.");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return new Source(null, "The file is missing or is not a regular file.");
        byte[] bytes;
        try {
            if (Files.size(file) > fileLimit) return new Source(null, "The file exceeds workspace.git.file-limit-bytes.");
            bytes = Files.readAllBytes(file);
        } catch (IOException e) { return new Source(null, "Cannot read the file: " + e.getMessage()); }
        if (!text(bytes)) return new Source(null, "Binary or non-UTF-8 files cannot be edited here.");
        String[] attributes = git.run(repository.root(), "check-attr", "-z", "filter", "working-tree-encoding", "--", path).require().text().split("\0", -1);
        for (int i = 0; i + 2 < attributes.length; i += 3) {
            String value = attributes[i + 2];
            if (!value.equals("unspecified") && !value.equals("unset")) return new Source(null, "This file uses Git content filters or working-tree encoding; edit it in your editor.");
        }
        return new Source(bytes, null);
    }
    private static boolean text(byte[] bytes) {
        for (byte b : bytes) if (b == 0) return false;
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); return true; }
        catch (CharacterCodingException e) { return false; }
    }
    private static boolean startsWithBom(byte[] bytes) { return bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]; }
    private static int count(String text, char character) { int count = 0; for (int i = 0; i < text.length(); i++) if (text.charAt(i) == character) count++; return count; }
    private static int countCrlf(String text) { int count = 0; for (int i = text.indexOf("\r\n"); i >= 0; i = text.indexOf("\r\n", i + 2)) count++; return count; }
    private static byte[] concat(byte[] first, byte[] second) { byte[] result = Arrays.copyOf(first, first.length + second.length); System.arraycopy(second, 0, result, first.length, second.length); return result; }
}
