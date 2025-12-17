package com.foggyframework.bundle.loader;

import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.bundle.BundleImpl;
import com.foggyframework.bundle.SystemBundlesContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
@Slf4j
public abstract class BundleLoader<T extends BundleDefinition> {

    protected List<T> bundleDefList;

    public BundleLoader(List<T> bundleDefList) {
        this.bundleDefList = bundleDefList;
    }

    public List<T> getBundleDefList() {
        return bundleDefList;
    }

    public abstract void load(SystemBundlesContext systemBundlesContext);

    /**
     * @param bundle
     * @param templatePath g.e
     */
    boolean tryUnzip(BundleImpl bundle, String templatePath) {

        ApplicationContext appCtx = bundle.getSystemBundlesContext().getApplicationContext();

        try {

            //好了，将classpath中的资源,copy到WebRoot下
            String path = bundle.getBasePath();
            Resource webRootRes = appCtx.getResource(path);
            //rebapckOld(appCtx,bundle);

            //弄到foggy/templates的根路径
            //gitPath的值看上去类似jar:file:/Users/fengjianguang/.m2/repository/org/foggysource/foggy-spring-def-test/2.0.d-SNAPSHOT/foggy-spring-def-test-2.0.d-SNAPSHOT.jar!/foggy/templates/foggy-spring-def-test-git.properties
//            String gitPath = classpathGit.getURL().toString();
//            String templatesPath = gitPath.substring(0, gitPath.lastIndexOf("/"));

            if (!webRootRes.exists()) {
                Resource rootRes = appCtx.getResource("/");//先弄到根目录
                File rootFile = rootRes.getFile();
                File file = new File(rootFile, path);
                if (!file.mkdirs()) {
                    throw new RuntimeException(String.format("创建文件夹【%s】失败！", file));
                }
            }
            copyJarResourceFile(templatePath, webRootRes.getFile().getAbsolutePath(), "/foggy/templates/");

            return true;
        } catch (IOException e) {
            log.error("无法解压: "+e.getMessage());
            //异常，不处理
            if(log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }


//    private void delBackup(ApplicationContext appCtx) {
//        try {
//            Resource webRootRes = appCtx.getResource(this.base);
//            File tmp = new File(webRootRes.getFile().getParentFile(), this.getBundleName() + ".backup");
//            FileUtils.deleteFolder(tmp);
//        } catch (Throwable e) {
//            //异常，不处理
//            e.printStackTrace();
//            return;
//        }
//    }

//     void rebapckOld(ApplicationContext appCtx,BundleImpl bundle) {
//        try {
//            Resource webRootRes = appCtx.getResource("/");
//            File baseFile = new File(webRootRes.getFile(), bundle.getBastPath());
//            File tmp = new File(webRootRes.getFile(), "WEB-INF/" + bundle.getName() + ".backup");
//
//            if (tmp.exists()) {
//                FileUtils.deleteFolder(tmp);
//            }
//            if (baseFile.exists()) {
//                baseFile.renameTo(tmp);
//            }
//
//        } catch (Throwable e) {
//            //异常，不处理
//            e.printStackTrace();
//            return;
//        }
//    }

    public static boolean copyJarResourceFile(String fileDir, String desDir, String rootPath) {

        String pathWithOutJar = fileDir.substring("jar:".length());
        File dir = new File(desDir);

        dir.mkdirs();

        //获取容器资源解析器
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
//            CopyOption opt = CopyOption
            //获取所有匹配的文件
//JAR entry / not found in /Users/fengjianguang/.m2/repository/com/foggysource/foggy-framework-bundle-test-client/3.0.d-SNAPSHOT/foggy-framework-bundle-test-client-3.0.d-SNAPSHOT.jar

            Resource[] resources = resolver.getResources(fileDir + "/**");
            for (Resource src : resources) {
                try {
                    //获得文件流，因为在jar文件中，不能直接通过文件资源路径拿到文件，但是可以在jar包中拿到文件流
//                    if (!(src instanceof ClassPathResource)) {
//                        logger.warn(String.format("【%s】不是ClassPathResource，退出", src));
//                        return false;
//                    }
//                    UrlResource pathRes = (UrlResource) src;
                    String path = src.getURL().getPath();
//                    path = path.replace("jar:", "");
                    path = path.replace(pathWithOutJar, "");
                    String abPath = path.replace(rootPath, "");
                    String targetFilePath = dir.getPath() + File.separator + abPath;

                    if (abPath.endsWith("/")) {
                        //目录
                        new File(targetFilePath).mkdirs();
                        continue;
                    }
                    //                    String filePath =
                    InputStream stream = src.getInputStream();

                    File ttfFile = new File(targetFilePath);
                    Files.copy(stream, ttfFile.toPath(), StandardCopyOption.REPLACE_EXISTING);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

}
