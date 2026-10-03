package com.foggyframework.dataviewer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.security.*;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.model.api.QueryFacade;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;

@AutoConfiguration(after={DataViewerAutoConfiguration.class,DataViewerMemoryAutoConfiguration.class})
@ConditionalOnProperty(prefix="foggy.data-viewer.link-authorization",name="enabled",havingValue="true")
@EnableConfigurationProperties(DataViewerProperties.class)
public class ViewerAuthorizationAutoConfiguration {
    @Bean @ConditionalOnMissingBean(ViewerGrantStore.class)
    ViewerGrantStore viewerGrantStore(DataViewerProperties p,ObjectMapper mapper,ObjectProvider<MongoTemplate> mongo) {
        if ("memory".equals(p.getCache().getStore())) throw new IllegalArgumentException("Persistent viewer authorization requires SQLite or Mongo query contexts");
        if (p.getCache().getStorage()==DataViewerProperties.CacheProperties.Storage.MONGO)
            return new MongoViewerGrantStore(mongo.getObject(),mapper);
        return new SqliteViewerGrantStore(Path.of(p.getCache().getSqlitePath()),mapper);
    }
    @Bean @ConditionalOnMissingBean(ViewerGrantService.class)
    ViewerGrantService viewerGrantService(ViewerGrantStore store,ViewerIdentityProvider identities,
            QueryCacheService queries,@org.springframework.context.annotation.Lazy QueryFacade facade,DataViewerProperties p) {
        return new ViewerGrantService(store,identities,queries,facade,p.getLinkAuthorization(),Clock.systemUTC());
    }
    @Bean ViewerLinkController viewerLinkController(ViewerGrantService grants,DataViewerProperties p) {
        return new ViewerLinkController(grants,p);
    }
    @Bean FilterRegistrationBean<ViewerSessionFilter> viewerSessionFilter(ViewerGrantService grants,ObjectMapper mapper) {
        var registration=new FilterRegistrationBean<ViewerSessionFilter>();
        registration.setFilter(new ViewerSessionFilter(grants,mapper)); registration.addUrlPatterns("/data-viewer/api/*");
        registration.setOrder(-100); return registration;
    }
}
