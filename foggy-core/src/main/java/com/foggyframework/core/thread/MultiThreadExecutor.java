package com.foggyframework.core.thread;


import com.foggyframework.core.ex.RX;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 使用完后，记得在finally中调 用waitAllCompleted(true);
 * 
 * @author oldseasoul
 *
 */
@Slf4j
public class MultiThreadExecutor {

	ThreadPoolExecutor executorService = null;

	public int maxQueueSize = 0;

	public int total = -1;

	private volatile Throwable error;

	public void setError(Throwable error) {
		this.error = error;
	}

	public Throwable getError() {
		return error;
	}

	public int getMaxQueueSize() {
		return maxQueueSize;
	}

	public void setMaxQueueSize(int maxQueueSize) {
		this.maxQueueSize = maxQueueSize;
	}

	public MultiThreadExecutor(int maxNum) {
		executorService = new ThreadPoolExecutor(maxNum, maxNum, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
	}

	public MultiThreadExecutor(int corePoolSize, int maximumPoolSize) {
		executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		
		maxQueueSize = executorService.getMaximumPoolSize();
	}
	public MultiThreadExecutor(int corePoolSize, int maximumPoolSize,int total) {
		this(corePoolSize, maximumPoolSize);
		this.total=total;
	}
	public static void main(String[] args) throws InterruptedException {
		MultiThreadExecutor mt = new MultiThreadExecutor(20);
		for (int i = 0; i < 20; i++) {
			Runnable syncRunnable = new Runnable() {
				@Override
				public void run() {
					System.err.println("XXXX:" + Thread.currentThread().getName());
				}
			};
			mt.execute(syncRunnable);
		}
		Thread.sleep(3000);
		mt.waitAllCompleted(true);
		System.out.println(mt.executorService);
	}

	public void waitAllCompleted(boolean shutdown) {
		waitAllCompleted(shutdown, false);
	}

	public void waitAllCompleted(boolean shutdown, boolean stopIfHasError) {
		long expectedTaskCount = total > 0 ? total : executorService.getTaskCount();
		if (shutdown) {
			executorService.shutdown();
		}
		try {
			if (shutdown) {
				while (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
					logProgress();
					throwIfHasError(stopIfHasError);
				}
			} else {
				while (executorService.getCompletedTaskCount() < expectedTaskCount) {
					Thread.sleep(1000);
					logProgress();
					throwIfHasError(stopIfHasError);
				}
			}
			throwIfHasError(stopIfHasError);
		} catch (InterruptedException e) {
			if (shutdown) {
				executorService.shutdownNow();
			}
			Thread.currentThread().interrupt();
			throw RX.throwB(e);
		} catch (RuntimeException e) {
			if (shutdown) {
				executorService.shutdownNow();
			}
			throw e;
		}
		if (log.isDebugEnabled()) {
			log.debug("所有任务执行完成【" + executorService + "】");
		}
	}

	private void logProgress() {
		log.debug("任务执行进度 active={}/queued={}/submitted={}", executorService.getActiveCount(),
				executorService.getQueue().size(), executorService.getTaskCount());
	}

	private void throwIfHasError(boolean stopIfHasError) {
		Throwable taskError = error;
		if (taskError != null && stopIfHasError) {
			throw new RuntimeException(taskError);
		}
	}

	/**
	 * 添加任务,如果executing列表的数量小于maxNum，则立即开始执行这个任务
	 * 
	 * @param run
	 * @return
	 */
	public MTask execute(Runnable run) {

		if (maxQueueSize > 0) {
			while (executorService.getQueue().size() > maxQueueSize) {
				// 待等 执行的任务 太多了，等 等 吧
				try {
					log.debug("待执行任务超过队列阈值，6 秒后重试 queued={}/threshold={}",
							executorService.getQueue().size(), maxQueueSize);
					Thread.sleep(6000);
				} catch (InterruptedException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
		}

		MTask task = new MTask(run, this);

		executorService.execute(task);

		if (log.isDebugEnabled()) {
			log.debug("加入任务，当前执行中的任务还有【" + executorService.getActiveCount() + "】个");
		}
		return task;
	}

	public Runnable execute(Runnable run, long wait) {

		if (maxQueueSize > 0) {
			while (executorService.getQueue().size() > maxQueueSize) {
				// 待等 执行的任务 太多了，等 等 吧
				try {
//					System.out.println("等 待执行的任务 太多了，等 gh rh后看看");
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
		}

//		MTask task = new MTask(run, this);
//		if (debug) {
//			logger.debug("加入任务，当前执行中的任务还有【" + executorService.getActiveCount() + "】个");
//		}
		executorService.execute(run);
		return run;
	}

	public int getActiveCount() {
		return executorService.getActiveCount();
	}
}
