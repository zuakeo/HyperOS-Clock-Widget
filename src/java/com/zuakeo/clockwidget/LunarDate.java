package com.zuakeo.clockwidget;

import java.util.Calendar;

/**
 * 公历 -> 农历（1900-2100）。
 *
 * 只做一件事：给出「九月三十」这种中文农历日，供组件日期行使用。
 * 表是通用的 lunarInfo 压缩表：低 4 位 = 闰月月份（0 表示无闰月），
 * bit4..bit15 = 十二个月的大小月（1 = 30 天，0 = 29 天），
 * bit16 = 闰月大小（1 = 30 天）。
 *
 * 注意：本类不依赖任何第三方库，也不联网，纯本地换算。
 */
final class LunarDate {

    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520                                                                                    // 2100
    };

    private static final String[] MONTH_NAMES = {
            "正月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "冬月", "腊月"
    };

    private static final String[] DAY_NAMES = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private LunarDate() {
    }

    /** 当天农历，例如「九月三十」「闰四月廿一」。超出 1900-2100 返回空串。 */
    static String format(long millis) {
        try {
            Calendar target = Calendar.getInstance();
            target.setTimeInMillis(millis);
            int y = target.get(Calendar.YEAR);
            int m = target.get(Calendar.MONTH) + 1;
            int d = target.get(Calendar.DAY_OF_MONTH);

            Calendar base = Calendar.getInstance();
            base.clear();
            base.set(1900, Calendar.JANUARY, 31);

            Calendar day = Calendar.getInstance();
            day.clear();
            day.set(y, m - 1, d);

            long offset = Math.round((day.getTimeInMillis() - base.getTimeInMillis()) / 86400000d);
            if (offset < 0) {
                return "";
            }

            int lunarYear = 1900;
            while (lunarYear <= 2100) {
                int yearDays = yearDays(lunarYear);
                if (offset < yearDays) {
                    break;
                }
                offset -= yearDays;
                lunarYear++;
            }
            if (lunarYear > 2100) {
                return "";
            }

            int leapMonth = leapMonth(lunarYear);
            int month = 1;
            boolean isLeap = false;
            int dayOfMonth = 1;
            while (true) {
                int days = isLeap ? leapDays(lunarYear) : monthDays(lunarYear, month);
                if (offset < days) {
                    dayOfMonth = (int) offset + 1;
                    break;
                }
                offset -= days;

                // 闰月紧跟在同号月之后
                if (!isLeap && leapMonth == month) {
                    isLeap = true;
                } else {
                    isLeap = false;
                    month++;
                }
            }

            String prefix = isLeap ? "闰" : "";
            return prefix + MONTH_NAMES[month - 1] + DAY_NAMES[dayOfMonth - 1];
        } catch (Throwable t) {
            return "";
        }
    }

    /** 该农历年闰几月，0 表示不闰。 */
    private static int leapMonth(int year) {
        return LUNAR_INFO[year - 1900] & 0xf;
    }

    /** 闰月天数。 */
    private static int leapDays(int year) {
        if (leapMonth(year) == 0) {
            return 0;
        }
        return ((LUNAR_INFO[year - 1900] & 0x10000) != 0) ? 30 : 29;
    }

    /** 农历某月天数。 */
    private static int monthDays(int year, int month) {
        return ((LUNAR_INFO[year - 1900] & (0x10000 >> month)) != 0) ? 30 : 29;
    }

    /** 农历一年总天数（含闰月）。 */
    private static int yearDays(int year) {
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            if ((LUNAR_INFO[year - 1900] & i) != 0) {
                sum++;
            }
        }
        return sum + leapDays(year);
    }
}
