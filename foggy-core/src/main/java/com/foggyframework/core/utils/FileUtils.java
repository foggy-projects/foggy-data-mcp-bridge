/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils;

import com.foggyframework.core.ex.RX;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.*;


public final class FileUtils {
    /**
     * 不显示隐藏文件夹
     *
     * @author seasoul
     */
    static class FolderFilter implements FileFilter {

        @Override
        public boolean accept(final File pathname) {
            return pathname.isDirectory() && !pathname.isHidden();
        }
    }

    public static byte[] InputStream2Bytes(FileInputStream is) {

        byte[] byt;
        try {
            byt = new byte[is.available()];
            is.read(byt);
            return byt;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static byte[] input2byte(InputStream inStream) {
        ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
        byte[] buff = new byte[100];
        int rc = 0;
        try {
            while ((rc = inStream.read(buff, 0, 100)) > 0) {
                swapStream.write(buff, 0, rc);
            }
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
        byte[] in2b = swapStream.toByteArray();
        return in2b;
    }

    static class XmlFileFilter implements FileFilter {

        @Override
        public boolean accept(final File pathname) {
            return pathname.getName().matches(".*\\.xml");
        }
    }

    public static class ZipCompressor {
        static final int BUFFER = 8192;

        private final File zipFile;

        public ZipCompressor(File zipFile) {
            this.zipFile = zipFile;
        }

        public ZipCompressor(String pathName) {
            zipFile = new File(pathName);
        }

        public void compress(File file) {
            if (!file.exists())
                throw new RuntimeException(file.getPath() + "不存在！");
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(zipFile);
                CheckedOutputStream cos = new CheckedOutputStream(fileOutputStream, new CRC32());
                ZipOutputStream out = new ZipOutputStream(cos);
                String basedir = "";
                // if(file.is)
                if (file.isDirectory()) {
                    File[] ll = file.listFiles();
                    if (ll != null) {
                        for (File f : ll) {
                            compress(f, out, basedir);
                        }
                    }
                } else {
                    compressFile(file, out, basedir);
                }
                out.close();
            } catch (Exception e) {
                throw ErrorUtils.toRuntimeException(e);
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void compress(File file, ZipOutputStream out, String basedir) {
            /* 判断是目录还是文件 */
            if (file.isDirectory()) {
                System.out.println("压缩：" + basedir + file.getName());
                this.compressDirectory(file, out, basedir);
            } else {
                System.out.println("压缩：" + basedir + file.getName());
                this.compressFile(file, out, basedir);
            }
        }

        /**
         * 压缩一个目录
         */
        private void compressDirectory(File dir, ZipOutputStream out, String basedir) {
            if (!dir.exists())
                return;

            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    /* 递归 */
                    compress(files[i], out, basedir + dir.getName() + "/");
                }
            }
        }

        /**
         * 压缩一个文件
         */
        private void compressFile(File file, ZipOutputStream out, String basedir) {
            if (!file.exists()) {
                return;
            }
            try {
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                ZipEntry entry = new ZipEntry(basedir + file.getName());
                out.putNextEntry(entry);
                int count;
                byte[] data = new byte[BUFFER];
                while ((count = bis.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                bis.close();
            } catch (Exception e) {
                throw ErrorUtils.toRuntimeException(e);
            }
        }
    }

    public static FolderFilter folderFilter = new FolderFilter();

    public static XmlFileFilter xmlFileFilter = new XmlFileFilter();

    private static char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
            'f'};

    public static final int BUFSIZE = 1024 * 8;

    private static void appendHexPair(byte bt, StringBuffer stringbuffer) {
        char c0 = hexDigits[(bt & 0xf0) >> 4];
        char c1 = hexDigits[bt & 0xf];
        stringbuffer.append(c0);
        stringbuffer.append(c1);
    }

    public static void assertExists(File file) {
        if (!file.exists()) {
            throw new RuntimeException("文件:[" + file.getPath() + "]不存在");
        }
    }

    private static String bufferToHex(byte[] bytes) {
        return bufferToHex(bytes, 0, bytes.length);
    }

    private static String bufferToHex(byte[] bytes, int m, int n) {
        StringBuffer stringbuffer = new StringBuffer(2 * n);
        int k = m + n;
        for (int l = m; l < k; l++) {
            appendHexPair(bytes[l], stringbuffer);
        }
        return stringbuffer.toString();
    }

    public static void clearChild(File parent) {
        File[] ff = parent.listFiles();
        if (ff != null) {
            for (File f : ff) {
                delete(f);
            }
        }
    }

    /**
     * 如果src为文件夹，则压缩src下的子文件
     *
     * @param src
     * @param zipFile
     */
    public static void compressFile(File src, File zipFile) {
        new ZipCompressor(zipFile).compress(src);
    }

    public static void copy(File src, File dst) {
        if (src.isDirectory()) {
            // 源是目录，因此目录也必须是目录
            if (src.isDirectory()) {
                copyDict(src, dst);
            } else {
                throw new RuntimeException("ooxxfile");
            }
        } else {
            copyFile(src, dst);
        }
    }

    public static void copy(URL url, File dst) {
        try {
            save(dst, url.openStream());
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
    }

    public static File copy2TmpFile(URL url) {
        try {
            File dst = File.createTempFile(UuidUtils.newUuid(), "jpg");
            save(dst, url.openStream());
            return dst;
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
    }

    public static void copyUsingSessionId(URL url, String sessionId, File dst) {
        HttpURLConnection con = null;
        InputStream in = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Cookie", sessionId);
            in = con.getInputStream();
            save(dst, in);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            if (con != null) {
                con.disconnect();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    throw ErrorUtils.toRuntimeException(e);
                }
            }
        }

    }

    // 处理目录
    public static void copyDict(File src, File dst) {
        File[] file = src.listFiles();// 得到源文件下的文件项目
        if (!dst.exists()) {
            dst.mkdirs();
        }
        if (file != null) {
            for (int i = 0; i < file.length; i++) {
                if (file[i].isFile()) {// 判断是文件
                    File sourceDemo = new File(src.getAbsolutePath() + "/" + file[i].getName());
                    File destDemo = new File(dst.getAbsolutePath() + "/" + file[i].getName());
                    copyFile(sourceDemo, destDemo);
                }
                if (file[i].isDirectory()) {// 判断是文件夹
                    File sourceDemo = new File(src.getAbsolutePath() + "/" + file[i].getName());
                    File destDemo = new File(dst.getAbsolutePath() + "/" + file[i].getName());
                    destDemo.mkdir();// 建立文件夹
                    copyDict(sourceDemo, destDemo);
                }
            } // end copyDict
        }
    }

    /**
     * 将文件source复制到target
     *
     * @param src
     * @param dst
     */
    public static void copyFile(File src, File dst) {
        FileInputStream in = null;
        FileOutputStream out = null;
        if (src == null || !src.exists()) {
            // 源文件不存在，直接退出
            return;
        }
        if (src.isHidden()) {
            // 不复制隐藏文件
            return;
        }
        if (!dst.exists()) {
            try {
                dst.createNewFile();
            } catch (IOException e) {
                throw ErrorUtils.toRuntimeException(e);
            }
        }
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            if (in == null || out == null) {
                System.err.println("copy [" + src + "] to [" + dst + "] 失败..");
            } else {
                FileChannel srcChannel = in.getChannel();
                FileChannel dstChannel = out.getChannel();
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
                srcChannel.close();
                dstChannel.close();
            }
        } catch (FileNotFoundException e) {
            throw ErrorUtils.toRuntimeException(e);
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            try {
                in.close();
                out.close();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Deprecated
    public static File createTempDirectory(String name) {
        String sysTemp = System.getProperty("java.io.tmpdir");
        File dir = new File(sysTemp, name + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    public static File createTempDirectoryFile(String name) {
        String sysTemp = System.getProperty("java.io.tmpdir");
        File dir = new File(sysTemp, name + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    public static String createTempDirectoryPath(String name) {
        String sysTemp = System.getProperty("java.io.tmpdir");
        File dir = new File(sysTemp, name + System.currentTimeMillis());
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    /**
     * 删除给定的目录下的所有目录及文件
     *
     * @param file
     */
    public static void delete(File file) {
        if (file.isFile()) {
            deleteFile(file);
        } else {
            deleteFolder(file);
        }
    }

    public static void deleteFile(File file) {
        if (file != null) {
            file.delete();
        }
    }

    public static void deleteFolder(File folder) {
        File[] ff = folder.listFiles();
        if (ff != null) {

            for (File f : ff) {
                if (f.isFile()) {
                    deleteFile(f);
                } else {
                    deleteFolder(f);
                }
            }
        }
        folder.delete();
    }

    public static URL extractJarFileURL(String jarFile, String pathInJarFile) throws MalformedURLException {
        return new URL("jar:file:" + jarFile + "!/" + pathInJarFile);
        // return new URL("jar:file:/d:/etell_backups/ooooo.zip!/.meta");
    }

    public static File fileFileByName(File parent, final String name) {
        File[] fs = parent.listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.getName().equalsIgnoreCase(name);
            }
        });
        if (fs == null) {
            return null;
        }
        return fs.length > 0 ? fs[0] : null;
    }

    public static File findFileByRegular(File parent, final String pattern) {

        File[] fs = parent.listFiles(file -> Pattern.matches(pattern, file.getName()));
        if (fs == null) {
            return null;
        }
        return fs.length > 0 ? fs[0] : null;
    }

    public static File findFileByRegular(String parent, final String pattern) {

        return findFileByRegular(new File(parent), pattern);
    }

    public static File findFolder(final File parent, final String name, final boolean createIfNotFound) {
        File file = null;
        File[] ff = parent.listFiles(FileUtils.folderFilter);
        if (ff != null) {
            for (final File f : ff) {
                if (f.getName().compareTo(name) == 0) {
                    file = f;
                    break;
                }
            }
        }
        if (file == null) {
            file = new File(parent.getPath() + "\\" + name);
            file.mkdir();
        }
        return file;
    }

    public static File findFolderDeep(final String parent, final String name, final int deep) {
        return findFolderDeep(new File(parent), name, deep);
    }

    public static File findFolderDeep(final File parent, final String name, final int deep) {
        File[] ff = parent.listFiles(FileUtils.folderFilter);
        if (ff != null) {
            for (final File f : ff) {
                if (f.getName().compareTo(name) == 0) {
                    return f;
                }

            }
        }
        //说明没有找到
        if (deep > 0) {
            if (ff != null) {
                for (final File f : ff) {
                    File deepFind = findFolderDeep(f, name, deep - 1);
                    if (deepFind != null) {
                        return deepFind;
                    }
                }
            }

        }

        return null;
    }

    /**
     * 计算文件的MD5，重载方法
     *
     * @param file 文件对象
     * @return
     * @throws IOException
     */
    public static String getFileMD5String(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        FileChannel ch = in.getChannel();
        MappedByteBuffer byteBuffer = ch.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(byteBuffer);
            return bufferToHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            System.err.println(FileUtils.class.getName() + "初始化失败，MessageDigest不支持MD5Util.");
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                in.close();
            }
        }

    }

    /**
     * 计算文件的MD5
     *
     * @param fileName 文件的绝对路径
     * @return
     * @throws IOException
     */
    public static String getFileMD5String(String fileName) throws IOException {
        File f = new File(fileName);
        return getFileMD5String(f);
    }

    public static String getFileNameWithOutSuffix(File file) {
        return file.getName().substring(0, file.getName().lastIndexOf("."));
    }

    public static String getNameWithOutSuffix(String name) {
        if (name == null) {
            return null;
        }
        return name.substring(0, name.lastIndexOf("."));
    }

    public static String getMD5String(InputStream in) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int numRead = 0;
            while ((numRead = in.read(buffer)) > 0) {
                messageDigest.update(buffer, 0, numRead);
            }
            return bufferToHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            System.err.println(FileUtils.class.getName() + "初始化失败，MessageDigest不支持MD5Util.");
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 获取不带后缀名的文件名
     *
     * @return
     */
    public static String getNameWithOutPref(String filename) {
        // String name = file.getName();
        int x = filename.lastIndexOf(".");
        if (x > 0) {
            filename = filename.substring(0, x);
        }
        return filename;
    }

    public static File[] listChildFileByExtName(File folder, boolean deep, final String... extName) {
        if (deep) {
            final List<File> results = new ArrayList<File>();

            listChildFileByExtName1(folder, results, extName);
            return results.toArray(new File[0]);
        } else {
            return listChildFileByExtName(folder, extName[0]);
        }
    }

    public static File[] listChildFileByExtName(File folder, final String extName) {
        return folder.listFiles(new FileFilter() {

            @Override
            public boolean accept(File arg0) {
                return arg0.getName().endsWith(extName);
            }
        });
    }

    private static void listChildFileByExtName1(File parent, List<File> results, final String... extName) {
        if (parent == null) {
            return;
        }
        File[] ff = parent.listFiles();
        if (ff != null) {
            for (File f : ff) {
                if (f.isFile()) {
                    // 是文件
                    X:
                    for (String n : extName) {
                        if (f.getName().endsWith(n)) {
                            results.add(f);
                            break;
                        }
                    }
                } else {
                    // 文件夹
                    listChildFileByExtName1(f, results, extName);
                }
            }
        }
    }

    public static List<File> listChildren(File parentFile, boolean deep, boolean containParentFile) {
        List<File> result = new ArrayList<File>();
        if (containParentFile)
            result.add(parentFile);
        return listChildren(result, parentFile, deep);
    }

    private static List<File> listChildren(List<File> result, File parentFile, boolean deep) {

        if (parentFile.isDirectory()) {
            File[] ff = parentFile.listFiles();
            if (ff != null) {
                for (File f : ff) {
                    if (deep) {
                        listChildren(result, f, deep);
                    }
                    result.add(f);
                }
            }
        }
        return result;
    }

    /**
     * 不显示隐藏文件夹
     *
     * @param folder
     * @return
     */
    public static File[] listChildrenFolder(final File folder) {
        return folder.listFiles(FileUtils.folderFilter);
    }

    public static File[] listChildrenXmlFile(final File folder) {
        return folder.listFiles(FileUtils.xmlFileFilter);
    }

    public static void main(String[] args) throws IOException {
        // String fileName =
        // "F:\\cwow\\WoW-3.1.3-zhCN-TBC-Installer\\DirectX\\ManagedDX.CAB";
        // long start = FoggyRuntime.currentTimeMillis();
        // // Systemx.out.println(getFileMD5String(fileName));
        // long end = FoggyRuntime.currentTimeMillis();
        // // Systemx.out.println("Consume " + (end - start) + "ms");
        // String s =
        // "http://10.89.165.235:7198/process/BIDataService?method=GetJsonDataBySql&sql=SELECT+row_number%28%29+over%28order+by+time_start%29+LINE_NUM%2C+TIME_START+TEST_DATE%2C+TO_CHAR%28TIME_START%2C%27YYYY-MM-DD+HH%3AMM%27%29+TIME_START%2C+TO_CHAR%28TIME_END%2C%27YYYY-MM-DD+HH%3AMM%27%29+TIME_END%2C+ACTIVITY_PHASE%2C+WORKCONTENT%2C+SEQUENCE_NO%2C+WELL_ID+FROM+V_OILTEST_DAILY+T+WHERE+T.WELL_ID+%3D+%270000000008%27+AND+WORKCONTENT+LIKE+%27%25%22%25%27+";
        // List<String> x = readLines(new URL(s), "UTF-8");
        // // Systemx.out.println(x);
    }

    public static void mergeFiles(File outFile, List<File> files, boolean withLine) {
        FileChannel outChannel = null;
        // out.println("Merge " + Arrays.toString(files) + " into " + outFile);
        byte l = '\n';// .getBytes();
        ByteBuffer line = ByteBuffer.allocate(8);

        try {
            outChannel = new FileOutputStream(outFile).getChannel();
            // if (append) {
            // outChannel.position(outChannel.size());
            // }
            for (File f : files) {

                FileChannel fc = new FileInputStream(f).getChannel();
                ByteBuffer bb = ByteBuffer.allocate(BUFSIZE);
                while (fc.read(bb) != -1) {
                    bb.flip();
                    outChannel.write(bb);
                    bb.clear();
                }
                if (withLine) {
                    line.put(l);
                    line.flip();
                    outChannel.write(line);
                    line.clear();
                }
                fc.close();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * 该接口不会返回空
     *
     * @param file
     * @param encoding
     * @return
     */
    public static List<String> readLines(File file, String encoding) {
        FileInputStream is;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
        return readLines(is, encoding);
    }

    /**
     * 该接口不会返回空
     *
     * @param is
     * @param encoding
     * @return
     */
    @SuppressWarnings("unchecked")
    public static List<String> readLines(InputStream is, String encoding) {
        try {
            InputStreamReader read = null;
            if (StringUtils.isEmpty(encoding)) {
                read = new InputStreamReader(is);
            } else {
                read = new InputStreamReader(is, encoding);
            }

            List<String> xx = new ArrayList<String>();
            BufferedReader bufferedReader = new BufferedReader(read);
            String lineTXT = null;
            while ((lineTXT = bufferedReader.readLine()) != null) {
                xx.add(lineTXT);
            }
            read.close();
            return xx;
        } catch (Exception e) {
            System.out.println("读取文件内容操作出错");
            e.printStackTrace();
            return Collections.EMPTY_LIST;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
//	public static List<String> readLines(OutputStream is, String encoding) {
//		try {
//			InputStreamReader read = null;
//			if (StringUtils.isEmpty(encoding)) {
//				read = new OutputStreamReader(is);
//			} else {
//				read = new InputStreamReader(is, encoding);
//			}
//
//			List<String> xx = new ArrayList<String>();
//			BufferedReader bufferedReader = new BufferedReader(read);
//			String lineTXT = null;
//			while ((lineTXT = bufferedReader.readLine()) != null) {
//				xx.add(lineTXT);
//			}
//			read.close();
//			return xx;
//		} catch (Exception e) {
//			System.out.println("读取文件内容操作出错");
//			e.printStackTrace();
//			return Collections.EMPTY_LIST;
//		} finally {
//			if (is != null) {
//				try {
//					is.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		}
//	}

    public static List<String> readLines(URL url) {
        return readLines(url, null);
    }

    public static List<String> readLines(URL url, String encoding) {
        try {
            return readLines(url.openStream(), encoding);
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
    }

    public static Properties readPropertiesFile(String filePath) {
        if (StringUtils.isEmpty(filePath)) {
            return null;
        }
        Properties objProperties = new Properties();
        File file = new File(filePath);
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            objProperties = new Properties();
            objProperties.load(inStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("未找到属性资源文件!", e);
        } catch (IOException e) {
            throw new RuntimeException("加载属性资源文件发生IO异常!", e);
        } finally {
            try {
                if (inStream != null)
                    inStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return objProperties;
    }

    public static Properties readPropertiesFile(URL url) {
        Properties objProperties = new Properties();
        InputStream inStream = null;
        try {
            inStream = url.openStream();
            objProperties.load(inStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("未找到属性资源文件!", e);
        } catch (IOException e) {
            throw new RuntimeException("读取属性资源文件发生IO错误!URL:" + url.getPath(), e);
        } finally {
            try {
                if (inStream != null)
                    inStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return objProperties;
    }

    public static void save(File f, byte[] responseBody) {

        try {
            f.createNewFile();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(responseBody);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void save(File file, InputStream in) {
        OutputStream fw = null;
        // InputStreamReader read = null;
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw RX.throwB("创建文件:" + file + "失败");
                }
            } catch (IOException e) {
                throw ErrorUtils.toRuntimeException(e);
            }
        }
        try {
            fw = new FileOutputStream(file);
            // read = new InputStreamReader(in);
            byte[] b = new byte[1024];
            int len = 0;
            while ((len = in.read(b)) != -1) {
                fw.write(b, 0, len);
            }
            fw.flush();
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            try {
                if (fw != null)
                    fw.close();

            } catch (IOException e) {
                throw ErrorUtils.toRuntimeException(e);
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw ErrorUtils.toRuntimeException(e);
                }
            }

        }
    }

    public static void save(File file, List<String> ss) {
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw ErrorUtils.toRuntimeException(e);
            }
        }
        try {
            fw = new FileWriter(file);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);
            for (String s : ss) {
                s = s.replace("/debug", "");
                pw.print(s);
                pw.print("\r");
            }
            pw.flush();
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {

            try {
                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();
                if (pw != null)
                    pw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                throw ErrorUtils.toRuntimeException(e);
            }

        }

    }

    public static void save(String file, String str) {

        save(new File(file), str);
    }

    public static void save(File file, String str) {
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        if (!file.exists()) {
            try {

                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                throw ErrorUtils.toRuntimeException(e);
            }
        }
        try {
            fw = new FileWriter(file);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);
            pw.print(str);
            pw.flush();
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {

            try {
                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();
                if (pw != null)
                    pw.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                throw ErrorUtils.toRuntimeException(e);
            }

        }

    }

    public static Properties storePropertiesFile(Properties objProperties, File file) {
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            objProperties.store(stream, "");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("未找到属性资源文件!", e);
        } catch (IOException e) {
            throw new RuntimeException("加载属性资源文件发生IO异常!", e);
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return objProperties;
    }

    public static String toString(File f) {
        return toString(f, "utf-8");
    }

    public static String toStringByFilePath(String path) {
        return toString(new File(path), "utf-8");
    }

    public static byte[] readBytes(File file) {
        byte[] buffer = null;
        FileInputStream fis;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    public static String toString(File f, String encoding) {
        FileReader r = null;
        BufferedReader br = null;
        String str;
        StringBuilder sb = new StringBuilder();
        FileInputStream fs = null;
        InputStreamReader read = null;
        try {
            r = new FileReader(f);
            fs = new FileInputStream(f);

            if (StringUtils.isEmpty(encoding)) {
                read = new InputStreamReader(fs);
            } else {
                read = new InputStreamReader(fs, encoding);
            }
            br = new BufferedReader(read);
            while ((str = br.readLine()) != null) {
                sb.append(str).append("\n");
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            try {
                if (br != null)
                    br.close();
                if (r != null)
                    r.close();
                if (fs != null)
                    r.close();
                if (read != null)
                    read.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                throw ErrorUtils.toRuntimeException(e);
            }

        }
        return sb.toString();
    }

    public static String toString(InputStream in) {
        return toString(in, "utf-8");
    }

    // /**
    // * 将src的内容追加到dst的文件尾部
    // *
    // * @param dst
    // * @param src
    // * @param withLine
    // * 是否换行
    // */
    // public static void append(File dst, File src, boolean withLine) {
    // mergeFiles(dst, new File[] { src }, true,true);
    // }

    public static String toString(InputStream in, String encoding) {
        List<String> lines = FileUtils.readLines(in, encoding);
        StringBuilder sb = new StringBuilder();

        for (String str : lines) {
            sb.append(str).append("\n");
        }
        return sb.toString();
    }

    public static void unCompressFile(File zipFile, File dst) {
        ZipInputStream zin = null;
        try {
            File olddirec = zipFile; // 解压缩的文件路径(为了获取路径)
            zin = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            // 创建文件夹
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    File directory = new File(olddirec.getParent(), entry.getName());
                    if (!directory.exists())
                        if (!directory.mkdirs())
                            System.exit(0);
                    zin.closeEntry();
                }
                if (!entry.isDirectory()) {
                    File myFile = new File(entry.getName());
                    File f = new File(dst.getPath() + File.separator + myFile.getPath());
                    f.getParentFile().mkdirs();
                    FileOutputStream fout = new FileOutputStream(f);
                    DataOutputStream dout = new DataOutputStream(fout);
                    byte[] b = new byte[1024];
                    int len = 0;
                    while ((len = zin.read(b)) != -1) {
                        dout.write(b, 0, len);
                    }
                    dout.close();
                    fout.close();
                    zin.closeEntry();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e);
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    public static void write(File f, OutputStream ops) {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(f);

            int size = 0;
            byte[] b = new byte[4096];
            while ((size = fileInputStream.read(b)) != -1) {
                ops.write(b, 0, size);
            }
            ops.flush();
        } catch (FileNotFoundException e) {
            throw ErrorUtils.toRuntimeException(e);
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    throw ErrorUtils.toRuntimeException(e);
                }
            }
        }
    }

    public static void write(InputStream in, OutputStream ops) {
        try {

            int size = 0;
            byte[] b = new byte[4096];
            while ((size = in.read(b)) != -1) {
                ops.write(b, 0, size);
            }
            ops.flush();
        } catch (FileNotFoundException e) {
            throw ErrorUtils.toRuntimeException(e);
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    throw ErrorUtils.toRuntimeException(e);
                }
            }
        }
    }

    public static void write(File f, Writer writer) {
        FileReader r = null;
        try {
            r = new FileReader(f);

            int size = 0;
            char[] b = new char[4096];
            while ((size = r.read(b)) != -1) {
                writer.write(b, 0, size);
            }
            writer.flush();
        } catch (FileNotFoundException e) {
            throw ErrorUtils.toRuntimeException(e);
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException e) {
                    throw ErrorUtils.toRuntimeException(e);
                }
            }
        }
    }

}
