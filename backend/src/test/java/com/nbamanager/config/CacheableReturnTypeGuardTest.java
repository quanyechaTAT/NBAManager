package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * 缓存值"必须可被 Redis 序列化器还原"的架构护栏。
 *
 * <h2>为什么需要这个测试</h2>
 * 本项目已经真实踩过一次：{@code SmartDataService.getNewsWithCache} 上标了
 * {@code @Cacheable} 却返回 {@code Page<GameNewsDto>}。写入 Redis 成功，读回来时
 * Jackson 造不出 {@code PageImpl}（没有默认构造器，也没有可推断的 creator），
 * 报 "Cannot construct instance of PageImpl (no Creators...)"。
 * 症状是"第一次请求 200、之后所有请求 500"——比不加缓存更糟，而且只在缓存命中时才出现，
 * 单元测试与首次手测都发现不了。
 *
 * 该缺陷此前只是"潜伏"（没有任何调用方，Redis 里也就没有 news 键），
 * 但隐患本身留在代码里。现在：dead code 已删除，并由本测试阻止同类写法再次出现。
 *
 * <h2>护栏内容</h2>
 * 1. 扫描 {@code com.nbamanager} 下所有 Spring bean，断言没有任何 {@code @Cacheable}
 *    方法返回 {@code Page} / {@code Slice}（这类值无法往返）；
 * 2. 用真实序列化器演示 {@code Page} 确实无法往返——把"为什么禁止"钉成可执行证据，
 *    而不是注释里的一句提醒。
 */
class CacheableReturnTypeGuardTest {

    @Test
    void noCacheableMethodReturnsPageOrSlice() throws Exception {
        List<String> offenders = new ArrayList<>();

        for (Class<?> beanType : scanSpringBeans("com.nbamanager")) {
            for (Method method : beanType.getDeclaredMethods()) {
                Cacheable cacheable = AnnotationUtils.findAnnotation(method, Cacheable.class);
                if (cacheable == null) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                if (Page.class.isAssignableFrom(returnType) || Slice.class.isAssignableFrom(returnType)) {
                    offenders.add(beanType.getName() + "#" + method.getName()
                            + " -> " + returnType.getName()
                            + " (cache=" + String.join(",", cacheable.value()) + ")");
                }
            }
        }

        assertThat(offenders)
                .as("Page/Slice 无法被 Redis 序列化器还原（PageImpl 没有可用构造器），"
                        + "缓存它们会造成\"命中即 500\"；请缓存纯数据容器，由调用方重建 PageImpl。"
                        + "参见 PlayerListCache / PostListCache 的做法")
                .isEmpty();
    }

    /**
     * 把"Page 不能进 Redis"钉成可执行证据。
     *
     * 写入是成功的（类型信息里有 org.springframework.data.domain.PageImpl），
     * 失败发生在读取侧——这正是它难以被发现的原因。
     */
    @Test
    void pageCannotBeDeserializedByTheRealRedisSerializer() {
        RedisConfig config = new RedisConfig(1800, 60, 60);
        var serializer = config.jacksonRedisSerializer();

        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 2);

        // 写入侧不报错，所以"缓存里已经有脏数据了"这件事不会立刻暴露
        byte[] bytes = serializer.serialize(page);
        assertThat(bytes).isNotEmpty();

        assertThatThrownBy(() -> serializer.deserialize(bytes))
                .as("PageImpl 读回来必须失败——这就是\"缓存命中即 500\"的根因")
                .isInstanceOf(Exception.class);
    }

    /** 扫描 @Component 元注解（含 @Service/@RestController/@Configuration）标记的 bean 类。 */
    private static List<Class<?>> scanSpringBeans(String basePackage) throws ClassNotFoundException {
        var provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // 默认实现只接受"独立且具体"的类；接口/抽象类不会被代理成 bean，直接跳过即可
                return beanDefinition.getMetadata().isIndependent() && !beanDefinition.getMetadata().isInterface();
            }
        };
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : provider.findCandidateComponents(basePackage)) {
            String className = definition.getBeanClassName();
            if (className != null) {
                classes.add(Class.forName(className));
            }
        }
        assertThat(classes).as("扫描不到任何 bean，说明护栏本身失效了").isNotEmpty();
        return classes;
    }
}
