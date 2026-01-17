package com.foggyframework.dataset.db.model.cache.eviction;

import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 缓存自动失效 AOP 切面
 * <p>
 * 处理 {@link EvictQueryCache} 注解，在数据变更操作后自动清除相关缓存。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class CacheEvictionAspect {

    private final QueryCacheProvider queryCacheProvider;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 处理 beforeInvocation=false（默认）的缓存清除
     * <p>
     * 方法成功返回后清除缓存。
     * </p>
     */
    @AfterReturning(
            pointcut = "@annotation(evictQueryCache) && !@annotation(com.foggyframework.dataset.db.model.cache.eviction.EvictQueryCache(beforeInvocation=true))",
            returning = "result"
    )
    public void evictCacheAfterReturning(JoinPoint joinPoint, EvictQueryCache evictQueryCache, Object result) {
        if (!evictQueryCache.beforeInvocation()) {
            doEvict(joinPoint, evictQueryCache, result);
        }
    }

    /**
     * 处理 beforeInvocation=true 或 onlyOnSuccess=false 的缓存清除
     */
    @Around("@annotation(evictQueryCache)")
    public Object evictCacheAround(ProceedingJoinPoint joinPoint, EvictQueryCache evictQueryCache) throws Throwable {
        // beforeInvocation=true: 方法执行前清除
        if (evictQueryCache.beforeInvocation()) {
            doEvict(joinPoint, evictQueryCache, null);
        }

        try {
            Object result = joinPoint.proceed();

            // 默认情况下 AfterReturning 会处理
            // 这里不需要重复处理

            return result;
        } catch (Throwable ex) {
            // onlyOnSuccess=false: 即使异常也清除
            if (!evictQueryCache.onlyOnSuccess() && !evictQueryCache.beforeInvocation()) {
                doEvict(joinPoint, evictQueryCache, null);
            }
            throw ex;
        }
    }

    /**
     * 执行缓存清除
     */
    private void doEvict(JoinPoint joinPoint, EvictQueryCache evictQueryCache, Object result) {
        try {
            // evictAll 优先
            if (evictQueryCache.evictAll()) {
                log.info("Evicting all query cache due to @EvictQueryCache on {}",
                        getMethodName(joinPoint));
                queryCacheProvider.evictAll();
                return;
            }

            // 收集需要清除的模型
            Set<String> modelsToEvict = new HashSet<>();

            // 从 models 属性获取
            for (String model : evictQueryCache.models()) {
                if (model != null && !model.isEmpty()) {
                    modelsToEvict.add(model);
                }
            }

            // 从 SpEL 表达式获取
            String expression = evictQueryCache.modelsExpression();
            if (expression != null && !expression.isEmpty()) {
                Set<String> expressionModels = evaluateExpression(joinPoint, expression, result);
                modelsToEvict.addAll(expressionModels);
            }

            // 执行清除
            if (!modelsToEvict.isEmpty()) {
                log.info("Evicting query cache for models {} due to @EvictQueryCache on {}",
                        modelsToEvict, getMethodName(joinPoint));
                for (String model : modelsToEvict) {
                    queryCacheProvider.evict(model);
                }
            }
        } catch (Exception e) {
            // 缓存清除失败不应影响业务
            log.warn("Failed to evict query cache: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析 SpEL 表达式获取模型名称
     */
    @SuppressWarnings("unchecked")
    private Set<String> evaluateExpression(JoinPoint joinPoint, String expressionStr, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(),
                    method,
                    joinPoint.getArgs(),
                    parameterNameDiscoverer
            );

            // 添加 result 变量
            if (result != null) {
                context.setVariable("result", result);
            }

            Expression expression = parser.parseExpression(expressionStr);
            Object value = expression.getValue(context);

            if (value == null) {
                return Collections.emptySet();
            }

            Set<String> models = new HashSet<>();
            if (value instanceof String) {
                models.add((String) value);
            } else if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    if (item != null) {
                        models.add(item.toString());
                    }
                }
            } else {
                models.add(value.toString());
            }
            return models;
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression '{}': {}", expressionStr, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 获取方法全限定名
     */
    private String getMethodName(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
}
