package com.foggyframework.core.common;

public interface State {
    Integer getState();

    default boolean hasState(Integer state) {
        if (state == null) {
            state = 0;
        }
        return state == getState();
    }

    /**
     * 只要有一个状态相符合，即返回真
     *
     * @param states
     * @return
     */
    default boolean hasState(int... states) {
        Integer state = getState();
        for (int s : states) {
            if (hasState(s)) {
                return true;
            }
        }
        return false;
    }
}
