package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ReplayFactsHashService {

    public String hash(List<PlayByPlay> events) {
        List<String> canonicalEvents = events == null
                ? List.of()
                : events.stream()
                        .filter(Objects::nonNull)
                        .map(this::canonicalEvent)
                        .sorted()
                        .toList();

        MessageDigest digest = sha256();
        for (String canonicalEvent : canonicalEvents) {
            update(digest, canonicalEvent);
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String canonicalEvent(PlayByPlay event) {
        StringBuilder canonical = new StringBuilder(256);
        append(canonical, normalize(event.getGameId()));
        append(canonical, event.getPeriod());
        append(canonical, normalize(event.getGameClock()));
        append(canonical, normalize(event.getDescription()));
        append(canonical, event.getHomeScore());
        append(canonical, event.getAwayScore());
        append(canonical, normalize(event.getEventType()));
        append(canonical, event.getPlayerId());
        append(canonical, normalize(event.getPlayerName()));
        append(canonical, event.getTeamId());
        append(canonical, normalize(event.getSource()));
        append(canonical, normalize(event.getSourceEventId()));
        append(canonical, event.getEventOrder());
        append(canonical, normalize(event.getActionType()));
        append(canonical, normalize(event.getTeamName()));
        append(canonical, event.getScoreMargin());
        append(canonical, event.getIsScoringPlay());
        append(canonical, event.getIsHighlightCandidate());
        append(canonical, normalize(event.getRawPayloadJson()));
        append(canonical, normalize(event.getRawPayloadHash()));
        return canonical.toString();
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void update(MessageDigest digest, Object value) {
        String text = value == null ? "<null>" : value.toString();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }

    private void append(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
