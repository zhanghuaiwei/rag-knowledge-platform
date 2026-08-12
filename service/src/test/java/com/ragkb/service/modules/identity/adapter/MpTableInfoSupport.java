package com.ragkb.service.modules.identity.adapter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单测辅助：初始化 MyBatis-Plus 实体元数据，使 {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}
 * 能解析列名（正常由 MP 运行时在 Mapper 注册时完成，单测无 Spring 容器故手动补齐）。
 */
public final class MpTableInfoSupport {

    private MpTableInfoSupport() {
    }

    public static void init(Class<?>... entityClasses) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entityClass : entityClasses) {
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
