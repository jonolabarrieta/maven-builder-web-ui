package net.olaba.mvnbuilder.service;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Transient recovery plans; queue ownership and one-shot acceptance stay on the server. */
public final class BuildContinuationRegistry {
    public record Target(Long id, String directory) { }
    public record Plan(Long workspaceId, String runId, List<Target> pending, List<String> command,
                       String javaHome, List<String> skipped) {
        public Plan { pending=List.copyOf(pending); command=List.copyOf(command); skipped=List.copyOf(skipped); }
    }
    private record Entry(Plan plan, Instant created) { }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final Set<String> canceled = new HashSet<>();
    private final Clock clock;
    private final int limit;
    public BuildContinuationRegistry() { this(Clock.systemUTC(), 128); }
    BuildContinuationRegistry(Clock clock, int limit) { this.clock=clock; this.limit=limit; }
    public synchronized String register(Plan plan) {
        expire();
        if (canceled.contains(plan.runId())) return null;
        while (entries.size() >= limit) entries.remove(entries.keySet().iterator().next());
        String id=UUID.randomUUID().toString(); entries.put(id,new Entry(plan,clock.instant())); return id;
    }
    public synchronized Plan accept(Long workspaceId, String id, String mode, Consumer<Plan> validate) {
        if (mode==null || !Set.of("retry","skip").contains(mode)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose retry or skip.");
        expire(); Entry entry=entries.get(id);
        if (entry==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"This recovery action has expired or was already used. Start a fresh build.");
        if (!Objects.equals(workspaceId,entry.plan().workspaceId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Recovery action does not belong to this workspace.");
        validate.accept(entry.plan()); entries.remove(id); return entry.plan();
    }
    public synchronized void cancelAll() {
        for (Entry entry:entries.values()) canceled.add(entry.plan().runId());
        entries.clear();
    }
    public synchronized void cancelRun(String runId) {
        if (canceled.size() >= 256) canceled.clear();
        canceled.add(runId); entries.values().removeIf(entry->entry.plan().runId().equals(runId));
    }
    private void expire() { entries.values().removeIf(entry->entry.created().plus(Duration.ofHours(1)).isBefore(clock.instant())); }
}
