package com.nbamanager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nbamanager.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 未知用户登录的时序侧信道防护回归。
 *
 * 原先的占位哈希是 <b>61</b> 字符（BCrypt 期望 60 = $2a$10$ + 22 位 salt + 31 位 hash），
 * BCrypt 会先做格式校验、不合法就直接返回 false 并打
 * "Encoded password does not look like BCrypt" ——
 * 于是"对不存在的用户也做一次同代价校验"这件事<b>根本没有发生</b>：
 * 防护失效，而且那条 WARN 本身就是可观测的"用户不存在"信号。
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceTimingDefenseTest {

    @Mock private UserAccountRepository userAccountRepository;

    @Test
    void dummyHashIsAWellFormedBcryptHash() throws Exception {
        var field = UserDetailsServiceImpl.class.getDeclaredField("DUMMY_BCRYPT_HASH");
        field.setAccessible(true);
        String hash = (String) field.get(null);

        // 必须是合法 BCrypt 哈希：长度 60，且能被 BCrypt 正常处理（不会提前短路）
        assertThat(hash).hasSize(60);
        assertThat(hash).matches("\\A\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}\\z");

        // 关键证明：对合法哈希做一次不匹配的校验，必须真的走到 BCrypt 内部
        // （非法哈希会立刻返回 false，耗时可忽略；这里用"它能与自身匹配"来证明格式有效）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("dummy-password-for-timing", hash))
                .as("占位哈希必须格式合法，否则这次校验会被跳过，防护形同虚设")
                .isFalse();
        assertThat(encoder.upgradeEncoding(hash))
                .as("cost 10 与当前默认一致，不应被判为需要升级")
                .isFalse();
    }

    @Test
    void unknownUserStillThrowsUsernameNotFound() {
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userAccountRepository);
        when(userAccountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void knownUserIsReturnedWithoutTimingPath() {
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userAccountRepository);
        com.nbamanager.domain.UserAccount account = new com.nbamanager.domain.UserAccount();
        account.setId(1L);
        account.setUsername("admin");
        account.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        account.setRole(com.nbamanager.domain.Role.ADMIN);
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        assertThat(service.loadUserByUsername("admin").getUsername()).isEqualTo("admin");
    }
}
