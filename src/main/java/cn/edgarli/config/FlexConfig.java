package cn.edgarli.config;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * MyBatis-Flex + Spring Boot 4 兼容层：显式使用 HikariDataSource。
 * - 不再让 Spring Boot 创建数据源，避免其 DataSourceBuilder 在缺少 Hikari 驱动元信息时失活。
 * - MapUnderscoreToCamelCase=True 交给 FlexConfiguration，符合 Flex 1.x 强制要求。
 */
@Configuration
public class FlexConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public HikariDataSource dataSource() {
        return new HikariDataSource();
    }

    @Bean
    public FlexSqlSessionFactoryBean sqlSessionFactory(javax.sql.DataSource dataSource) throws Exception {
        FlexSqlSessionFactoryBean factoryBean = new FlexSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:cn/edgarli/mapper/**/*.xml"));
        FlexConfiguration configuration = new FlexConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(javax.sql.DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public FlexGlobalConfig flexGlobalConfig() {
        return new FlexGlobalConfig();
    }
}
