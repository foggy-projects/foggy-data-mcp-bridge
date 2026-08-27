package com.foggyframework.bundle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Getter
@AllArgsConstructor
@ToString
public class BundleResource {
    Bundle bundle;
    Resource resource;

    public InputStream getInputStream() {
        try {
            URL url;
            try {
                url = resource.getURL();
            } catch (IOException ignored) {
                return resource.getInputStream();
            }
            if ("jar".equalsIgnoreCase(url.getProtocol())) {
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                return connection.getInputStream();
            }
            return resource.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isFile() {
        return resource.isFile();
    }

    public File getFile() {
        try {
            return resource.getFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
