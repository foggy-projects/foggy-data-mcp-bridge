package com.foggyframework.fsscript.loadder;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class FsscriptRemoveEvent extends ApplicationEvent {


    public FsscriptRemoveEvent(List<Fsscript> source) {
        super(source);
    }

    public final List<Fsscript> getRemovedFsscripts() {
        return (List<Fsscript>)this.getSource();
    }

}
