package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PythonScriptRunnerCancellationTest {

    @Test
    void cancelledJobDoesNotStartNextPythonStage() {
        PythonScriptRunner runner = new PythonScriptRunner();
        runner.setCancellationChecker(jobId -> true);

        PythonScriptRunner.PythonResult result = runner.runForJob(
                "script-that-must-not-start.py", new String[0], 30, 7L);

        assertThat(result.exitCode()).isEqualTo(-2);
        assertThat(result.stderr()).contains("cancelled");
    }
}
