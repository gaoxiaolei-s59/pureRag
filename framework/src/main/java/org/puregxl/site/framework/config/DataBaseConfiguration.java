package org.puregxl.site.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Date;

@AutoConfiguration
@ConditionalOnClass({DataSource.class, SqlSessionFactory.class, MybatisSqlSessionFactoryBean.class})
public class DataBaseConfiguration {
    /**
     * MyBatis-Plus PostgreSQL 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * MyBatis-Plus 源数据自动填充类
     */
    @Bean
    public MyMetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
    }

    /**
     * Spring Boot 4 下显式创建 MyBatis-Plus SqlSessionFactory，避免 Mapper 初始化时缺少
     * sqlSessionFactory/sqlSessionTemplate。
     */
    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               ObjectProvider<MybatisPlusInterceptor> interceptorProvider,
                                               ObjectProvider<MetaObjectHandler> metaObjectHandlerProvider) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        interceptorProvider.ifAvailable(factoryBean::setPlugins);
        metaObjectHandlerProvider.ifAvailable(metaObjectHandler -> {
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setMetaObjectHandler(metaObjectHandler);
            factoryBean.setGlobalConfig(globalConfig);
        });
        return factoryBean.getObject();
    }

    /**
     * MyBatis-Plus 源数据自动填充类。
     */
    static class MyMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            strictInsertFill(metaObject, "createTime", Date::new, Date.class);
            strictInsertFill(metaObject, "updateTime", Date::new, Date.class);
            strictInsertFill(metaObject, "delFlag", () -> 0, Integer.class);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            strictUpdateFill(metaObject, "updateTime", Date::new, Date.class);
        }
    }
}
