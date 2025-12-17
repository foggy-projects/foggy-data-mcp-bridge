/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;

import com.github.sisyphsu.dateparser.DateParserUtils;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 在String与java.util.Date之间转换
 *
 * @author seasoul
 */
public class DateTransFormatter implements ObjectTransFormatter<Date> {
    // private static final transient Logger logger = Logger
    // .getLogger(SqlDateTransFormatter.class);
    // private final SimpleDateFormat format01 = new
    // SimpleDateFormat("yyyy-MM-dd");
    // private final SimpleDateFormat format02 = new
    // SimpleDateFormat("yyyy-mm-dd");
    // private final SimpleDateFormat format03 = new SimpleDateFormat(
    // "yyyy-MM-dd HH:mm:ss");

    public static void main(final String[] args) throws ParseException {
        System.err.println(ObjectTransFormatter.pattern01.matcher("08-1-01").find());
        System.err.println(ObjectTransFormatter.pattern03.matcher("9118-1-01").find());
        System.err.println(ObjectTransFormatter.pattern07.matcher("9118-01-01").find());

        System.err.println(new DateTransFormatter().format("2015-10-01 08:10:00"));
        System.err.println(new DateTransFormatter().format("2015-10-01 07"));
        System.err.println(ObjectTransFormatter.pattern04.matcher("2016-08-23 20:10").find());
        System.err.println(
                new Date(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2016-08-23 20:12").getTime()));

        ObjectTransFormatter.DATE_TRANSFORMATTERINSTANCE.format("2015-10");
    }

    public Object deserialize(final Object object) {
        // final SimpleDateFormat format01 = new
        // SimpleDateFormat("yyyy-MM-dd");
        if (object instanceof java.sql.Date date) {
            // logger.error("serialize java.util.Date : "+obj+" to
            // java.sql.Date ");
            // format01.f
            return new Date(date.getTime());
        } else if (object instanceof Timestamp time) {
            return new Date(time.getTime());
        } else if (object instanceof Time time) {
            return new Date(time.getTime());
        } else {
            // 无法识别的格式
            return object;
        }
    }

    @Override
    public Date format(final Object obj) {
        try {
            if (obj instanceof Date) {
                return (Date) obj;
            } else if (obj instanceof Long) {

                return new Date((Long) obj);
            } else if (obj instanceof String str) {
                if (pattern04.matcher(str).find()) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(str);
                } else if (pattern05.matcher(str).find()) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(str);
                } else if (str.length() > 20) {
                    return DateParserUtils.parseDate(str);
                } else if (pattern06.matcher(str).find() && str.length() >13) {
                    return new SimpleDateFormat("yyyy-MM-dd HH").parse(str);
                } else if (pattern01.matcher(str).find()) {
                    Date r = new SimpleDateFormat("yyyy-MM-dd").parse(str);
                    return r;
                } else if (pattern02.matcher(str).find()) {
                    return new SimpleDateFormat("yyyy-MM-dd").parse(str);
                } else if (pattern07.matcher(str).find()) {
                    return new SimpleDateFormat("yyyy-MM").parse(str);
                } else {

                }
            } else if (obj instanceof Integer) {

                return new Date((Integer) obj);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Date formatHMS(final Object obj) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) obj);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getName() {
        return "VARCHAR";
    }

    public final boolean isEmpty(final Object value) {
        if (value == null)
            return true;
        else if (value instanceof String) {
            return ((String) value).length() == 0;
        } else {
            return false;
        }
    }

    public int length() {
        return ObjectTransFormatter.UNKNOW_DATALENGTH;
    }

    @Override
    public Class<Date> type() {
        return Date.class;
    }

}
