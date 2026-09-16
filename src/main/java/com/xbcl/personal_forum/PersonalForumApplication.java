package com.xbcl.personal_forum;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工程启动入口。main 一跑，Spring Boot 依次做这些事：
 *
 * <ol>
 *   <li>扫 com.xbcl.personal_forum 及其子包下所有 @Component / @Service / @RestController / @Configuration，
 *       建成 Bean 放进容器 —— 所以你的类必须在这个包底下，挪出去就扫不到了</li>
 *   <li>读 application.yaml，看到 profiles.active: local，再叠一层 application-local.yaml
 *       （数据库密码、端口这些只在本机文件里）</li>
 *   <li>启动内嵌的 Tomcat，端口 9090，路由前缀 /dev-api</li>
 * </ol>
 *
 * <p>@MapperScan("com.xbcl.personal_forum.mapper")：
 * 告诉 MyBatis 去这个包下找所有 Mapper 接口，运行时给它们生成实现类。
 * 有了它，各个 Mapper 上的 @Mapper 写不写都行。
 *
 * <p>@SpringBootApplication 本身是三个注解叠起来的：
 * @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan。
 * 「自动配置」的意思是：你 pom 里引了 mybatis-plus 和 mysql 驱动，
 * 它看到就自动把 DataSource、SqlSessionFactory 这些给你配好，不用写 XML。
 */
@MapperScan("com.xbcl.personal_forum.mapper")
@SpringBootApplication
public class PersonalForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonalForumApplication.class, args);
    }

}
