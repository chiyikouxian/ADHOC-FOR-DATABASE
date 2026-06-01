package com.fanet.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.fanet.mapper.pg", sqlSessionFactoryRef = "pgSqlSessionFactory")
public class PgDataSourceConfig {

    @Primary
    @Bean("pgDataSource")
    @ConfigurationProperties(prefix = "datasource.pg")
    public DataSource pgDataSource() {
        return new HikariDataSource();
    }

    @Primary
    @Bean("pgSqlSessionFactory")
    public SqlSessionFactory pgSqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(pgDataSource());
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:mapper/pg/*.xml"));
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    @Primary
    @Bean("pgTransactionManager")
    public PlatformTransactionManager pgTransactionManager() {
        return new DataSourceTransactionManager(pgDataSource());
    }
}
