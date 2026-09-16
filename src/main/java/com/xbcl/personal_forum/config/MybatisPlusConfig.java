package com.xbcl.personal_forum.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 的插件配置。
 *
 * <p>「插件」（拦截器）是 MyBatis-Plus 在 SQL 真正执行前后插手的地方。
 * 不配这些，功能就少一块 —— 比如不配分页插件，
 * selectPage 会把整张表查回来再在内存里切，数据一多就慢。
 *
 * <p>注意：这个 Bean 返回的是一个「容器」，里面可以塞多个插件，
 * 顺序有讲究 —— 分页要放在防全表更新前面。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页。构造参数传 DbType.MYSQL，它才知道按哪种方言拼 LIMIT
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 2. 防全表更新删除：没有 WHERE 的 UPDATE / DELETE 直接抛异常
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
