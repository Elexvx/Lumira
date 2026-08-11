package com.lumira.api.file;

import java.util.Locale;
import java.util.regex.Pattern;

/** Stable naming contract for a competition-owned file storage space. */
public final class CompetitionStorageSpace {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );
    private static final String STORAGE_KEY_PREFIX = "competition_";
    private static final String ROOT_PATH_PREFIX = "storage/uploads/competitions/";
    private static final String TITLE_PREFIX = "比赛专属存储 · ";
    private static final int MAX_TITLE_LENGTH = 128;

    private CompetitionStorageSpace() {
    }

    public static String storageKey(String competitionUuid) {
        return STORAGE_KEY_PREFIX + compactUuid(competitionUuid);
    }

    public static String rootPath(String competitionUuid) {
        return ROOT_PATH_PREFIX + compactUuid(competitionUuid) + "/";
    }

    public static boolean isCompetitionStorageKey(String storageKey) {
        return storageKey != null && storageKey.startsWith(STORAGE_KEY_PREFIX);
    }

    public static String title(Long competitionId, String competitionTitle) {
        String normalizedTitle = competitionTitle == null ? "" : competitionTitle.trim();
        if (normalizedTitle.isEmpty()) {
            normalizedTitle = competitionId == null ? "未命名比赛" : "比赛 " + competitionId;
        }
        int availableLength = MAX_TITLE_LENGTH - TITLE_PREFIX.length();
        if (normalizedTitle.length() > availableLength) {
            int endIndex = availableLength;
            if (Character.isHighSurrogate(normalizedTitle.charAt(endIndex - 1))
                    && Character.isLowSurrogate(normalizedTitle.charAt(endIndex))) {
                endIndex -= 1;
            }
            normalizedTitle = normalizedTitle.substring(0, endIndex);
        }
        return TITLE_PREFIX + normalizedTitle;
    }

    private static String compactUuid(String competitionUuid) {
        String normalized = competitionUuid == null
                ? ""
                : competitionUuid.trim().toLowerCase(Locale.ROOT);
        if (!UUID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("competitionUuid must be a canonical UUID");
        }
        return normalized.replace("-", "");
    }
}
