package cn.edgarli.config;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis-Flex + Spring Boot 4 兼容层：
 * - 显式使用 HikariDataSource 接收 spring.datasource 配置（Boot 4 DataSourceBuilder
 *   在缺少 Hikari driver metadata 时无法构造）；
 * - 显式装配 FlexSqlSessionFactoryBean 与 PlatformTransactionManager。
 */
@Configuration
@EnableConfigurationProperties
public class FlexConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public HikariDataSource dataSource() {
        return new HikariDataSource();
    }

    @Bean
    public FlexSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
        FlexSqlSessionFactoryBean factoryBean = new FlexSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:cn/edgarli/mapper/**/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        factoryBean.setConfiguration(configuration);
        return factoryBean;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public FlexGlobalConfig flexGlobalConfig() {
        return new FlexGlobalConfig();
    }
}
