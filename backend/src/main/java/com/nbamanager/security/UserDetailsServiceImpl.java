package com.nbamanager.security;

import com.nbamanager.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * 时序侧信道防护用的固定 BCrypt 哈希（cost 10）。
     *
     * 必须是一个<b>格式合法</b>的哈希：BCrypt 会先做正则/长度校验，不合法的输入会立刻
     * 返回 false（并打 WARN "Encoded password does not look like BCrypt"），
     * 那样这次"同代价校验"就完全没发生，防护形同虚设。
     * 此前这里写的是 61 字符的字符串（salt+hash 段多了 1 位），实测每次未命中用户登录
     * 都会刷出该 WARN —— 这既是防护失效的证据，也是可以据此判断"用户不存在"的信号。
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final BCryptPasswordEncoder TIMING_ENCODER = new BCryptPasswordEncoder();

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAccountRepository
                .findByUsername(username)
                .map(UserPrincipal::new)
                .orElseGet(() -> {
                    // 时序侧信道防护：对不存在的用户也执行一次同代价的 BCrypt 校验，
                    // 使登录响应时间不随用户是否存在而变化
                    TIMING_ENCODER.matches("dummy-password-for-timing", DUMMY_BCRYPT_HASH);
                    throw new UsernameNotFoundException("用户不存在: " + username);
                });
    }
}
