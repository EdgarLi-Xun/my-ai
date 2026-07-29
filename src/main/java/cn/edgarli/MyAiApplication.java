package cn.edgarli;

import cn.edgarli.infrastructure.config.AdminProperties;
import cn.edgarli.infrastructure.config.LogProperties;
import cn.edgarli.infrastructure.config.TrashProperties;
import cn.edgarli.mapper.UserApiKeyMapper;
import cn.edgarli.mapper.UserMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application entry point.
 * Spring Boot 应用启动入口。
 * <p>
 * 启用 {@code @MapperScan} 扫描 {@code cn.edgarli.mapper}（仅带 {@link Mapper}
 * 注解的接口），同时显式启用 {@code @ConfigurationProperties} 绑定
 * {@link TrashProperties} / {@link LogProperties} / {@link AdminProperties}。
 * {@code @EnableScheduling} 让 {@code LogCleanupTask} / {@code ConversationCleanupTask}
 * 等定时任务生效。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({TrashProperties.class, LogProperties.class, AdminProperties.class})
@MapperScan(basePackages = "cn.edgarli.mapper", annotationClass = Mapper.class)
public class MyAiApplication {

    /**
     * Application main; delegated to {@link SpringApplication#run}.
     * 启动入口，委托给 {@link SpringApplication#run}。
     *
     * @param args 命令行参数 / command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }

    /**
     * Constructor injection of mappers to satisfy MyBatis-Flex default expectations.
     * 通过构造器注入 mapper 以满足 MyBatis-Flex 默认期望。
     * <p>
     * 实际逻辑为空——仅用于让 Spring 提前初始化 mapper bean，避免某些场景下
     * 启动后第一次访问 mapper 时出现"找不到 bean"延迟。
     *
     * @param userMapper       用户 mapper / user mapper
     * @param userApiKeyMapper 用户 Key mapper / user API key mapper
     */
    public MyAiApplication(UserMapper userMapper, UserApiKeyMapper userApiKeyMapper) {
        // 通过构造器注入以满足 Flex 默认期望 / constructor-injected to satisfy Flex default expectations
    }

    /**
     * Print startup banner once the context is ready.
     * 上下文就绪后打印启动横幅。
     * <p>
     * 端口以 {@code application.yml} 中 {@code server.port} 为准（默认 8031）。
     * Port follows {@code server.port} in {@code application.yml} (default 8031).
     *
     * @return CommandLineRunner printing startup info / CommandLineRunner that prints startup info
     */
    @Bean
    public CommandLineRunner startupInfo() {
        return args -> {
            System.out.println("MyAi application started.");
            System.out.println("Open http://localhost:8031/ to manage users, keys and chats.");
            System.out.println("Chat API: POST http://localhost:8031/api/conversations/{id}/messages "
                    + "(POST /api/chat is a deprecated alias).");
        };
    }
}