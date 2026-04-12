package io.github.qihua233.ae2_ftbquest_detector.utility;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TeamDisplayNameResolver {
    private TeamDisplayNameResolver() {
    }

    public static String resolveExistingTeamName(UUID ownerTeamId) {
        return resolveExistingTeamName(ownerTeamId, null);
    }

    public static String resolveExistingTeamName(UUID ownerTeamId, String cachedTeamName) {
        if (ownerTeamId == null) {
            return null;
        }
        String teamName = resolveRawTeamName(ownerTeamId, cachedTeamName);
        String shortId = toShortTeamId(ownerTeamId);
        if (teamName == null) {
            return shortId;
        }
        if (shortId == null) {
            return teamName;
        }
        if (teamName.equals(shortId)) {
            return shortId;
        }
        return teamName + "#" + shortId;
    }

    public static String resolveRawTeamName(UUID ownerTeamId, String cachedTeamName) {
        if (ownerTeamId == null) {
            return null;
        }

        TeamManagerImpl manager = TeamManagerImpl.INSTANCE;
        if (manager == null) {
            return normalize(cachedTeamName);
        }

        Team team = manager.getTeamByID(ownerTeamId).orElse(null);
        if (team == null) {
            team = manager.getTeamMap().get(ownerTeamId);
        }

        String bestName = resolveBestName(manager, team, ownerTeamId);
        if (bestName != null) {
            return bestName;
        }

        return normalize(cachedTeamName);
    }

    public static boolean isTeamNameTooShort(UUID ownerTeamId, String cachedTeamName, int minLength) {
        String teamName = resolveRawTeamName(ownerTeamId, cachedTeamName);
        return teamName != null && teamName.length() < minLength;
    }

    private static String resolveBestName(TeamManagerImpl manager, Team team, UUID ownerTeamId) {
        String value = readName(team);
        if (value != null) {
            return value;
        }

        value = findNameKeyByTeamId(manager, ownerTeamId);
        if (value != null) {
            return value;
        }

        if (team != null && team.isPlayerTeam()) {
            value = normalize(manager.getPlayerName(team.getOwner()).getString());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String readName(Team team) {
        if (team == null) {
            return null;
        }
        String propertyName = normalize(team.getProperty(TeamProperties.DISPLAY_NAME));
        if (propertyName != null) {
            return propertyName;
        }

        String plainName = normalize(team.getName().getString());
        if (plainName != null) {
            return plainName;
        }

        return normalize(team.getColoredName().getString());
    }

    private static String findNameKeyByTeamId(TeamManagerImpl manager, UUID ownerTeamId) {
        for (Map.Entry<String, Team> entry : manager.getTeamNameMap().entrySet()) {
            Team team = entry.getValue();
            if (team == null) {
                continue;
            }
            UUID id = team.getId();
            if (id != null && id.equals(ownerTeamId)) {
                String keyName = normalize(entry.getKey());
                if (keyName != null) {
                    return keyName;
                }
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static String toShortTeamId(UUID id) {
        if (id == null) {
            return null;
        }
        String compact = id.toString().replace("-", "").toUpperCase(Locale.ROOT);
        if (compact.length() <= 8) {
            return compact;
        }
        return compact.substring(0, 8);
    }
}
