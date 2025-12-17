package com.foggyframework.core.utils;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class NumberUtils {

    /**
     * 当c的值为空，0/0.00时，返回false
     *
     * @param c 数字
     * @return 当c的值为空，0/0.00时，返回false
     */
    public static boolean check(Number c) {

        boolean x;
        if (c instanceof Integer) {
            x = c.intValue() != 0;
        } else if (c instanceof Long) {
            x = c.intValue() != 0;
        } else if (c instanceof Double) {
            x = ((Double) c) != 0.0;
        } else {
            x = !StringUtils.isEmpty(c);
        }
        return x;
    }

    /**
     * 取小数点后两位，四舍五入 @numberUtils.toFixed(x)
     *
     * @param value 数字
     * @return 取小数点后两位，四舍五入 @numberUtils.toFixed(x)
     */
    public static double toFixed(Number value) {
        if (value == null) {
            return 0;
        }
        return toFixed(value, 2);
    }

    /**
     * 把分转成元为单位的字符串
     *
     * @param value 分
     * @return 元
     */
    public static String cent2yuan(Integer value) {
        if (value == null) {
            return "0";
        }
        return toFixedStr(value / 100.0);
    }

    /**
     * 把分转成元为单位的字符串
     * 2022-12-12 升级，如果是以.00结束，则去掉.00
     *
     * @param value 分
     * @return 元
     */
    public static String cent2yuanFix(Integer value) {
        if (value == null) {
            return "0";
        }
        String str = toFixedStr(value / 100.0);
        if (str.endsWith(".00")) {
            return str.substring(0, str.length() - 3);
        } else {
            return str;
        }
    }


    /**
     * 把传入的金额value,按kv进行分割，例如 value = 9873 ,kv = 5000,返回
     *
     * @param value
     * @param kv
     * @return
     */
    public static Integer[] split(Integer value, Integer kv) {
        if (value == null) {
            return new Integer[0];
        }
//        Integer[] ss = new Integer[Math.round(1984 / (500*1.0))];

        List<Integer> ll = new ArrayList<>();

        Integer left = value;
        while (true) {

            if (left < kv) {
                ll.add(left);
                break;
            }
            ll.add(kv);
            left = left - kv;
        }

        return ll.toArray(new Integer[0]);
    }

    public static double toFixedDouble(double value) {

        return toFixed(value, 2);
    }

    public static double toDoubleFix(String value) {
        if (value == null) {
            return 0;
        }
        return toFixed(Double.valueOf(value), 2);
    }

    public static String toFixedStr(Double value) {
        if (value == null) {
            return null;
        }
        DecimalFormat df1 = new DecimalFormat("0.00");
        return df1.format(toFixed(value, 2));
    }

    public static String toFixedStr3(Double value) {
        if (value == null) {
            return null;
        }
        DecimalFormat df1 = new DecimalFormat("0.000");
        return df1.format(toFixed(value, 3));
    }

    public static String toFixedStr4(Double value) {
        if (value == null) {
            return null;
        }
        DecimalFormat df1 = new DecimalFormat("0.0000");
        return df1.format(toFixed(value, 2));
    }

    /**
     * 从字符中提取出数字
     *
     * @param cost
     * @return
     */
    public static Double extract_cost_dot(String cost, int dotLength) {
        Pattern compile = Pattern.compile("(\\d+\\.\\d+)|(\\d+)");
        Matcher matcher = compile.matcher(cost);
        matcher.find();
        Double d = Double.valueOf(matcher.group());
        if (d == null) {
            return null;
        }
        if (dotLength > 0) {
            return NumberUtils.toFixed(d, dotLength);
        }
        return d;
    }

    /**
     * 不够位数的在前面补0，保留num的长度位数字
     *
     * @param code
     * @return
     */
    public static String bl(Long code, int num) {
        // 保留num的位数
        // 0 代表前面补充0
        // num 代表长度为4
        // d 代表参数为正数型
        String result = String.format("%0" + num + "d", code);

        return result;
    }

    public static Integer toInteger(Number v, int scal) {
        float dv = v.floatValue() * scal;

        return (Math.round(dv));
    }

    /**
     * 四舍五入
     *
     * @param value1 数字
     * @param num    位数
     * @return 返回四舍五入后的数字
     */
    public static double toFixed(Number value1, int num) {
        if (value1 == null) {
            return 0;
        }
        double value = value1.doubleValue();
        switch (num) {
            case 0:
                return (Math.round(value));
            case 1:
                return (Math.round(value * 10)) / 10.0;
            case 2:
                return (Math.round(value * 100)) / 100.0;
            case 3:
                return (Math.round(value * 1000)) / 1000.0;
            case 4:
                return (Math.round(value * 10000)) / 10000.0;
            case 5:
                return (Math.round(value * 100000)) / 100000.0;
            case 6:
                return (Math.round(value * 1000000)) / 1000000.0;
            case 7:
                return (Math.round(value * 10000000)) / 10000000.0;
        }

        double x = Math.pow(10, num);

        double d = (Math.round(value * x)) / x;
        return d;
    }

    public static String format(String format, Number num) {
        if (StringUtils.isEmpty(format)) {
            return null;
        }
        DecimalFormat df = new DecimalFormat(format);
        return df.format(num);
    }

    public static double toDouble(Object object) {
        if (object instanceof Integer) {
            return ((Integer) object).doubleValue();
        } else if (object instanceof Number) {
            return ((Number) object).doubleValue();
        } else if (object != null) {
            final String str = object.toString();
            return str.length() > 0 ? Double.valueOf((String) object) : 0;
        }
        return 0;
    }

    /**
     * 若为空返回0
     *
     * @param ps_value
     * @return
     */
    public static Double numToDouble(Number ps_value) {
        return ps_value == null ? 0 : ps_value.doubleValue();
    }

    public static boolean doubleEquals(double x, double x2) {
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(x2);
    }

    /**
     * 保留小数点radix位的比较
     *
     * @param x
     * @param x2
     * @param radix
     * @return
     */
    public static boolean equals(double x, double x2, int radix) {
        double o = 10;
        switch (radix) {
            case 0:
                o = 1;
                break;
            case 1:
                o = 10;
                break;
            case 2:
                o = 100;
                break;
            case 3:
                o = 1000;
                break;
            case 4:
                o = 10000;
                break;
            case 5:
                o = 100000;
                break;
            case 6:
                o = 1000000;
                break;
            case 7:
                o = 10000000;
                break;
        }
        double xx = Math.floor(x * o) / o;
        double xx2 = Math.floor(x2 * o) / o;
        return Double.doubleToLongBits(xx) == Double.doubleToLongBits(xx2);
    }

    public static boolean equalsFix(double x, double x2, int radix) {
        double o = 10;
        switch (radix) {
            case 0:
                o = 1;
                break;
            case 1:
                o = 10;
                break;
            case 2:
                o = 100;
                x = x + 0.0001;
                x2 = x2 + 0.0001;
                break;
            case 3:
                o = 1000;
                break;
            case 4:
                o = 10000;
                break;
            case 5:
                o = 100000;
                break;
            case 6:
                o = 1000000;
                break;
            case 7:
                o = 10000000;
                break;
        }
        double xx = Math.floor(x * o) / o;
        double xx2 = Math.floor(x2 * o) / o;
        return Double.doubleToLongBits(xx) == Double.doubleToLongBits(xx2);
    }

    // public static void main(String[] args) {
    // // Systemx.out.println(NumberUtils.equals(0.44423, 0.44426, 4));
    // }

    public static byte[] int2Bytes(int num) {
        byte[] byteNum = new byte[4];
        for (int ix = 0; ix < 4; ++ix) {
            int offset = 32 - (ix + 1) * 8;
            byteNum[ix] = (byte) ((num >> offset) & 0xff);
        }
        return byteNum;
    }

    public static int bytes2Int(byte[] byteNum) {
        int num = 0;
        for (int ix = 0; ix < 4; ++ix) {
            num <<= 8;
            num |= (byteNum[ix] & 0xff);
        }
        return num;
    }

    public static byte int2OneByte(int num) {
        return (byte) (num & 0x000000ff);
    }

    public static int oneByte2Int(byte byteNum) {
        // 针对正数的int
        return byteNum > 0 ? byteNum : (128 + (128 + byteNum));
    }

    public static byte[] long2Bytes(long num) {
        byte[] byteNum = new byte[8];
        for (int ix = 0; ix < 8; ++ix) {
            int offset = 64 - (ix + 1) * 8;
            byteNum[ix] = (byte) ((num >> offset) & 0xff);
        }
        return byteNum;
    }

    public static long bytes2Long(byte[] byteNum) {
        long num = 0;
        for (int ix = 0; ix < 8; ++ix) {
            num <<= 8;
            num |= (byteNum[ix] & 0xff);
        }
        return num;
    }

    public static boolean equals(Integer x1, Integer x2) {
        if (x1 == null) {
            x1 = 0;
        }
        if (x2 == null) {
            x2 = 0;
        }
        return x1.equals(x2);
    }

    public static boolean equals(Long x1, Long x2) {
        if (x1 == null || x2 == null) {
            return x1 == x2;
        }
        return x1.equals(x2);
    }

    public static char[] NUMBER_CHAR = "零壹贰叁肆伍陆柒捌玖".toCharArray();

    public static char[] UNIT_CHAR = "元拾佰仟万拾佰仟亿拾佰仟".toCharArray();

    public static String toCN(Number amount) {
        return amount == null ? null : amount2RMB(amount.intValue());
    }

    public static String toCNX(Number amount) {
        return amount == null ? null : amount2RMBX(amount.intValue());
    }

    /**
     * 将小写金额转换成大写金额(以元为单位)
     *
     * @param amount 小写金额
     * @return 大写金额
     */
    public static String amount2RMB(int amount) {
        StringBuilder retSb = new StringBuilder();
        String retStr = "";
        int length = String.valueOf(amount).length() - 1;// 长度
        int pos = length;// 当前位置
        int posValue = 0;// 当前位置对应的值
        int dividend = (int) Math.pow(10, length);// 被除数
        boolean flag = false;

        if (amount < 10) {
            retSb.append(NUMBER_CHAR[amount]);
            retSb.append(UNIT_CHAR[pos]);
        } else {
            while (pos > 0) {
                posValue = amount / dividend;
                amount = amount % dividend;
                if (posValue > 0) {
                    if (flag && (pos != 3 && pos != 7)) {
                        retSb.append(NUMBER_CHAR[0]);
                    }
                    flag = false;
                    retSb.append(NUMBER_CHAR[posValue]);
                    retSb.append(UNIT_CHAR[pos]);
                }
                if (posValue == 0) {
                    flag = true;
                    if (pos == 4 || pos == 8) {
                        retSb.append(UNIT_CHAR[pos]);
                    }
                }
                pos--;
                dividend = dividend / 10;
            }
            if (amount > 0) {
                if (flag && (pos != 3 && pos != 7)) {
                    retSb.append(NUMBER_CHAR[0]);
                }
                retSb.append(NUMBER_CHAR[amount]);
            }
            retSb.append(UNIT_CHAR[0]);
        }
        retSb.append("整");

        retStr = retSb.toString().replaceAll("亿万", "亿");

        // // Systemx.out.println(retStr);
        return retStr;
    }

    /**
     * 仅返回每位的数据
     *
     * @param amount
     * @return
     */
    public static String amount2RMBX(int amount) {
        StringBuilder retSb = new StringBuilder();
        String retStr;
        int length = String.valueOf(amount).length() - 1;// 长度
        int pos = length;// 当前位置
        int posValue;// 当前位置对应的值
        int dividend = (int) Math.pow(10, length);// 被除数

        while (pos > 0) {
            posValue = amount / dividend;
            amount = amount % dividend;

            retSb.append(NUMBER_CHAR[posValue]);

            pos--;
            dividend = dividend / 10;
        }

        retSb.append(NUMBER_CHAR[amount]);

        retStr = retSb.toString();
        System.out.println(retStr);
        return retStr;
    }

    public static boolean isNumber(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public Integer ceil(double d) {
        return new Double(Math.ceil(d)).intValue();
    }

    public static Double sum(Double v1, Double v2) {
        if (v1 == null) {
            return v2;
        }
        if (v2 == null) {
            return v1;
        }
        return v1 + v2;
    }

    public static int sumInteger(Integer... vs) {
        int total = 0;
        for (Integer v : vs) {
            total = total + (v == null ? 0 : v);
        }
        return total;
    }

    public static int toInt(Integer object) {
        return object == null ? 0 : object;
    }

    public static boolean isZero(Integer object) {
        return object == null || object == 0;
    }

    public static long toLong(Long object) {
        return object == null ? 0 : object;
    }

    public static int toMoneyInt(double object) {
        return (int) (object + 0.000001);
    }

    public static String toMoneyStr(Integer object) {
        if (object == null) {
            return null;
        }
        return toFixedStr(object / 100.0);
    }


    public static Integer toInteger(Object object) {
        if (object instanceof Number) {
            return ((Number) object).intValue();
        } else if (object instanceof String str) {
            if (str.length() == 1 && str.contentEquals("-")) {
                return -0;
            }
            return str.length() > 0 ? Double.valueOf((String) object).intValue() : null;
        } else if (object instanceof Boolean) {
            return ((Boolean) object).booleanValue() ? 1 : 0;
        }
        return (Integer) object;
    }

    /**
     * @param num
     * @return
     * @numberUtils.numberToLetter(1) <br/>
     */
    public static String numberToLetter(int num) {
        if (num <= 0) {
            return null;
        }
        String letter = "";
        num--;
        do {
            if (letter.length() > 0) {
                num--;
            }
            letter = ((char) (num % 26 + 'A')) + letter;
            num = (num - num % 26) / 26;
        } while (num > 0);

        return letter;
    }

    public static String toIntString(double d) {

        return Integer.valueOf(Double.valueOf(d).intValue()).toString();
    }

    public static boolean hasFlag(Long source, long flag) {
        if (source == null) {
            return false;
        }
        return (source & flag) > 0;
    }

    public static Long addFlag(Long source, long flag) {
        if (source == null) {
            return flag;
        }
        return source | flag;
    }

    public static Long addFlags(Long source, long... flag) {
        if (source == null) {
            source = 0L;
        }
        for (long l : flag) {
            source = source | l;
        }
        return source;
    }

    public static Long delFlag(Long source, long flag) {
        if (source == null) {
            return 0L;
        }
        return source & (~flag);
    }

    public static Long delFlags(Long source, long... flag) {
        if (source == null) {
            source = 0L;
        }
        for (long l : flag) {
            source = source & (~l);
        }
        return source;
    }

    public static boolean hasIntFlag(Integer source, long flag) {
        if (source == null) {
            return false;
        }
        return (source & flag) > 0;
    }

    public static Long addIntFlag(Integer source, long flag) {
        if (source == null) {
            return flag;
        }
        return source | flag;
    }

    /**
     * 通过计算数值的位数，提取前几位进行比较。
     * <p>
     * long num = 123456L;
     * System.out.println(startsWithDigits(num, 123, 3)); // true
     * System.out.println(startsWithDigits(num, 12, 2));  // true
     *
     * @param number
     * @param prefix
     * @param prefixLength
     * @return
     */
    public static boolean startsWithDigits(long number, int prefix, int prefixLength) {
        if (prefixLength <= 0) throw new IllegalArgumentException("prefixLength必须大于0");
        if (number < 0) number = -number; // 忽略符号
        if (number == 0) return prefix == 0 && prefixLength == 1;

        // 计算数值的位数
        long temp = number;
        int numLength = 0;
        while (temp != 0) {
            temp /= 10;
            numLength++;
        }

        if (numLength < prefixLength) return false;
        long divisor = (long) Math.pow(10, numLength - prefixLength);
        long firstDigits = number / divisor;
        return firstDigits == prefix;
    }

    public static String subLongToStr(Long str, int start, int end) {
        if (str == null) {
            return null;
        }
        return str.toString().substring(start, end);
    }
    public static Long subLong(Long str, int start, int end,String addToRight) {
        if (str == null) {
            return null;
        }
        return Long.parseLong(str.toString().substring(start, end)+addToRight);
    }
}
