package cn.edgarli;

import cn.edgarli.mapper.UserApiKeyMapper;
import cn.edgarli.mapper.UserMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 应用启动入口。
 */
@SpringBootApplication
@MapperScan(basePackages = "cn.edgarli.mapper", annotationClass = Mapper.class)
public class MyAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAiApplication.class, args);
    }

    public MyAiApplication(UserMapper userMapper, UserApiKeyMapper userApiKeyMapper) {
        // 通过构造器注入以满足 Flex 默认期望
    }

    @Bean
    public CommandLineRunner startupInfo() {
        return args -> {
            System.out.println("MyAi application started.");
            System.out.println("Open http://localhost:8080/ to manage users, keys and chats.");
            System.out.println("Chat API: POST http://localhost:8080/api/chat with userId and messages.");
        };
    }
}
