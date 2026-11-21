package com.xbcl.personal_forum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开定时任务（W6 的超时关单要用）。
 *
 * <p><b>为什么需要这个注解：</b>@Scheduled 本身只是个标记，Spring 启动时如果没看到
 * @EnableScheduling，压根不会创建调度器 —— 方法上的注解会被静默忽略。
 * 这种"加了注解但没生效"最难查：日志里什么都没有，任务就是不跑。
 *
 * <p><b>默认调度器只有一个线程。</b>@EnableScheduling 会注册一个
 * ThreadPoolTaskScheduler（默认 poolSize = 1），所有 @Scheduled 方法共用它：
 * 超时关单这一轮要扫 200 单、跑 2 秒，W7 的什么缓存预热任务就得跟着排队 2 秒。
 * 现在只有一个任务，先不管；等出现第二个任务再回来加线程池：
 * <pre>
 * &#64;Bean
 * public ThreadPoolTaskScheduler taskScheduler() {
 *     ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
 *     s.setPoolSize(4);
 *     s.setThreadNamePrefix("forum-task-");
 *     s.setWaitForTasksToCompleteOnShutdown(true);   // 关服务时等手头的任务收尾
 *     return s;
 * }
 * </pre>
 * 但注意：线程池大了，同一个 @Scheduled 方法如果上一轮还没跑完，下一轮到点还会再进来一个 ——
 * 超时关单本身是幂等的（条件更新），多线程并发跑也不会关错单，只是白干活。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
