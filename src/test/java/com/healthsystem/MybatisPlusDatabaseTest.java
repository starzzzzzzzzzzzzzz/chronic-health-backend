package com.healthsystem;

import com.healthsystem.entity.AppMetadata;
import com.healthsystem.mapper.AppMetadataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** 显式启用的真实数据库测试；事务回滚，保留原有数据。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@Transactional
class MybatisPlusDatabaseTest {
    @Autowired AppMetadataMapper mapper;

    @Test
    void mapsExistingSchemaAndLoadsXml() {
        AppMetadata metadata = mapper.selectById("application");
        assertThat(metadata).isNotNull();
        assertThat(metadata.getMetadataValue()).isEqualTo("chronic-health");
        assertThat(metadata.getCreatedAt()).isNotNull();
        assertThat(mapper.findValueByKey("application")).isEqualTo("chronic-health");
    }

    @Test
    void baseMapperSupportsCrud() {
        String key = "test-" + UUID.randomUUID();
        AppMetadata metadata = new AppMetadata();
        metadata.setMetadataKey(key);
        metadata.setMetadataValue("before");
        assertThat(mapper.insert(metadata)).isEqualTo(1);
        assertThat(mapper.selectById(key).getMetadataValue()).isEqualTo("before");
        metadata.setMetadataValue("after");
        assertThat(mapper.updateById(metadata)).isEqualTo(1);
        assertThat(mapper.findValueByKey(key)).isEqualTo("after");
        assertThat(mapper.deleteById(key)).isEqualTo(1);
        assertThat(mapper.selectById(key)).isNull();
    }
}
