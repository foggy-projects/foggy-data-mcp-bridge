package com.foggyframework.core.thread;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MTask implements Runnable {

	Runnable run;
	MultiThreadExecutor multiThreadExecutor;

	public MTask(Runnable run, MultiThreadExecutor multiThreadExecutor) {
		this.run = run;
		this.multiThreadExecutor = multiThreadExecutor;
	}

	@Override
	public void run() {

		try {
			run.run();
		} catch (Throwable t) {
			t.printStackTrace();
			multiThreadExecutor.setError(t);
		}
    }

}
