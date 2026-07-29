package cn.edgarli.infrastructure.config;

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
 * MyBatis-Flex + Spring Boot 4 compatibility layer: explicitly uses HikariDataSource.
 * - 不再让 Spring Boot 创建数据源，避免其 DataSourceBuilder 在缺少 Hikari 驱动元信息时失活。
 * - No longer let Spring Boot create the DataSource, to avoid its DataSourceBuilder deactivating when Hikari driver metadata is missing.
 * - MapUnderscoreToCamelCase=True 交给 FlexConfiguration，符合 Flex 1.x 强制要求。
 * - MapUnderscoreToCamelCase=True is delegated to FlexConfiguration, which is required by Flex 1.x.
 */
@Configuration
public class FlexConfig {

    /**
     * 构造 HikariDataSource，从 {@code spring.datasource.*} 绑定属性。
     * Build a HikariDataSource bound to {@code spring.datasource.*}.
     *
     * @return HikariDataSource 实例 / HikariDataSource instance
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public HikariDataSource dataSource() {
        return new HikariDataSource();
    }

    /**
     * 构造 FlexSqlSessionFactoryBean：注册 mapper XML + 开启下划线转驼峰。
     * Build FlexSqlSessionFactoryBean: register mapper XML and enable underscore-to-camelCase mapping.
     *
     * @param dataSource 数据源 / data source
     * @return SqlSessionFactoryBean / SqlSessionFactoryBean
     * @throws Exception 资源解析失败 / resource resolution failure
     */
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

    /**
     * 提供主事务管理器（与 Flex 共享同一 DataSource）。
     * Provide the primary PlatformTransactionManager (sharing the same DataSource with Flex).
     *
     * @param dataSource 数据源 / data source
     * @return 事务管理器 / transaction manager
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(javax.sql.DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 暴露 FlexGlobalConfig，便于审计/分页等全局配置。
     * Expose FlexGlobalConfig for auditing/paging and other global settings.
     *
     * @return FlexGlobalConfig 实例 / FlexGlobalConfig instance
     */
    @Bean
    public FlexGlobalConfig flexGlobalConfig() {
        return new FlexGlobalConfig();
    }
}
