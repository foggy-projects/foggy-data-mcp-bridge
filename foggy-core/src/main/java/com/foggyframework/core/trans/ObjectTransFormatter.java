/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.trans;


import java.util.regex.Pattern;

public interface ObjectTransFormatter<T> {
    class BooleanTransFormatter implements ObjectTransFormatter<Boolean> {

        @Override
        public Boolean format(final Object object) {
            if (object == null) {
                return (Boolean) object;
            }

            if (object instanceof Number x) {
                // TODO 存在BUG,需要更好的判断
                return x.floatValue() != 0;
            }
            if (object instanceof Boolean) {
                return (Boolean) object;
            } else {
                String s = object.toString().trim();
                if (s.isEmpty() || s.equalsIgnoreCase("false") || s.equals("0")) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            }
        }

        @Override
        public Class<Boolean> type() {
            return Boolean.class;
        }
    }

    Byte byte1 = Byte.valueOf((byte) 1);
    Byte byte0 = Byte.valueOf((byte) 0);

    class ByteTransFormatter implements ObjectTransFormatter<Byte> {

        @Override
        public Byte format(final Object object) {
            if (object instanceof String) {
                if (((String) object).trim().length() == 0) {
                    return Byte.valueOf("0");
                }
                return Byte.valueOf((String) object);
            } else if (object instanceof Byte) {
                return (Byte) object;
            } else if (object instanceof Boolean) {
                return ((Boolean) object).booleanValue() ? byte1 : byte0;
            } else if (object == null) {
                return null;
            } else {
                return Byte.valueOf(object.toString());
            }
        }

        @Override
        public Class<Byte> type() {
            return Byte.class;
        }

    }

    /**
     * boolean
     *
     * @author Foggy
     */
    class SBooleanTransFormatter implements ObjectTransFormatter {

        @Override
        public Boolean format(final Object object) {
            if (object == null) {
                return false;
            }

            return BOOLEAN_TRANSFORMATTERINSTANCE.format(object);
        }

        @Override
        public Class type() {
            return Boolean.class;
        }
    }

    class SDoubleTransFormatter implements ObjectTransFormatter {

        @Override
        public Double format(final Object object) {
            if (object == null) {
                return 0.0;
            }

            return DOUBLE_TRANSFORMATTERINSTANCE.format(object);
        }

        @Override
        public Class type() {
            return Double.class;
        }
    }

    class SFloatTransFormatter implements ObjectTransFormatter {

        @Override
        public Float format(final Object object) {
            if (object == null) {
                return new Float(0);
            }

            return FLOAT_TRANSFORMATTERINSTANCE.format(object);
        }

        @Override
        public Class type() {
            return Float.class;
        }
    }

    class SIntegerTransFormatter implements ObjectTransFormatter {

        @Override
        public Integer format(final Object object) {
            if (object == null)
                return 0;
            return INTEGER_TRANSFORMATTERINSTANCE.format(object);
        }

        @Override
        public Class type() {
            return Integer.class;
        }
    }

    class StringTransFormatter implements ObjectTransFormatter<String> {

        @Override
        public String format(final Object object) {
            return object == null ? null : object.toString();
        }

        @Override
        public Class<String> type() {
            return String.class;
        }

    }

    Pattern pattern01 = Pattern.compile("\\d{2}-\\d{1,2}-\\d{1,2}");

    Pattern pattern02 = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");

    Pattern pattern03 = Pattern.compile("\\d{4}\\d{1,2}\\d{1,2}");

    Pattern pattern04 = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}\\s*\\d{1,2}:\\d{1,2}:\\d{1,2}");

    Pattern pattern05 = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}\\s*\\d{1,2}:\\d{1,2}");

    Pattern pattern06 = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}\\s*\\d{1,2}");

    Pattern pattern07 = Pattern.compile("\\d{4}-\\d{2,2}");

    ByteTransFormatter BYTE_TRANSFORMATTERINSTANCE = new ByteTransFormatter();

    BigDecimalTransFormatter BIGDECIMAL_TRANSFORMATTERINSTANCE = new BigDecimalTransFormatter();
    DoubleTransFormatter DOUBLE_TRANSFORMATTERINSTANCE = new DoubleTransFormatter();
    FloatTransFormatter FLOAT_TRANSFORMATTERINSTANCE = new FloatTransFormatter();
    IntegerTransFormatter INTEGER_TRANSFORMATTERINSTANCE = new IntegerTransFormatter();
    SqlDateTransFormatter SQL_DATE_TRANSFORMATTERINSTANCE = new SqlDateTransFormatter();
    NumberTransFormatter NUMBER_TRANSFORMATTERINSTANCE = new NumberTransFormatter();
    DateTransFormatter DATE_TRANSFORMATTERINSTANCE = new DateTransFormatter();
    StringTransFormatter STRING_TRANSFORMATTERINSTANCE = new StringTransFormatter();
    BooleanTransFormatter BOOLEAN_TRANSFORMATTERINSTANCE = new BooleanTransFormatter();
    SBooleanTransFormatter SBOOLEAN_TRANSFORMATTERINSTANCE = new SBooleanTransFormatter();
    LongTransFormatter LONG_TRANSFORMATTERINSTANCE = new LongTransFormatter();

    int UNKNOW_INDEX = -1;

    int UNKNOW_DATALENGTH = -1;

    int UNKNOW_DATATYPE = -1;

    /**
     * 将用户输入值转换为目标识别值
     *
     * @param object
     * @return
     */
    T format(Object object);

    Class<?> type();

    // /**
    // * 将用户输入值转换为目标识别值
    // *
    // * @param object
    // * @return
    // */
    // public T format(String object);
}
