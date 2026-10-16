package com.healthsystem.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.healthsystem.mapper")
public class MybatisPlusConfiguration {
}
