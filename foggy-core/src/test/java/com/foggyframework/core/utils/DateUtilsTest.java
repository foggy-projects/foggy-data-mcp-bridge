package com.foggyframework.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;

class DateUtilsTest {

    @Test
    void days() {

        Date date1 = new Date();
        Date date2 = new Date();

        int[] results = {
                DateUtils.days(date1, date2),
                DateUtils.days(date1, DateUtils.addDays(date1,1)),
                DateUtils.days(date1, DateUtils.addDays(date1,100)),
                DateUtils.days(date1, DateUtils.addDays(date1,2000)),
                DateUtils.days(date1, DateUtils.addDays(date1,-2000))
        };

        Assertions.assertEquals(0,results[0]);
        Assertions.assertEquals(1,results[1]);
        Assertions.assertEquals(100,results[2]);

        Assertions.assertEquals(2000,results[3]);
        Assertions.assertEquals(-2000,results[4]);
    }

    @Test
    void toStartMinute() {
        Date date = new Date();
        Date start = DateUtils.toStartMinute(date);
        Date end = DateUtils.toEndMinute(date);

        long xx = end.getTime() - start.getTime();
        System.err.println("toStartMinute: "+xx);

        Assertions.assertEquals(xx,60*1000);

        Assertions.assertEquals(start.getSeconds(),0);
        Assertions.assertEquals(end.getSeconds(),0);
    }

    @Test
    void toStartHour() {
        Date date = new Date();
        Date start = DateUtils.toStartHour(date);
        Date end = DateUtils.toEndHour(date);

        long xx = end.getTime() - start.getTime();
        System.err.println("toStartHour: "+xx);

        Assertions.assertEquals(xx,60*1000*60);

        Assertions.assertEquals(start.getSeconds(),0);
        Assertions.assertEquals(end.getSeconds(),0);

        Assertions.assertEquals(start.getMinutes(),0);
        Assertions.assertEquals(end.getMinutes(),0);
    }


    @Test
    public void testSubtractBetweenDatesReturnsCorrectDifference() {
        Calendar cal = Calendar.getInstance();
        cal.set(2022, Calendar.JANUARY, 15);
        Date startDate = cal.getTime();

        cal.set(2023, Calendar.JANUARY, 15);
        Date endDate = cal.getTime();

        // Test for MONTH field
        int monthDiff = DateUtils.subtract(Calendar.MONTH, endDate, startDate);
        Assertions.assertEquals(12, monthDiff, "Month difference should be 12");

        // Test for YEAR field
        int yearDiff = DateUtils.subtract(Calendar.YEAR, endDate, startDate);
        Assertions.assertEquals(1, yearDiff, "Year difference should be 1");

        // Test for DAY field
        cal.set(2022, Calendar.JANUARY, 16);
        Date nextDay = cal.getTime();
        int dayDiff = DateUtils.subtract(Calendar.DATE, nextDay, startDate);
        Assertions.assertEquals(1, dayDiff, "Day difference should be 1");
    }

    @Test
    public void testSubtractWhenEndDateIsBeforeStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2023, Calendar.JANUARY, 15);
        Date startDate = cal.getTime();

        cal.set(2022, Calendar.JANUARY, 15);
        Date endDate = cal.getTime();

        // Test for negative difference when end date is before start date
        int monthDiff = DateUtils.subtract(Calendar.MONTH, endDate, startDate);
        Assertions.assertEquals(-12, monthDiff, "Month difference should be negative when end date is before start date");
    }

    @Test
    public void testSubtractWithNullDatesReturnsZero() {
        // Both dates are null
        int result = DateUtils.subtract(Calendar.MONTH, (Date) null, null);
        Assertions.assertEquals(0, result, "Difference should be 0 when both dates are null");

        // Start date is null
        result = DateUtils.subtract(Calendar.MONTH, new Date(), null);
        Assertions.assertEquals(0, result, "Difference should be 0 when start date is null");

        // End date is null
        result = DateUtils.subtract(Calendar.MONTH, null, new Date());
        Assertions.assertEquals(0, result, "Difference should be 0 when end date is null");
    }
}
