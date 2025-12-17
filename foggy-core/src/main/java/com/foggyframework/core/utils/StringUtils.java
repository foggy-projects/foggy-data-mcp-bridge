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
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class StringUtils {
    /**
     * 对str进行分割，分割后每段长度为length,如果数量小于num，则补全，如果数量大于num则放弃
     *
     * @param str
     * @param length
     * @param num
     * @return
     */
    public static String[] txtSplitByLength(String str, int length, int num) {

        if (str == null) {
            return new String[]{"", "", ""};
        }
        String[] values = new String[num];


        for (int i = 0, idx = 0; i < values.length; i++) {
            if (idx >= str.length()) {
                //已经没有后续数据了
                values[i] = "";
            } else if ((str.length() - idx) < length) {
                //剩下的字符串，不够length的长度了
                values[i] = str.substring(idx);
            } else {
                values[i] = str.substring(idx, idx + length);
            }
            idx = idx + length;

        }
        return values;

    }

    public static int countOfChar(String str, char c) {

        int size = 0;

        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                size++;
            }
        }

        return size;
    }

    /**
     * 比较src和dst，在给定的checkFields中是否相等,通常用于重复入参检查
     *
     * @param src
     * @param dst
     * @param errorMsg    如果非空，在不相等时会抛异常
     * @param checkFields
     */
    public static boolean checkEq(Object src, Object dst, String errorMsg, String... checkFields) {
        BeanInfoHelper srcHelper = BeanInfoHelper.getClassHelper(src.getClass());
        BeanInfoHelper dstHelper = BeanInfoHelper.getClassHelper(dst.getClass());

        for (String checkField : checkFields) {
            Object v1 = srcHelper.getBeanProperty(checkField, true).getBeanValue(src);
            Object v2 = dstHelper.getBeanProperty(checkField, true).getBeanValue(dst);

            boolean b = StringUtils.equals(v1, v2);
            if (b) {
                continue;
            } else {
                if (errorMsg != null) {
                    RX.throwB(errorMsg + ",field: " + checkField);
                } else {
                    return false;
                }
            }

        }
        return true;
    }

    /**
     * 将会把字段名，如get_name转换为getName
     *
     * @param str
     * @return
     */
    public static String to(String str) {
        String[] xx = str.split("_");
        StringBuilder sb = new StringBuilder(xx[0]);
        for (int i = 1; i < xx.length; i++) {
            String x = xx[i];
            if (StringUtils.isTrimEmpty(x)) {
                continue;
            }
            sb.append(x.substring(0, 1).toUpperCase());
            sb.append(x, 1, x.length());
        }
        return sb.toString();
    }

    public static String to(String str, String split) {
        String[] xx = str.split(split);
        StringBuilder sb = new StringBuilder(xx[0]);
        for (int i = 1; i < xx.length; i++) {
            String x = xx[i];
            if (StringUtils.isTrimEmpty(x)) {
                continue;
            }
            sb.append(x.substring(0, 1).toUpperCase());
            sb.append(x, 1, x.length());
        }
        return sb.toString();
    }

    public static String toLink(String str) {

        String link = "-";

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            char chr = str.charAt(i);
            if (Character.isUpperCase(chr)) {
                //大写
                if (i > 0) {
                    sb.append(link);
                }
                sb.append(Character.toLowerCase(chr));
            } else {
                sb.append(chr);
            }
            i++;
        }
        return sb.toString();
    }

    public static String nextLine() {

        return "\n";
    }

    public static String replaceOnce(String template, String placeholder, String replacement) {
        if (template == null) {
            return null;  // returning null!
        }
        int loc = template.indexOf(placeholder);
        if (loc < 0) {
            return template;
        } else {
            return template.substring(0, loc) + replacement + template.substring(loc + placeholder.length());
        }
    }

    public static boolean equalsIgnoreCase(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equalsIgnoreCase(str2);
    }

    /**
     * 去掉字符串中所有的空格
     *
     * @param obj
     * @return
     */
    public static String trimStr2(Object obj) {
        if (null == obj) {
            return null;
        }
        if (obj instanceof String str) {
            if (StringUtils.isBlank(str)) {
                return null;
            }
            return str.replaceAll(" ", "");
        }
        return null;
    }

    /**
     * 去掉首尾的空格
     *
     * @param obj
     * @return
     */
    public static String trimStr(Object obj) {
        if (null == obj) {
            return null;
        }
        if (obj instanceof String str) {
            if (StringUtils.isBlank(str)) {
                return null;
            }
            return str.trim();
        }
        return null;
    }

    public static String unionPath(String... path) {
        if (path.length < 1) {
            return "";
        }
        if (path.length < 2) {
            return path[0];
        }
        StringBuilder sb = new StringBuilder();
        String p1 = path[0];
        String p2 = path[0];
        for (int i = 0; i < path.length; i++) {
            unionPath1(sb, path[i]);
        }

        return sb.toString();
    }

    private static String unionPath1(StringBuilder sb, String path) {

        if (sb.length() == 0) {
            sb.append(path);
        } else if (sb.charAt(sb.length() - 1) == '/' && path.startsWith("/")) {
            sb.deleteCharAt(sb.length() - 1);
            sb.append(path);
        } else if (sb.charAt(sb.length() - 1) == '/' || path.startsWith("/")) {
            sb.append(path);
        } else {
            sb.append("/");
            sb.append(path);
        }
        return sb.toString();
    }

    public static void arrayCopy(Object[] dest, Object[] src) {
        for (int i = 0; i < dest.length && i < src.length; i++) {
            dest[i] = src[i];
        }
    }

    public static String toFirstUpperCase(String name) {
        if (StringUtils.isEmpty(name)) {
            return null;
        }

        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public String toISO(String str) {
        if (str == null) {
            return null;
        }

        try {
            return new String(str.getBytes("gbk"), "ISO8859_1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return str;
        }

    }

    /**
     * 截取str中的数字部分包含小数
     *
     * @param str
     * @return String
     * @author: 9meng
     */
    public static String getAllNum(String str) {
        StringBuilder builder = new StringBuilder();
        String regEx = "(\\d+(\\.\\d+)?)";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(str);
        while (m.find()) {// 当符合正则表达式定义的条件时
            builder.append(m.group());
        }
        return builder.toString();
    }

    private static final String xml10pattern;

    static {
        xml10pattern = "[^" + "\u0009\r\n" + "\u0020-\uD7FF" + "\uE000-\uFFFD" + "\ud800\udc00-\udbff\udfff" + "]";
    }

    /**
     * 字符串转为char数组，然后逐一进行判断，符合的字符保留。 @stringUtils.decodeby10()
     *
     * @param str
     * @return
     */
    public static String decodeby10(String str) {
        StringBuffer out = new StringBuffer();
        if (str == null || ("".equals(str)))
            return "";
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if ((chars[i] >= 19968 && chars[i] <= 40869) // 中日朝兼容形式的unicode编码范围： U+4E00——U+9FA5
                    || (chars[i] >= 11904 && chars[i] <= 42191)// 中日朝兼容形式扩展
                    || (chars[i] >= 63744 && chars[i] <= 64255)// 中日朝兼容形式扩展
                    || (chars[i] >= 65072 && chars[i] <= 65103)// 中日朝兼容形式扩展
                    || (chars[i] >= 65280 && chars[i] <= 65519)// 全角ASCII、全角中英文标点、半宽片假名、半宽平假名、半宽韩文字母的unicode编码范围：U+FF00——U+FFEF
                    || (chars[i] >= 32 && chars[i] <= 126)// 半角字符的unicode编码范围：U+0020-U+007e
                    || (chars[i] >= 12289 && chars[i] <= 12319)// 全角字符的unicode编码范围：U+3000——U+301F
            ) {
                out.append(chars[i]);
            }
        }
        String result = out.toString().trim();
        result = result.replaceAll("\\?", "").replaceAll("\\*", "").replaceAll("<|>", "").replaceAll("\\|", "")
                .replaceAll("/", "");
        return result.trim();
    }


    public static String trim(String str) {

        if (str == null) {
            return null;
        }

        str = str.replaceAll(" ", ""); // 全角空格
        str = str.replaceAll(" ", "");

        return str.trim();
    }

    /**
     * 仅替换左右
     *
     * @param str
     * @return
     */
    public static String trimSafe(String str) {

        if (str == null) {
            return null;
        }
        str = str.trim();

        if (str.startsWith("　")) {
            try {
                return trimSafe(str.substring(1));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (str.endsWith("　")) {
            try {
                return trimSafe(str.substring(0, str.length() - 1));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
//        if(str.startsWith("\t")){
//            return trimSafe(str.substring(1));
//        }
//        if(str.endsWith("\t")){
//            return trimSafe(str.substring(0,str.length()));
//        }
//        str = str.replaceAll(" ", ""); // 全角空格
//        str = str.replaceAll(" ", "");

        return str;
    }

    /**
     * @param v
     * @return
     * @stringUtils.fixXmlUtf8() XSL有时候会跳出java.io.IOException: Invalid UTF-16 surrogate detected: d83d ?
     * 也许是XML有非法字符，处理掉它
     */
    public static String fixXmlUtf8(String v) {
        if (v == null) {
            return null;
        }
        v = v.replaceAll(xml10pattern, " ");
        return v;
    }

    public static String encodeInput(final String value) {
        final String ret = StringUtils.replace(value, "\"", "\\\"");
        return ret;
    }

    public static boolean equals(Object gv, Object prevGv) {
        if (gv == null || prevGv == null) {
            return gv == prevGv;
        }
        return gv.equals(prevGv);
    }

    public static boolean equals(String value, String value2) {
        if (value == null || value2 == null) {
            return value2 == value;
        }
        return value.equals(value2);
    }

    /**
     * 这个，即使value==null,value2=""。也是成立的
     *
     * @param value
     * @param value2
     * @return
     */
    public static boolean equals1(String value, String value2) {
        if (value == null || value2 == null) {
            if (value2 == value) {
                return true;
            }
        }
        if (StringUtils.isEmpty(value) || StringUtils.isEmpty(value2)) {
            return true;
        }
        return value.equals(value2);
    }

    public static List<String> getContains(final String str, final String patternStr) {
        final ArrayList<String> returnlist = new ArrayList<String>();

        final Pattern p = Pattern.compile(patternStr);
        final Matcher m = p.matcher(str);
        // final StringBuffer sb = new StringBuffer();

        // 使用find()方法查找第一个匹配的对象
        boolean result = m.find();
        // 使用循环将句子里所有的 patternStr
        while (result) {

            returnlist.add(m.group(0));
            result = m.find();
        }

        return returnlist;
    }

    public static int getCount(final String query, final char c) {
        int cnt = 0;
        for (int i = 0; i < query.length(); i++) {
            if (query.charAt(i) == c) {
                cnt++;
            }
        }
        return cnt;
    }

    public static String getTableName(String name) {
        final int i = name.lastIndexOf('.');
        if (i != -1) {
            name = name.substring(i + 1);
        }
        return StringUtils.propertyToDB(name);
    }

    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }

    public static boolean hasLength(String text) {
        return hasLength((CharSequence) text);
    }

    public static boolean hasText(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public  static boolean isEmpty(final Long value) {
        return value == null || value == 0L;
    }

    public  static boolean isEmpty(final Object value) {
        return value == null || "".equals(value);
    }

    public  static boolean isTrimEmpty(final Object value) {
        return value == null || "".equals(value)
                || (value instanceof String && value.toString().trim().length() == 0);
    }

    public  static boolean isNotEmpty(final Object value) {
        return !isEmpty(value);
    }

    public  static boolean isNotTrimEmpty(final Object value) {
        return !isTrimEmpty(value);
    }

    public static String iso88591ToGbk(String x) {
        try {
            return x == null ? x : new String(x.getBytes(StandardCharsets.ISO_8859_1), "GBK");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return x;
        }
    }

    public static boolean isTrimEmpty(String str) {
        return str == null || isEmpty(str.trim());
    }

    /**
     * 如果指定字符串的长度超出length，则减少
     *
     * @param string
     * @param length
     * @return
     */
    public static String maxLength(String string, int length) {
        if (string == null)
            return null;
        if (string.length() > length) {
            return string.substring(0, length);
        }
        return string;
    }

    public static int parseInt(final String string) {
        try {
            return Integer.parseInt(string.trim());
        } catch (final Exception e) {
        }
        return 0;
    }

    // like String patternStr = "\\:([a-z|A-Z|0-9|_]+)"; //匹配:abcd
    public static String print(final Object[] args) {
        final StringBuilder sb = new StringBuilder("[");
        for (final Object obj : args) {
            sb.append(obj == null ? "null" : obj.toString());
            sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String printTab(int tabs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabs; i++) {
            sb.append("\t");
        }

        return sb.toString();
    }

    public static String propertyToDB(final String name) {
        return name.toUpperCase();
    }

    public static String replace(final String line, final String oldString, final String newString) {
        if (line == null) {
            return null;
        }

        int i = 0;

        if ((i = line.indexOf(oldString, i)) >= 0) {
            final char[] line2 = line.toCharArray();
            final char[] newString2 = newString.toCharArray();
            final int oLength = oldString.length();
            final StringBuilder buf = new StringBuilder(line2.length);

            buf.append(line2, 0, i).append(newString2);
            i += oLength;

            int j = i;

            while ((i = line.indexOf(oldString, i)) > 0) {
                buf.append(line2, j, i - j).append(newString2);
                i += oLength;
                j = i;
            }

            buf.append(line2, j, line2.length - j);

            return buf.toString();
        }

        return line;
    }

    public static String stringList2Query(final List<String> list) {
        String result = "";
        if (list.size() == 0) {
            return "''";
        }
        for (int i = 0; i < list.size(); i++) {
            result += "'" + list.get(i) + "',";
        }
        return result.endsWith(",") ? result.substring(0, result.length() - 1) : result;
    }

    public static String toBytesString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(b);
        }
        return sb.toString();
    }

    /**
     * 呃，字符串间用空格格开
     *
     * @param xx
     * @return
     */
    public static String toString(Object... xx) {
        if (xx == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object x : xx) {
            sb.append(x).append(" ");
        }
        return sb.toString();
    }

    /**
     * 与maxLength不同之处在于，当超出时，以...结束
     *
     * @param str
     * @param length
     * @return
     */
    public static String maxLengthX(String str, int length) {
        if (str == null) {
            return null;
        }
        if (length > str.length()) {
            return str;
        }
        str = str.substring(0, length - 3);
        return str + "...";
    }

    /**
     * 将VehicleType转成vehicle_type格式
     */
    public static String to_sm_string(String str) {
        if (str == null) {
            return null;
        }
        str = str.trim();
        if (str.length() < 1) {
            return str.toLowerCase();
        }
        StringBuilder sb = new StringBuilder();
        if (Character.isUpperCase(str.charAt(0))) {
            sb.append(Character.toLowerCase(str.charAt(0)));
        } else {
            sb.append(str.charAt(0));
        }

        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_");
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 将VehicleType转成vt_格式
     */
    public static String to_cn_string(String str) {
        if (str == null) {
            return "";
        }
        str = str.trim();
        if (str.length() < 1) {
            return str.toLowerCase() + "_";
        }

        StringBuilder sb = new StringBuilder();
        if (Character.isUpperCase(str.charAt(0))) {
            sb.append(Character.toLowerCase(str.charAt(0)));
        } else {
            sb.append(str.charAt(0));
        }
        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
            }
        }
        return sb.append("_").toString();
    }

    /**
     * 将VehicleType转成vehicleType格式
     */
    public static String to_var_string(String str) {
        if (str == null) {
            return "";
        }
        str = str.trim();
        if (str.length() < 1) {
            return str.toLowerCase();
        }

        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if ((!Character.isWhitespace(str.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBlank(Long str) {
        return str == null || str == 0;
    }

    public static boolean isNotBlank(String str) {
        return !StringUtils.isBlank(str);
    }

    /**
     * ASCII表中可见字符从!开始，偏移位值为33(Decimal)
     */
    static final char DBC_CHAR_START = 33; // 半角!

    /**
     * ASCII表中可见字符到~结束，偏移位值为126(Decimal)
     */
    static final char DBC_CHAR_END = 126; // 半角~

    /**
     * 全角对应于ASCII表的可见字符从！开始，偏移值为65281
     */
    static final char SBC_CHAR_START = 65281; // 全角！

    /**
     * 全角对应于ASCII表的可见字符到～结束，偏移值为65374
     */
    static final char SBC_CHAR_END = 65374; // 全角～

    /**
     * ASCII表中除空格外的可见字符与对应的全角字符的相对偏移
     */
    static final int CONVERT_STEP = 65248; // 全角半角转换间隔

    /**
     * 全角空格的值，它没有遵从与ASCII的相对偏移，必须单独处理
     */
    static final char SBC_SPACE = 12288; // 全角空格 12288

    /**
     * 半角空格的值，在ASCII中为32(Decimal)
     */
    static final char DBC_SPACE = ' '; // 半角空格

    /**
     * <PRE>
     * <p>
     * 半角字符->全角字符转换
     * 只处理空格，!到˜之间的字符，忽略其他
     * </PRE>
     */
    private static String bj2qj(String src) {
        if (src == null) {
            return src;
        }
        StringBuilder buf = new StringBuilder(src.length());
        char[] ca = src.toCharArray();
        for (int i = 0; i < ca.length; i++) {
            if (ca[i] == DBC_SPACE) { // 如果是半角空格，直接用全角空格替代
                buf.append(SBC_SPACE);
            } else if ((ca[i] >= DBC_CHAR_START) && (ca[i] <= DBC_CHAR_END)) { // 字符是!到~之间的可见字符
                buf.append((char) (ca[i] + CONVERT_STEP));
            } else { // 不对空格以及ascii表中其他可见字符之外的字符做任何处理
                buf.append(ca[i]);
            }
        }
        return buf.toString();
    }

    /**
     * <PRE>
     * <p>
     * 全角字符->半角字符转换
     * 只处理全角的空格，全角！到全角～之间的字符，忽略其他
     * </PRE>
     */
    public static String qj2bj(String src) {
        if (src == null) {
            return src;
        }
        StringBuilder buf = new StringBuilder(src.length());
        char[] ca = src.toCharArray();
        for (int i = 0; i < src.length(); i++) {
            if (ca[i] >= SBC_CHAR_START && ca[i] <= SBC_CHAR_END) { // 如果位于全角！到全角～区间内
                buf.append((char) (ca[i] - CONVERT_STEP));
            } else if (ca[i] == SBC_SPACE) { // 如果是全角空格
                buf.append(DBC_SPACE);
            } else { // 不处理全角空格，全角！到全角～区间外的字符
                buf.append(ca[i]);
            }
        }
        return buf.toString();
    }

    /**
     * 所有用户录入的电话，都需要经过这里转换
     *
     * @param src
     * @return
     */
    public static String xxtel(String src) {
        if (src == null) {
            return src;
        }
        src = decodeby10(src);
        return qj2bj(src);
    }

    public static String buildEmpty(int length) {
        return buildEmpty("", length);
    }

    public static String buildEmpty(String str, int length) {
        StringBuilder sb = new StringBuilder(str);

        for (int i = 0; i < length; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    public static String fillEmpty(String str, int length) {
        StringBuilder sb = new StringBuilder(str);

        for (int i = str.length(); i < length; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }


    /**
     * Return true if the character is the high member of a surrogate pair.
     * <p>
     * This is not a public API.
     *
     * @param ch the character to test
     * @xsl.usage internal
     */
    static boolean isHighUTF16Surrogate(char ch) {
        return ('\uD800' <= ch && ch <= '\uDBFF');
    }

    /**
     * Return true if the character is the low member of a surrogate pair.
     * <p>
     * This is not a public API.
     *
     * @param ch the character to test
     * @xsl.usage internal
     */
    static boolean isLowUTF16Surrogate(char ch) {
        return ('\uDC00' <= ch && ch <= '\uDFFF');
    }

    static boolean isUtf16(char ch) {
        return isHighUTF16Surrogate(ch) || isLowUTF16Surrogate(ch);
    }

    public static String maskName(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() <= 1) {
            return name;
        }
        return maskTxt(name, 1, 1);
    }

    public static String maskTel(String name) {
        return maskTxt(name, 3, 4);
    }

    public static String maskTxt(String name, int start, int length) {
        if (name == null) {
            return null;
        }
        if (name.length() < start + length) {
            return name;
        }
        String x = name.substring(0, start);
        for (int i = 0; i < length; i++) {
            x = x + "*";
        }
        x = x + name.substring(start + length);
        return x;
    }


    public static String replaceUtf16ToEmpty(String str) {

        StringBuilder sb = new StringBuilder();

        final int length = str.length();
        char[] m_charsBuff = new char[length];

        str.getChars(0, length, m_charsBuff, 0);

        for (int i = 0; i < m_charsBuff.length; i++) {
            if (isHighUTF16Surrogate(m_charsBuff[i])) {
                i++;
            } else if (isLowUTF16Surrogate(m_charsBuff[i])) {
                sb.deleteCharAt(i - 1);
                i++;
            } else {
                sb.append(m_charsBuff[i]);
            }
        }

        return new String(sb.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static String txtMSK(String str) {

        if (str == null || str.length() == 0) {
            return "****";
        }
        if (str.length() == 1) {
            return str + "***";
        }

        final int length = str.length();
        char[] m_charsBuff = new char[length * 2 + 1];

        String first = null;
        str.getChars(0, length, m_charsBuff, 0);
        if (isHighUTF16Surrogate(m_charsBuff[0])) {
            first = new String(m_charsBuff, 0, 2);
        } else {
            first = new String(m_charsBuff, 0, 1);
        }
        if (str.length() == 2) {
            return first + "***";
        }
        if (str.length() == 3) {
            return first + "***";
        }
        int lastPos = m_charsBuff.length - 1;
        for (; lastPos > 0; lastPos--) {
            // // Systemx.out.println((byte )m_charsBuff[lastPos]);
            if (m_charsBuff[lastPos] != '\0') {
                break;
            }
        }
        String last = null;
        if (isLowUTF16Surrogate(m_charsBuff[lastPos])) {
            last = new String(m_charsBuff, lastPos - 1, 2);
        } else {
            last = new String(m_charsBuff, lastPos, 1);
        }
        return first + "***" + last;
    }

    /**
     * 例如，把"1,2,5,54 ,5"这样的字符，转换成集合{1,2,54,5}，去重，去左右空格
     *
     * @param str
     * @param split
     * @return
     */
    public static Set<String> splitAndDistinct(String str, String split) {

        return splitAndDistinct(str, split, Collections.EMPTY_SET);
    }

    public static Set<String> splitAndDistinct(String str, String split, Set<String> defaultSet) {
        if (str == null) {
            return defaultSet;
        }
        Set<String> set = Arrays.stream(str.split(split)).filter(e -> StringUtils.isNotEmpty(e.trim())).map(e -> e.trim()).collect(Collectors.toSet());
        return set;
    }

    public static Set<String> splitAndDistinct(String str, String split, String defaultValue) {
        if (str == null && defaultValue != null) {
            Set<String> s = new HashSet<>();
            s.add(defaultValue);
            return s;
        }
        Set<String> set = Arrays.stream(str.split(split)).filter(e -> StringUtils.isNotEmpty(e.trim())).map(e -> e.trim()).collect(Collectors.toSet());
        if (set.isEmpty() && defaultValue != null) {
            set.add(defaultValue);
        }
        return set;
    }

    public static Object trimObject(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            for (Map.Entry entry : ((Map<?, ?>) obj).entrySet()) {
                entry.setValue(trimObject(entry.getValue()));
            }
        } else if (obj instanceof String) {
            return trimSafe((String) obj);
        } else if (!BeanInfoHelper.isBaseClass(obj.getClass())) {
            for (BeanProperty fieldProperty : BeanInfoHelper.getClassHelper(obj.getClass()).getFieldProperties()) {
                fieldProperty.setBeanValue(obj, trimObject(fieldProperty.getBeanValue(obj)));
            }
        }

        return obj;
    }

    public static String join(List list, String split) {
        if (list == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object s : list) {
            if (s == null) {
                continue;
            }
            sb.append(s).append(split);
        }
        if (sb.length() > 0 && split != null && split.length() > 0) {
            sb.delete(sb.length() - split.length(), sb.length());
        }

        return sb.toString();
    }

    public static String join(String[] list, String split) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null) {
                continue;
            }
            sb.append(s).append(split);
        }
        if (sb.length() > 0 && split != null && split.length() > 0) {
            sb.delete(sb.length() - split.length(), sb.length());
        }

        return sb.toString();
    }
}
