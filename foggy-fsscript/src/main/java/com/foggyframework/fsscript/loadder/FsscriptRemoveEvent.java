package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FsscriptRemoveEvent extends ApplicationEvent {

    private final boolean scopeKnown;
    private final Set<String> affectedNamespaces;
    private final Map<String, String> committedSourceRevisions;
    private final List<String> affectedResources;
    private final String sourceRevision;

    public FsscriptRemoveEvent(List<Fsscript> source) {
        this(source, false, Set.of(), Map.of(), List.of());
    }

    public FsscriptRemoveEvent(
            List<Fsscript> source,
            boolean scopeKnown,
            Collection<String> affectedNamespaces,
            Map<String, String> committedSourceRevisions,
            List<String> affectedResources
    ) {
        super(source);
        this.scopeKnown = scopeKnown;
        this.affectedNamespaces = affectedNamespaces == null
                ? Set.of()
                : Set.copyOf(affectedNamespaces);
        this.committedSourceRevisions = committedSourceRevisions == null
                ? Map.of()
                : Map.copyOf(committedSourceRevisions);
        this.affectedResources = affectedResources == null
                ? List.of()
                : List.copyOf(affectedResources);
        this.sourceRevision = this.committedSourceRevisions.size() == 1
                ? this.committedSourceRevisions.values().iterator().next()
                : "source-event:" + UUID.randomUUID();
    }

    public final List<Fsscript> getRemovedFsscripts() {
        return (List<Fsscript>)this.getSource();
    }

    public boolean isScopeKnown() {
        return scopeKnown;
    }

    public Set<String> getAffectedNamespaces() {
        return affectedNamespaces;
    }

    public Map<String, String> getCommittedSourceRevisions() {
        return committedSourceRevisions;
    }

    /** Event-level opaque marker; use the map for exact multi-namespace revisions. */
    public String getSourceRevision() {
        return sourceRevision;
    }

    public List<String> getAffectedResources() {
        return affectedResources;
    }

}
