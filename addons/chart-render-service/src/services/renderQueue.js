const logger = require('../utils/logger');
const { TimeoutError } = require('../middleware/errorHandler');

/**
 * 渲染队列管理器
 * 控制并发渲染数量，避免系统过载
 */
class RenderQueue {
  constructor(maxConcurrent = 10) {
    this.maxConcurrent = maxConcurrent;
    this.running = 0;
    this.queue = [];
    this.completed = 0;
    this.failed = 0;
    this.totalWaitTime = 0;
    this.totalRenderTime = 0;
  }

  /**
   * 添加渲染任务到队列
   * @param {Function} renderFunction 渲染函数
   * @param {number} timeout 超时时间 (毫秒)
   * @returns {Promise} 渲染结果
   */
  async add(renderFunction, timeout = 30000) {
    return new Promise((resolve, reject) => {
      const task = {
        id: this.generateTaskId(),
        renderFunction,
        timeout,
        resolve,
        reject,
        createdAt: Date.now(),
        startedAt: null
      };

      this.queue.push(task);
      this.processQueue();

      logger.debug('Task added to render queue', {
        taskId: task.id,
        queueLength: this.queue.length,
        running: this.running
      });
    });
  }

  /**
   * 处理队列中的任务
   */
  async processQueue() {
    if (this.running >= this.maxConcurrent || this.queue.length === 0) {
      return;
    }

    const task = this.queue.shift();
    this.running++;
    task.startedAt = Date.now();

    const waitTime = task.startedAt - task.createdAt;
    this.totalWaitTime += waitTime;

    logger.debug('Starting render task', {
      taskId: task.id,
      waitTime: `${waitTime}ms`,
      running: this.running,
      queueLength: this.queue.length
    });

    try {
      // 创建超时Promise
      const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => {
          reject(new TimeoutError(`Render timeout after ${task.timeout}ms`));
        }, task.timeout);
      });

      // 竞争执行
      const result = await Promise.race([
        task.renderFunction(),
        timeoutPromise
      ]);

      const renderTime = Date.now() - task.startedAt;
      this.totalRenderTime += renderTime;
      this.completed++;

      logger.debug('Render task completed', {
        taskId: task.id,
        renderTime: `${renderTime}ms`,
        waitTime: `${waitTime}ms`
      });

      task.resolve(result);
    } catch (error) {
      this.failed++;

      logger.logError(error, {
        taskId: task.id,
        waitTime: `${waitTime}ms`,
        renderTime: task.startedAt ? `${Date.now() - task.startedAt}ms` : 'unknown'
      });

      task.reject(error);
    } finally {
      this.running--;
      // 继续处理队列
      setImmediate(() => this.processQueue());
    }
  }

  /**
   * 获取队列状态
   * @returns {Object} 队列状态信息
   */
  getStatus() {
    const total = this.completed + this.failed;
    const avgWaitTime = total > 0 ? Math.round(this.totalWaitTime / total) : 0;
    const avgRenderTime = this.completed > 0 ? Math.round(this.totalRenderTime / this.completed) : 0;

    return {
      maxConcurrent: this.maxConcurrent,
      running: this.running,
      queued: this.queue.length,
      completed: this.completed,
      failed: this.failed,
      total,
      successRate: total > 0 ? Math.round((this.completed / total) * 100) : 0,
      avgWaitTime: `${avgWaitTime}ms`,
      avgRenderTime: `${avgRenderTime}ms`
    };
  }

  /**
   * 清空队列 (紧急情况使用)
   */
  clear() {
    const cancelledTasks = this.queue.length;

    // 拒绝所有排队的任务
    this.queue.forEach(task => {
      task.reject(new Error('Queue cleared'));
    });

    this.queue = [];

    logger.warn('Render queue cleared', {
      cancelledTasks,
      runningTasks: this.running
    });

    return {
      cancelledTasks,
      runningTasks: this.running
    };
  }

  /**
   * 生成任务ID
   * @returns {string} 任务ID
   */
  generateTaskId() {
    return `render_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * 获取性能统计
   * @returns {Object} 性能统计信息
   */
  getPerformanceStats() {
    const total = this.completed + this.failed;

    return {
      totalTasks: total,
      completedTasks: this.completed,
      failedTasks: this.failed,
      runningTasks: this.running,
      queuedTasks: this.queue.length,
      successRate: total > 0 ? (this.completed / total) * 100 : 0,
      avgWaitTime: total > 0 ? this.totalWaitTime / total : 0,
      avgRenderTime: this.completed > 0 ? this.totalRenderTime / this.completed : 0,
      totalWaitTime: this.totalWaitTime,
      totalRenderTime: this.totalRenderTime
    };
  }
}

module.exports = RenderQueue;