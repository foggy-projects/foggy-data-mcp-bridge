package com.foggyframework.dataset.resultset.support;

import com.foggyframework.core.ex.RX;

import java.util.function.Function;

public class CommandUtils {
	public static final <T> T execute(Function command, Object... args) {
		try {
			return (T) command.apply(args);
		} catch (IllegalArgumentException e) {
			throw RX.throwB(e);
		}
	}
}
