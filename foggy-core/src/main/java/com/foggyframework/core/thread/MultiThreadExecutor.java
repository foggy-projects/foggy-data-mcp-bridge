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

	private Throwable error;

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

	private boolean checkAllCompleted(){
		if(total>0){
			return executorService.getCompletedTaskCount() == total;
		}
		return executorService.getActiveCount() != 0;
	}

	public void waitAllCompleted(boolean shutdown, boolean stopIfHasError) {

		while (checkAllCompleted()) {
			try {
				Thread.sleep(1000);
				System.out.println("executorService.getActiveCount():"+executorService.getActiveCount()+"/"+executorService.getQueue().size()+"/"+executorService.getTaskCount());
				if (error != null && stopIfHasError) {
					throw new RuntimeException(error);
				}
			} catch (InterruptedException e) {
				throw RX.throwB(e);
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("所有任务执行完成【" + executorService + "】");
		}
		if (shutdown) {
			executorService.shutdownNow();
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
					System.out.println("等 待执行的任务 太多了，等 6秒后看看");
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
