package com.foggyframework.bundle.event;

import com.foggyframework.bundle.SystemBundlesContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationContextEvent;

public class SystemBundlesContextRefreshedEvent extends ApplicationEvent {
    public SystemBundlesContextRefreshedEvent(SystemBundlesContext source) {
        super(source);
    }

    public final SystemBundlesContext getSystemBundlesContext() {
        return (SystemBundlesContext)this.getSource();
    }
}
