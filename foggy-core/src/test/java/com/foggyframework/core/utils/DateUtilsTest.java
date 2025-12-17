package com.foggyframework.core.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

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

        Assert.assertEquals(0,results[0]);
        Assert.assertEquals(1,results[1]);
        Assert.assertEquals(100,results[2]);

        Assert.assertEquals(2000,results[3]);
        Assert.assertEquals(-2000,results[4]);
    }

    @Test
    void toStartMinute() {
        Date date = new Date();
        Date start = DateUtils.toStartMinute(date);
        Date end = DateUtils.toEndMinute(date);

        long xx = end.getTime() - start.getTime();
        System.err.println("toStartMinute: "+xx);

        Assert.assertEquals(xx,60*1000);

        Assert.assertEquals(start.getSeconds(),0);
        Assert.assertEquals(end.getSeconds(),0);
//        Instant.
//        Assert.assertEquals();

    }

    @Test
    void toStartHour() {
        Date date = new Date();
        Date start = DateUtils.toStartHour(date);
        Date end = DateUtils.toEndHour(date);

        long xx = end.getTime() - start.getTime();
        System.err.println("toStartHour: "+xx);

        Assert.assertEquals(xx,60*1000*60);

        Assert.assertEquals(start.getSeconds(),0);
        Assert.assertEquals(end.getSeconds(),0);

        Assert.assertEquals(start.getMinutes(),0);
        Assert.assertEquals(end.getMinutes(),0);
//        Instant.
//        Assert.assertEquals();

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
        Assert.assertEquals("Month difference should be 12", 12, monthDiff);

        // Test for YEAR field
        int yearDiff = DateUtils.subtract(Calendar.YEAR, endDate, startDate);
        Assert.assertEquals("Year difference should be 1", 1, yearDiff);

        // Test for DAY field
        cal.set(2022, Calendar.JANUARY, 16);
        Date nextDay = cal.getTime();
        int dayDiff = DateUtils.subtract(Calendar.DATE, nextDay, startDate);
        Assert.assertEquals("Day difference should be 1", 1, dayDiff);
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
        Assert.assertEquals("Month difference should be negative when end date is before start date", -12, monthDiff);
    }

    @Test
    public void testSubtractWithNullDatesReturnsZero() {
        // Both dates are null
        int result = DateUtils.subtract(Calendar.MONTH, (Date) null, null);
        Assert.assertEquals("Difference should be 0 when both dates are null", 0, result);

        // Start date is null
        result = DateUtils.subtract(Calendar.MONTH, new Date(), null);
        Assert.assertEquals("Difference should be 0 when start date is null", 0, result);

        // End date is null
        result = DateUtils.subtract(Calendar.MONTH, null, new Date());
        Assert.assertEquals("Difference should be 0 when end date is null", 0, result);
    }
}