package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.web.support.BulkheadFilter;
import com.nbamanager.web.support.PagingRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F5 回归：关闭 bulkhead 时应用必须能正常启动。
 *
 * 缺陷史：把"可关闭"实现成
 * <ul>
 *   <li>{@code setEnabled(false)} 且不 setFilter → 启动抛
 *       {@code IllegalArgumentException: Filter must not be null}；</li>
 *   <li>返回 null → Spring 包成 NullBean，收集 ServletContextInitializer 时抛
 *       {@code BeanNotOfRequiredTypeException}。</li>
 * </ul>
 * 两种写法都会让"关闭该保护"直接导致应用起不来（实测均复现）。
 * 现改为按条件不注册该 bean：关闭时这个 bean 根本不存在，自然无副作用。
 */
class BulkheadDisabledStartupTest {

    private static WebMvcConfig newConfig() {
        WebMvcConfig config = new WebMvcConfig(new PagingRequestInterceptor());
        ReflectionTestUtils.setField(config, "uploadDir", "uploads");
        return config;
    }

    @Test
    void disabledBulkheadIsExpressedAsConditionalBeanNotAsEmptyRegistration() throws Exception {
        // 关闭状态必须是"不定义该 bean"，而不是"定义了一个空壳"，否则启动期就会失败
        ConditionalOnProperty conditional = WebMvcConfig.class
                .getMethod("bulkheadFilterRegistration", int.class, int.class)
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional)
                .as("bulkheadFilterRegistration 必须有条件注册注解，否则无法安全关闭")
                .isNotNull();
        assertThat(conditional.name()).containsExactly("app.bulkhead.enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
        assertThat(conditional.matchIfMissing())
                .as("默认（未配置该属性时）应启用保护")
                .isTrue();
    }

    @Test
    void enabledBulkheadRegistersFilterOnApiPaths() {
        FilterRegistrationBean<BulkheadFilter> registration =
                newConfig().bulkheadFilterRegistration(100, 400);

        assertThat(registration.getFilter()).isInstanceOf(BulkheadFilter.class);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
    }

    /**
     * 全局并发阈值必须低于 Tomcat 工作线程数（threads.max 默认 200）。
     *
     * 请求"在途数"受工作线程数约束：若全局阈值 ≥ 线程数，全局档永远不会成为
     * 第一个约束，等于虚设（第 5 轮实测：线程池被慢客户端占满后，本应 1ms 返回的
     * 429 也要排队 29.7s）。这里钉住默认值 180，防止日后被改回 400 之类的无效值。
     */
    @Test
    void globalMaxDefaultStaysBelowTomcatThreadMax() throws Exception {
        org.springframework.beans.factory.annotation.Value annotation = WebMvcConfig.class
                .getMethod("bulkheadFilterRegistration", int.class, int.class)
                .getParameters()[1]
                .getAnnotation(org.springframework.beans.factory.annotation.Value.class);

        String defaultValue = annotation.value()
                .substring(annotation.value().lastIndexOf(':') + 1,
                        annotation.value().length() - 1);
        int globalMaxDefault = Integer.parseInt(defaultValue);

        assertThat(globalMaxDefault)
                .as("全局并发阈值默认值必须 < threads.max(200)，否则全局档形同虚设")
                .isLessThan(200);
    }
}
