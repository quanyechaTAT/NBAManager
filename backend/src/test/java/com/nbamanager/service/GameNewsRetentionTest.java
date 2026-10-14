package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.UserFavorite;
import com.nbamanager.domain.UserBrowseHistory;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real selection/deletion queries and protects favorites and game records. */
class GameNewsRetentionTest {
    static SessionFactory factory;
    EntityManager em;
    GameNewsRepository news;
    UserBrowseHistoryRepository history;
    final LocalDateTime now = LocalDateTime.of(2026, 10, 14, 12, 0);

    @BeforeAll static void database() {
        boolean mysql = "true".equals(System.getenv("DISCOVERY_RETENTION_MYSQL"));
        factory = new Configuration().addAnnotatedClass(GameNews.class).addAnnotatedClass(UserFavorite.class)
                .addAnnotatedClass(UserBrowseHistory.class)
                .setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", mysql?"com.mysql.cj.jdbc.Driver":"org.h2.Driver")
                .setProperty("hibernate.connection.url", mysql?
                        "jdbc:mysql://127.0.0.1:13306/nba_retention_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai":
                        "jdbc:h2:mem:news_retention;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", mysql?"root":"sa")
                .setProperty("hibernate.connection.password", mysql?System.getenv("DISCOVERY_RETENTION_PASSWORD"):"")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
    }
    @AfterAll static void closeDatabase() { factory.close(); }
    @BeforeEach void begin() {
        em = factory.createEntityManager(); em.getTransaction().begin();
        var repositories = new JpaRepositoryFactory(em);
        news = repositories.getRepository(GameNewsRepository.class);
        history = repositories.getRepository(UserBrowseHistoryRepository.class);
    }
    @AfterEach void rollback() { em.getTransaction().rollback(); em.close(); }

    @Test void ageLimitUsesPublicationTimeAndProtectsFavoritesAndGames() {
        var old = article("old", 91, "general");
        var favorite = article("favorite", 200, "trade");
        var game = article("game", 200, "game");
        var live = article("live", 200, "general"); live.setStatus("LIVE");
        var fresh = article("fresh", 2, "general");
        var f = new UserFavorite(); f.setUserId(1L); f.setTargetType("NEWS"); f.setTargetId(favorite.getId()); em.persist(f);
        var h = new UserBrowseHistory(); h.setUserId(1L); h.setTargetType("NEWS"); h.setTargetId(old.getId()); em.persist(h);
        em.flush();
        assertThat(new NewsRetentionService(news, history, 90, 10_000).cleanupAt(now)).isEqualTo(1);
        em.clear();
        assertThat(news.findAll()).extracting(GameNews::getId)
                .containsExactlyInAnyOrder(favorite.getId(), game.getId(), live.getId(), fresh.getId());
        assertThat(history.count()).isZero();
        assertThat(em.createQuery("select count(f) from UserFavorite f", Long.class).getSingleResult()).isEqualTo(1);
    }

    @Test void countLimitAppliesOnlyToUnprotectedOrdinaryNews() {
        var oldest = article("a", 3, "injury");
        var middle = article("b", 2, "trade");
        var newest = article("c", 1, "general");
        var game = article("game", 4, "summer_game");
        em.flush();
        assertThat(new NewsRetentionService(news, history, 90, 2).cleanupAt(now)).isEqualTo(1);
        em.clear();
        assertThat(news.findAll()).extracting(GameNews::getId)
                .containsExactlyInAnyOrder(middle.getId(), newest.getId(), game.getId());
        assertThat(news.existsById(oldest.getId())).isFalse();
    }

    @Test void exactCutoffAndLegacyMissingPublicationRemainStable() {
        article("boundary", 90, "general");
        var legacy = article("legacy", 2, "general"); legacy.setPublishedAt(null); legacy.setCreateTime(now.minusDays(2));
        em.flush();
        assertThat(new NewsRetentionService(news, history, 90, 10_000).cleanupAt(now)).isZero();
        assertThat(news.count()).isEqualTo(2);
    }

    private GameNews article(String title, int daysAgo, String category) {
        var row = new GameNews(); row.setTitle(title); row.setSummary(title); row.setContent(title);
        row.setHomeTeam(""); row.setAwayTeam(""); row.setGameStartTime(now); row.setGameEndTime(now.plusHours(2));
        row.setCreateTime(now); row.setPublishedAt(now.minusDays(daysAgo)); row.setCategory(category); row.setStatus("FINISHED");
        em.persist(row); return row;
    }
}
