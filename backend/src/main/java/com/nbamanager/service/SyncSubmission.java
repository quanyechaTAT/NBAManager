package com.nbamanager.service;

import com.nbamanager.domain.SyncJob;

public record SyncSubmission(SyncJob job, boolean reused) {}
