package com.nbamanager.repository;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.domain.SyncJobStep;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import static org.assertj.core.api.Assertions.assertThat;

class SyncSnapshotRepositoryTest {
    private static SessionFactory factory;
    private EntityManager entityManager;
    private SyncJobRepository jobs;
    private SyncJobStepRepository steps;
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 5, 12, 0);

    @BeforeAll static void createTestDatabase() {
        factory = new Configuration().addAnnotatedClass(SyncJob.class).addAnnotatedClass(SyncJobStep.class)
                .setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:sync_snapshot;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory();
    }
    @AfterAll static void closeTestDatabase() { factory.close(); }
    @BeforeEach void openTransaction() {
        entityManager = factory.createEntityManager();
        entityManager.getTransaction().begin();
        var repositories = new JpaRepositoryFactory(entityManager);
        jobs = repositories.getRepository(SyncJobRepository.class);
        steps = repositories.getRepository(SyncJobStepRepository.class);
    }
    @AfterEach void rollBackTestData() {
        entityManager.getTransaction().rollback();
        entityManager.close();
    }

    private SyncJob job(String module, String status, int minute) {
        SyncJob row = new SyncJob();
        row.setModule(module); row.setStatus(status); row.setTriggerType("MANUAL");
        jobs.saveAndFlush(row);
        row.setCreatedAt(time.plusMinutes(minute)); row.setFinishedAt(time.plusMinutes(minute + 1));
        return jobs.saveAndFlush(row);
    }
    private SyncJobStep step(String module, String status, int minute) {
        SyncJobStep row = new SyncJobStep();
        row.setJobId(1L); row.setModule(module); row.setStatus(status);
        row.setFinishedAt(time.plusMinutes(minute));
        return steps.saveAndFlush(row);
    }
    @Test void latestJobsAreSelectedPerModuleWithDeterministicTimestampTies() {
        job("TEAMS", "SUCCESS", 1);
        job("TEAMS", "FAILED", 2);
        SyncJob newest = job("TEAMS", "RUNNING", 2);
        SyncJob players = job("PLAYERS", "QUEUED", 0);
        assertThat(jobs.findLatestPerModule()).extracting(SyncJob::getId)
                .containsExactlyInAnyOrder(newest.getId(), players.getId());
        assertThat(jobs.findLatestSuccessfulPerModule()).extracting(SyncJob::getModule).containsExactly("TEAMS");
    }
    @Test void successfulTimeSurvivesLaterFailuresAndDuplicateFinishTimes() {
        job("TEAMS", "SUCCESS", 1);
        SyncJob success = job("TEAMS", "SUCCESS", 1);
        job("TEAMS", "FAILED", 2);
        assertThat(jobs.findLatestSuccessfulPerModule()).extracting(SyncJob::getId).containsExactly(success.getId());
        step("TEAMS", "SUCCESS", 1);
        SyncJobStep latest = step("TEAMS", "SUCCESS", 1);
        step("TEAMS", "FAILED", 2);
        SyncJobStep players = step("PLAYERS", "SUCCESS", 0);
        assertThat(steps.findLatestSuccessfulSteps()).extracting(SyncJobStep::getId)
                .containsExactlyInAnyOrder(latest.getId(), players.getId());
    }
}
