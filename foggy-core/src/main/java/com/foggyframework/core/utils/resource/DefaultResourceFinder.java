package com.foggyframework.core.utils.resource;

import com.foggyframework.core.ex.RX;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author fengjianguang
 */
@Slf4j
public class DefaultResourceFinder implements ResourceFinder {
    ApplicationContext appCtx;
    String path;

    /**
     * @param appCtx
     * @param path 需要查找的文件名
     */
    public DefaultResourceFinder(ApplicationContext appCtx, String path) {
        this.appCtx = appCtx;

        if (!path.endsWith("/")) {
            path = path + "/";
        }
        this.path = path;
    }
    public DefaultResourceFinder(ApplicationContext appCtx) {
        this.appCtx = appCtx;
        this.path = "/";
    }
    /**
     * 当多于一个时，会抛出异常
     *
     * @return
     */
    @Nullable
    public Resource findOne(String name) {

        try {
            String f = path + "**/" + name;
            if(log.isInfoEnabled()){
                log.info("findOne: "+f,",name: "+name);
            }
            Resource[] resources = appCtx.getResources(f);
            if (resources.length == 0) {
                return null;
            }
            if(resources.length>1){
                throw MULTI_FIND_ERROR.throwErrorWithFormatArgs(name, Arrays.toString(resources));
            }
            return resources[0];
        } catch (IOException e) {
            throw RX.throwB(e.getMessage(), null, e);
        }

    }

    @Override
    public Resource findByResource(Resource resource, String relativePath) {
        try {
            Resource res =  resource.createRelative(relativePath);

            return res;
        } catch (IOException e) {
            throw RX.throwB(e.getMessage(),null,e);
        }
    }

}
