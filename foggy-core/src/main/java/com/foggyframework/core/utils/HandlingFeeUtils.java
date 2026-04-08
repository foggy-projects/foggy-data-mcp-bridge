package com.foggyframework.core.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

/**
 * 手续费计算
 */
public final class HandlingFeeUtils {
    /**
     * 根据四舍五入来计算，精确到分
     *
     * @param billValue
     * @param rate
     * @return
     */
    public static int calRoundingHandlingFee(Integer billValue, double rate) {
        if (billValue == null) {
            return 0;
        }
        double v = billValue * rate;
        int fee = (int) Math.round(v);

        return fee;
    }

    /**
     * 和calRoundingHandlingFee类似,但多了个level,起因是客户有要求，货款手续费最小金额为1元，同时精度到元，而不是分
     * <p>
     * 例如HandlingFeeUtils.calRoundingHandlingFeeWithLevel(56300, rate,100,100)，返回200
     *
     * @param billValue
     * @param rate
     * @param level     为0表示精确到分、10为毛，100为元
     * @param minValue  最小金额
     * @return
     */
    public static int calRoundingHandlingFeeWithLevel(Integer billValue, double rate, int level, int minValue) {
        int fee = calRoundingHandlingFee(billValue, rate);
        if (level == 0) {
            return fee;
        }
        double v = fee * 1.0 / level;
        int nfee = (int) Math.round(v) * 100;
        return nfee > minValue ? nfee : minValue;
    }

    /**
     * 手续费均分模式，传入手续费fee，传入每个科目的费用，将fee均分到每个科目，返回每科目的手续费.要求计算出的科目手续费合数组必须等于传入的手续费fee
     * <p>
     * 手续费fee = 1000
     * 科目费用数组: [1000,0,2000,2000]
     * 得出手续费数组为 [200,0,400,400]
     */
    public static int[] reverseRoundingHandlingFeeList(int totalFee, int... values) {
        int totalValue = Arrays.stream(values).sum();
        int[] items = new int[values.length];

        double rate = totalFee / (totalValue * 1.0);
        int leftFee = totalFee;
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (value == 0) {
                continue;
            }
            int fee = (int) (value * rate);
            if (leftFee < fee) {
                items[i] = leftFee;
                //没了，结束
                leftFee = 0;
                break;
            }
            items[i] = fee;
            if (i == values.length - 1) {
                items[i] = leftFee;
                //最后一个
                leftFee = 0;
            } else {
                leftFee = leftFee - fee;
            }

        }
        if (leftFee > 0) {
            //如果还有剩余，直接给到最大的values
            int maxIdx = 0;
            int maxValue = 0;
            for (int i = items.length - 1; i >= 0; i--) {
                if (values[i] > maxValue) {
                    maxIdx = i;
                    maxValue = values[i];

                    break;
                }
            }
            items[maxIdx] = items[maxIdx] + leftFee;
            leftFee = 0;
        }

        return items;
    }
    /**
     * 手续费均分模式，传入手续费fee，传入每个科目的费用，将fee均分到每个科目，返回每科目的手续费.要求计算出的科目手续费合数组必须等于传入的手续费fee
     * <p>
     * 手续费fee = 1000
     * 科目费用数组: [1000,0,2000,2000]
     * 得出手续费数组为 [200,0,400,400]
     */
    public static long[] reverseRoundingHandlingFeeList(long totalFee, long... values) {
        long totalValue = Arrays.stream(values).sum();
        long[] items = new long[values.length];

        double rate = totalFee / (totalValue * 1.0);
        long leftFee = totalFee;
        for (int i = 0; i < values.length; i++) {
            long value = values[i];
            if (value == 0) {
                continue;
            }
            long fee = (int) (value * rate);
            if (leftFee < fee) {
                items[i] = leftFee;
                //没了，结束
                leftFee = 0;
                break;
            }
            items[i] = fee;
            if (i == values.length - 1) {
                items[i] = leftFee;
                //最后一个
                leftFee = 0;
            } else {
                leftFee = leftFee - fee;
            }

        }
        if (leftFee > 0) {
            //如果还有剩余，直接给到最大的values
            int maxIdx = 0;
            long maxValue = 0;
            for (int i = items.length - 1; i >= 0; i--) {
                if (values[i] > maxValue) {
                    maxIdx = i;
                    maxValue = values[i];

                    break;
                }
            }
            items[maxIdx] = items[maxIdx] + leftFee;
            leftFee = 0;
        }

        return items;
    }
    /**
     * 用于计算合单的手续费，例如values: [1,7,10]时，先会根据总合18算出一个手续费.
     * 然后再依次计算每票的手续费，当在计算中，若出现手续费为0时，则后续的运单手续费全部为0
     *
     * @param rate
     * @param values
     * @return
     */
    public static HandlingFeeResult calRoundingHandlingFeeList(double rate, Integer... values) {
        int totalValue = 0;

        for (Integer value : values) {
            totalValue = totalValue + (value == null ? 0 : value);
        }
        int totalFee = calRoundingHandlingFee(totalValue, rate);
        int[] items = new int[values.length];

        int leftFee = totalFee;
        for (int i = 0; i < values.length; i++) {
//            if(leftFee<=0){
//                break;
//            }
            Integer value = values[i];
            if (value == null) {
                continue;
            }

            int fee = calRoundingHandlingFee(value, rate);

            if (fee > leftFee) {
                //好吧，剩下的leftFee都不够分啦,跳出循环了
                items[i] = leftFee;
                leftFee = 0;
                break;
            } else {
                items[i] = fee;
                leftFee = leftFee - fee;
            }

        }
        if (leftFee > 0) {
            //如果有多出来的，全部扔给最后一笔退款的
            items[items.length - 1] = items[items.length - 1] + leftFee;
        }

        return new HandlingFeeResult(totalFee, items);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HandlingFeeResult {
        int totalFee;
        int[] feeList;
    }
}
