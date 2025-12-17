/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils;

import com.foggyframework.core.trans.ObjectTransFormatter;
import lombok.extern.slf4j.Slf4j;

import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Slf4j
public final class DateUtils {
//    static final Date date = new Date();

    static final Format format = new SimpleDateFormat("yyyy-MM-dd");

    public static final int[][] quarter_end_day = {{2, 31}, {5, 30}, {8, 30}, {11, 31}};
    public static final int[][] quarter_start_day = {{0, 1}, {3, 1}, {6, 1}, {9, 1}};
    public static final int[] month_in_quarter = {1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4};
    public static final String[][] quarter_has_month = {{"01", "02", "03"}, {"04", "05", "06"},
            {"07", "08", "09"}, {"10", "11", "12"}};

    public static Date toEndTime(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.HOUR_OF_DAY, 24);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 2022-01-01 00:20:30  -> 2022-01-01 00:20:00
     *
     * @param t
     * @return
     */
    public static Date toStartMinute(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 2022-01-01 00:20:30  -> 2022-01-01 00:21:00
     *
     * @param t
     * @return
     */
    public static Date toEndMinute(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.SECOND, 60);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 2022-01-01 00:20:30  -> 2022-01-01 00:20:00
     *
     * @param t
     * @return
     */
    public static Date toStartHour(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 2022-01-01 00:20:30  -> 2022-01-01 00:21:00
     *
     * @param t
     * @return
     */
    public static Date toEndHour(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.MINUTE, 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public static Date toStartTime(Date t) {
        Calendar c = Calendar.getInstance();
        c.setTime(t);// t.getCalendarDate().setHours(24);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public static Date add(int field, int amount, Date date) {
        Calendar ec = Calendar.getInstance();
//		int d = Calendar.MINUTE;
        ec.setTime(date);
        ec.add(field, amount);
        return ec.getTime();
    }

    public static String format2Month(Date d) {
        SimpleDateFormat xx = new SimpleDateFormat("yyyy-MM");
        return xx.format(d);
    }

    public static void setHMSMZero(Calendar d) {
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
    }

    /**
     * 设置成每月1号00:00
     *
     * @param d
     */
    public static void setDHMSMZero(Calendar d) {
        d.set(Calendar.DAY_OF_MONTH, 1);
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
    }

    /**
     * 获取从1970开始到现在的天数
     *
     * @param d
     * @return
     */
    public static int getDays(Date d) {
        Double x = d.getTime() / (1000.0 * 60 * 60 * 24);

        return new Double(Math.ceil(x)).intValue();
    }

    public static Date getDate(final String str) {
        try {
            final Date dt = (Date) DateUtils.format.parseObject(str);
            return dt;
        } catch (final ParseException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public static Calendar getCalendar(final String str) {
        try {
            final Date dt = (Date) DateUtils.format.parseObject(str);
            final Calendar c = Calendar.getInstance();
            c.setTime(dt);
            return c;
        } catch (final ParseException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public static Calendar getEndDayOfMonth(final int year, final int month) {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        return c;
    }

    public static Calendar getEndDayOfQuarter(final int year, final int quarter) {
        final Calendar c = Calendar.getInstance();
        c.set(year, DateUtils.quarter_end_day[quarter - 1][0], DateUtils.quarter_end_day[quarter - 1][1]);
        return c;
    }

    public static Calendar getFirstDayOfMonth(final int year, final int month) {
        final Calendar c = Calendar.getInstance();

        c.set(year, month - 1, 1);
        return c;
    }

    public static Calendar getFirstDayOfQuarter(final int year, final int quarter) {
        final Calendar c = Calendar.getInstance();
        c.set(year, (quarter - 1) * 3, 1);
        return c;
    }

    /**
     * @param year
     * @param month
     * @return
     */
    public static int getMonthDays(final int year, final int month) {
        final Calendar c = Calendar.getInstance();
        c.set(year, month - 1, 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * @param quarter
     * @return
     */
    public static int getQuarterDays(final int year, int quarter) {
        final Calendar c = Calendar.getInstance();
        quarter--;
        int start = 1;
        c.set(year, DateUtils.quarter_end_day[quarter][0], DateUtils.quarter_end_day[quarter][1]);
        final int end = c.get(Calendar.DAY_OF_YEAR);
        if (quarter != 0) {
            c.set(year, DateUtils.quarter_end_day[quarter - 1][0], DateUtils.quarter_end_day[quarter - 1][1]);
            c.add(Calendar.DAY_OF_MONTH, 1);
            start = c.get(Calendar.DAY_OF_YEAR);
        }
        return end - start + 1;
    }

    /**
     * @param c1
     * @param c2
     * @return
     */
    public static boolean isSameMonth(final Calendar c1, final Calendar c2) {
        return c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH);
    }

    /**
     * 判断两个日期是否同一天
     */
    public static boolean isSameDate(Date d1, Date d2) {
        if (null == d1 || null == d2)
            return false;
        if (Math.abs(d1.getTime() - d2.getTime()) > 3600000 * 24)
            return false;
        Calendar c1 = Calendar.getInstance();
        c1.setTime(d1);
        Calendar c2 = Calendar.getInstance();
        c1.setTime(d2);
        return c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH);
    }


    /**
     * @param c1
     * @param c2
     * @return
     */
    public static boolean isSameMonthAndYear(final Calendar c1, final Calendar c2) {
        return DateUtils.isSameYear(c1, c2) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH);
    }

    public static boolean isSameYear(final Calendar c1, final Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR);
    }

    /**
     * 检查当前时间是否超过大于等于date。
     *
     * @param date
     * @return
     */
    public static boolean isTimeup(Date date) {
        if (date == null) {
            return true;
        }
        return System.currentTimeMillis() >= date.getTime();
    }

    /**
     * 如果传入的date是今天，则返回v1,否则v2
     *
     * @param date
     * @param v1
     * @param v2
     * @return
     */

    public static Object isToday(Object date, Object v1, Object v2) {
        if (date == null) {
            return false;
        }
        Date d = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(date);
        Date cd = new Date();
        return (d.getYear() == cd.getYear() && d.getMonth() == cd.getMonth() && d.getDate() == cd.getDate()) ? v1 : v2;
    }

    /**
     * 如果date是今天之前的时间，返回v1,否则v2
     *
     * @param date
     * @param v1
     * @param v2
     * @return
     */
    public static Object isBeforeToday(Object date, Object v1, Object v2) {
        if (date == null) {
            return v2;
        }
        Date d = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(date);

        Date cd = new Date();
        Date todayStart = toDayStart(cd);
        return todayStart.getTime() > d.getTime() ? v1 : v2;
    }

    public static boolean isTimeup(Date date, boolean nullValue) {
        if (date == null) {
            return nullValue;
        }
        return System.currentTimeMillis() >= date.getTime();
    }

    public static void main(String[] args) throws ParseException {

        //checkDateOutTomorrow9();

//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:00"));
//
//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:01"));
//
//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 04:00:00"));
//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:00"),
//				DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:00"));
//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:00"),
//				DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-03 00:00:00"));
//		xx(DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-03 00:00:00"),
//				DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-02-06 00:00:01"));

//		System.out
//				.println(DateUtils.subtract(Calendar.MONTH, DateUtils.add(Calendar.MONTH, 1, new Date()), new Date()));

//        long x1 = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-04-01 00:00:00").getTime();
//        long x2 = ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2018-03-31 23:59:59 ").getTime();
//
//        Calendar x = Calendar.getInstance();
//        x.setTimeInMillis(x1);
//        System.err.println(x.get(Calendar.DAY_OF_MONTH));
//
//        System.out.println(x1);
//        System.out.println(x2);
    }

//	public static String xx() {
//		Double dd = d.getTime() / (1000.0 * 60 * 60 * 24);
//		String x = "2018-02-06 04:01:00 : " + dd + " , " + Math.ceil(dd);
//		System.err.println(x);
//
//		return x;
//	}

    /**
     * 获取date1~date2的天数，注意，是按自然时间来算的天数，而不是时间差
     *
     * @param date1
     * @param date2
     * @return
     */
    public static int days(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);

        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        int day1 = cal1.get(Calendar.DAY_OF_YEAR);
        int day2 = cal2.get(Calendar.DAY_OF_YEAR);

        int year1 = cal1.get(Calendar.YEAR);
        int year2 = cal2.get(Calendar.YEAR);
        if (year1 != year2) // 同一年
        {
            int timeDistance = 0;
            if (year1 < year2) {
                for (int i = year1; i < year2; i++) {
                    if (i % 4 == 0 && i % 100 != 0 || i % 400 == 0) // 闰年
                    {
                        timeDistance += 366;
                    } else // 不是闰年
                    {
                        timeDistance += 365;
                    }
                }
            } else {
                for (int i = year2; i < year1; i++) {
                    if (i % 4 == 0 && i % 100 != 0 || i % 400 == 0) // 闰年
                    {
                        timeDistance -= 366;
                    } else // 不是闰年
                    {
                        timeDistance -= 365;
                    }
                }
            }


            return timeDistance + (day2 - day1);
        } else // 不同年
        {
//			System.out.println("判断day2 - day1 : " + (day2 - day1));
            return day2 - day1;
        }
    }

    public static Date max(Date date1, Date date2) {
        if (date1 == null) {
            return date2;
        }
        if (date2 == null) {
            return date1;
        }
        if (date1.getTime() > date2.getTime()) {
            return date1;
        }
        return date2;
    }

    public static long sub(Date date1, Date date2) {
        long s1 = 0;
        long s2 = 0;
        if (date1 != null) {
//			return date2;
            s1 = date1.getTime();
        }
        if (date2 != null) {
            s2 = date2.getTime();
        }

        return s1 - s2;
    }

    /**
     * @param field
     * @param endDate
     * @param startDate
     * @return
     */
    public static int subtract(int field, Calendar endDate, Calendar startDate) {
        if (endDate == null || startDate == null) {
            return 0;
        }
        switch (field) {
            case Calendar.MONTH:
                int years = endDate.get(Calendar.YEAR) - startDate.get(Calendar.YEAR);
                int m = endDate.get(Calendar.MONTH) - startDate.get(Calendar.MONTH);
                int mm = years * 12 + m;
                return mm;
        }
        throw new UnsupportedOperationException();
    }
    public static int subtractCalendar(int field, Calendar endDate, Calendar startDate) {
        return subtract(field,endDate,startDate);
    }

    /**
     * 获取两个日期之间的相差,例如field为MONTH时,返回相差的月份
     *
     * @param field
     * @param endDate
     * @param startDate
     * @return
     */
    public static int subtract(int field, Date endDate, Date startDate) {
        if (endDate == null || startDate == null) {
            return 0;
        }
        switch (field) {
            case Calendar.YEAR:
                return endDate.getYear() - startDate.getYear();
            case Calendar.DATE:
                long x = endDate.getTime() - startDate.getTime();
                double d = x / (1000.0 * 60 * 60 * 24);

                return new Double(Math.ceil(d)).intValue();
            case Calendar.HOUR:
                long x1 = endDate.getTime() - startDate.getTime();
                double d1 = x1 / (1000.0 * 60 * 60);
                return new Double(Math.ceil(d1)).intValue();
        }
        Calendar ec = Calendar.getInstance();
        ec.setTime(endDate);
        Calendar sc = Calendar.getInstance();
        sc.setTime(startDate);
        return subtract(field, ec, sc);
    }
    public static int subtractDate(int field, Date endDate, Date startDate) {
        return subtract(field,endDate,startDate);
    }

    public static Date toDate(String str) {
        return ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(str);
    }

    /**
     * 是否凌晨00：00
     *
     * @param calendar
     * @return
     */
    public static boolean is0000(Calendar calendar) {
        return calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.MILLISECOND) == 0;
    }

    /**
     * 每月1号 凌晨00：00
     *
     * @param calendar
     * @return
     */
    public static boolean isM0000(Calendar calendar) {

        return calendar.get(Calendar.DAY_OF_MONTH) == 1 && calendar.get(Calendar.HOUR_OF_DAY) == 0
                && calendar.get(Calendar.MINUTE) == 0 && calendar.get(Calendar.MILLISECOND) == 0;
    }

    /**
     * yyyy-MM-dd
     *
     * @param date
     * @return
     */
    public static String toString(Date date) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat xx = new SimpleDateFormat("yyyy-MM-dd");
        return xx.format(date);
    }

    /**
     * @param xdate
     * @param months
     * @return
     */
    public static Date addMonths(Date xdate, int months) {
//		Date currentDate = FoggyRuntime.currentDate();
        if (xdate == null) {
            xdate = new Date();
        }
//		else if (xdate.getTime() < currentDate.getTime()) {
//			xdate = currentDate;
//		}
        Calendar c = Calendar.getInstance();
        c.setTime(xdate);
        c.add(Calendar.MONTH, months);
        return c.getTime();

    }

    public static Date addMins(Date xdate, int mins) {
//		Date currentDate = FoggyRuntime.currentDate();
        if (xdate == null) {
            xdate = new Date();
        }
//		else if (xdate.getTime() < currentDate.getTime()) {
//			xdate = currentDate;
//		}
        Calendar c = Calendar.getInstance();
        c.setTime(xdate);
        c.add(Calendar.MINUTE, mins);
        return c.getTime();

    }

    public static Date addSeconds(Date xdate, int sec) {
//		Date currentDate = FoggyRuntime.currentDate();
        if (xdate == null) {
            xdate = new Date();
        }
//		else if (xdate.getTime() < currentDate.getTime()) {
//			xdate = currentDate;
//		}
        Calendar c = Calendar.getInstance();
        c.setTime(xdate);
        c.add(Calendar.SECOND, sec);
        return c.getTime();

    }

    public static Date addDays(Date xdate, int days) {
//		Date currentDate = null;//FoggyRuntime.currentDate();
        if (xdate == null) {
            xdate = new Date();
        }
//		else if (xdate.getTime() < currentDate.getTime()) {
//			xdate = currentDate;
//		}
        Calendar c = Calendar.getInstance();
        c.setTime(xdate);
        c.add(Calendar.DATE, days);

        return c.getTime();

    }

    public static Date addHours(Date xdate, int hours) {
//		Date currentDate = FoggyRuntime.currentDate();
        if (xdate == null) {
            xdate = new Date();
        }
//		else if (xdate.getTime() < currentDate.getTime()) {
//			xdate = currentDate;
//		}
        Calendar c = Calendar.getInstance();
        c.setTime(xdate);
        c.add(Calendar.HOUR, hours);
        return c.getTime();

    }

    public static String getLastMonthStr() {
        Calendar c = Calendar.getInstance();
//		c.setTime(l);
        c.add(Calendar.MONTH, -1);
        String last_month = DateUtils.format2Month(c.getTime());

        return last_month;
    }

    /**
     * 设置成当天的起始时间，00：00：00
     *
     * @param date
     * @return
     */
    public static Date toDayStart(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);

        setHMSMZero(c);
//		startTime.set
        return c.getTime();
    }


    /**
     * 提供给fpage用来判断两个字符串日期相隔的天数
     *
     * @param date1 startTime
     * @param date2 endTime
     * @return int 相隔的天数
     */
    public static int daysDiffe(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return 1;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date p1 = null;
        try {
            p1 = sdf.parse(date1);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Date p2 = null;
        try {
            p2 = sdf.parse(date2);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        int diffe = (int) ((p2.getTime() - p1.getTime()) / (1000 * 3600 * 24));

        return Math.abs(diffe);
    }

    /**
     * startCalendar~endCalendar,分割成小时,这个需要调用者保证startCalendar和endCalendar在同一天，否则会出现循环
     *
     * @param startCalendar
     * @param endCalendar
     * @return
     */
    public static List<Integer> splitHours(Calendar startCalendar, Calendar endCalendar) {
        List<Integer> result = new ArrayList<>();
        startCalendar = (Calendar) startCalendar.clone();
        while (endCalendar.compareTo(startCalendar) > 0) {

            result.add(startCalendar.get(Calendar.HOUR_OF_DAY));

            startCalendar.add(Calendar.HOUR_OF_DAY, 1);
        }

        return result;
    }
}
