package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsPersistenceService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final GameNewsRepository repository;
    private final NewsRetentionService retention;

    public NewsPersistenceService(
            GameNewsRepository repository,
            NewsRetentionService retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public synchronized PersistResult persist(JSONArray candidates) {
        List<JSONObject> items = new ArrayList<>(candidates.length());
        Set<String> sourceUrls = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject item = candidates.getJSONObject(i);
            items.add(item);
            addIfPresent(sourceUrls, item.optString("sourceUrl", ""));
            addIfPresent(sourceUrls, normalizeUrl(item.optString("sourceUrl", "")));
            addIfPresent(titles, item.optString("headline", ""));
            addIfPresent(titles, item.optString("headlineEn", ""));
        }

        Set<String> existingUrls = sourceUrls.isEmpty()
                ? new HashSet<>() : normalizedUrls(repository.findBySourceUrlIn(sourceUrls));
        Set<String> existingTitles = titles.isEmpty()
                ? new HashSet<>() : normalizedTitles(repository.findByTitleIn(titles));
        if (!titles.isEmpty()) {
            existingTitles.addAll(normalizedEnglishTitles(repository.findByTitleEnIn(titles)));
        }

        Set<String> batchKeys = new HashSet<>();
        List<GameNews> toInsert = new ArrayList<>();
        int skipped = 0;

        for (JSONObject item : items) {
            String sourceUrl = normalizeUrl(item.optString("sourceUrl", ""));
            String englishTitle = normalizeTitle(item.optString(
                    "headlineEn", item.optString("headline", "")));
            String translatedTitle = normalizeTitle(item.optString("headline", ""));
            List<String> itemKeys = articleKeys(sourceUrl, englishTitle, translatedTitle);

            boolean duplicate = itemKeys.isEmpty()
                    || (!sourceUrl.isBlank() && existingUrls.contains(sourceUrl))
                    || (!englishTitle.isBlank() && existingTitles.contains(englishTitle))
                    || (!translatedTitle.isBlank() && existingTitles.contains(translatedTitle))
                    || itemKeys.stream().anyMatch(batchKeys::contains);
            if (duplicate) {
                skipped++;
                continue;
            }
            batchKeys.addAll(itemKeys);
            toInsert.add(toEntity(item, sourceUrl));
        }

        if (!toInsert.isEmpty()) {
            repository.saveAll(toInsert);
        }
        retention.cleanup();
        return new PersistResult(items.size(), toInsert.size(), skipped);
    }

    private List<String> articleKeys(String sourceUrl, String englishTitle, String translatedTitle) {
        List<String> keys = new ArrayList<>();
        if (!sourceUrl.isBlank()) {
            keys.add("url:" + sourceUrl);
        }
        if (!englishTitle.isBlank()) {
            keys.add("title:" + englishTitle);
        }
        if (!translatedTitle.isBlank() && !translatedTitle.equals(englishTitle)) {
            keys.add("title:" + translatedTitle);
        }
        return keys;
    }

    private GameNews toEntity(JSONObject item, String normalizedUrl) {
        String headline = nonBlank(item.optString("headline", ""),
                item.optString("headlineEn", "资讯"));
        String headlineEn = nonBlank(item.optString("headlineEn", ""), headline);
        String description = nonBlank(item.optString("description", ""), headline);
        String content = nonBlank(item.optString("content", ""), description);
        String contentEn = nonBlank(item.optString("contentEn", ""), content);
        LocalDateTime sourceTime = parsePublishedAt(item.optString("published", ""));
        LocalDateTime publishedAt = sourceTime == null ? LocalDateTime.now(SHANGHAI) : sourceTime;

        GameNews news = new GameNews();
        news.setTitle(truncate(headline, 120));
        news.setTitleEn(truncate(headlineEn, 200));
        news.setSummary(truncate(description, 300));
        news.setContent(content);
        news.setContentEn(contentEn);
        news.setHomeTeam(truncate(item.optString("homeTeam", ""), 80));
        news.setAwayTeam(truncate(item.optString("awayTeam", ""), 80));
        news.setHomeScore(null);
        news.setAwayScore(null);
        news.setGameStartTime(publishedAt);
        news.setGameEndTime(publishedAt.plusHours(2));
        news.setCreateTime(LocalDateTime.now());
        news.setPublishedAt(publishedAt);
        news.setPublishedAtSource(sourceTime == null ? "INGESTED" : "SOURCE");
        news.setStatus("FINISHED");
        news.setNbaGameId(nullableTruncated(item.optString("nbaGameId", ""), 16));
        news.setCategory(truncate(nonBlank(item.optString("category", ""), "general"), 20));
        news.setSourceUrl(nullableTruncated(normalizedUrl, 500));
        news.setImageUrl(nullableTruncated(item.optString("imageUrl", ""), 500));
        news.setViewCount(0);
        news.setFavoriteCount(0);
        return news;
    }

    static String normalizeUrl(String value) {
        String raw = normalizeValue(value);
        if (raw.isBlank()) return "";
        try {
            URI uri = URI.create(raw);
            String query = uri.getRawQuery();
            if (query != null) {
                query = java.util.Arrays.stream(query.split("&"))
                        .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("utm_"))
                        .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("cid="))
                        .collect(java.util.stream.Collectors.joining("&"));
                if (query.isBlank()) query = null;
            }
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(
                    uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(), path, query, null).toString();
        } catch (Exception ignored) {
            return raw.replaceAll("/+$", "");
        }
    }

    private static LocalDateTime parsePublishedAt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.replace("Z", "+00:00"))
                    .atZoneSameInstant(SHANGHAI).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignoredAgain) {
                try {
                    return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .withZoneSameInstant(SHANGHAI).toLocalDateTime();
                } catch (Exception ignoredFinally) {
                    return null;
                }
            }
        }
    }

    private static Set<String> normalizedUrls(Collection<GameNews> news) {
        Set<String> values = new HashSet<>();
        for (GameNews item : news) addIfPresent(values, normalizeUrl(item.getSourceUrl()));
        return values;
    }

    private static Set<String> normalizedTitles(Collection<GameNews> news) {
        Set<String> values = new HashSet<>();
        for (GameNews item : news) addIfPresent(values, normalizeTitle(item.getTitle()));
        return values;
    }

    private static Set<String> normalizedEnglishTitles(Collection<GameNews> news) {
        Set<String> values = new HashSet<>();
        for (GameNews item : news) addIfPresent(values, normalizeTitle(item.getTitleEn()));
        return values;
    }

    private static String normalizeTitle(String value) {
        return normalizeValue(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) values.add(value);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String nullableTruncated(String value, int maxLength) {
        String normalized = normalizeValue(value);
        return normalized.isBlank() ? null : truncate(normalized, maxLength);
    }

    public record PersistResult(int fetched, int inserted, int skipped) {}
}
