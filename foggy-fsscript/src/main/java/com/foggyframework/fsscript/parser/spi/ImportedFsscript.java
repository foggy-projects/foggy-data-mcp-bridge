package com.foggyframework.fsscript.parser.spi;

import lombok.Data;

import java.util.Map;

@Data
public class ImportedFsscript {

    FsscriptClosure closure;
    Fsscript fScript;

    public ImportedFsscript(FsscriptClosure closure, Fsscript fScript) {
        this.closure = closure;
        this.fScript = fScript;
    }

    public Map<String, Object> getExportMap() {
        return (Map<String, Object>) closure.getVar(FsscriptClosure.EXPORT_MAP_KEY);
    }
}
