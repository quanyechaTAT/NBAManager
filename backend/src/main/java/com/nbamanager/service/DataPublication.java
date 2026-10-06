package com.nbamanager.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Shared display predicate for verified fields and untouched migration snapshots. */
public final class DataPublication {
    public static final String LEGACY_SQL = "(p.data_source='UNKNOWN' AND p.data_quality='UNVERIFIED' AND p.data_field_states IS NULL AND p.data_synced_at IS NULL)";
    private DataPublication() {}
    public static Predicate available(Root<?> row, CriteriaBuilder builder, String field) {
        var validJson = builder.isTrue(builder.function("JSON_VALID", Boolean.class, row.get("dataFieldStates")));
        var safeJson = builder.<String>selectCase().when(validJson, row.<String>get("dataFieldStates")).otherwise("{}");
        var state = builder.function("JSON_UNQUOTE", String.class, builder.function("JSON_EXTRACT", String.class, safeJson, builder.literal("$." + field)));
        var verified = builder.and(builder.coalesce(row.<String>get("dataQuality"), "UNVERIFIED").in("COMPLETE", "PARTIAL"), builder.coalesce(state, "UNVERIFIED").in("OBSERVED", "DERIVED"));
        return builder.and(builder.or(retainedLegacy(row, builder), verified), builder.isNotNull(row.get(field)));
    }

    public static Predicate retainedLegacy(Root<?> row, CriteriaBuilder builder) {
        return builder.and(builder.equal(row.get("dataSource"), "UNKNOWN"),
                builder.equal(row.get("dataQuality"), "UNVERIFIED"),
                builder.isNull(row.get("dataFieldStates")), builder.isNull(row.get("dataSyncedAt")));
    }
}
