package de.pumpecraft.mod;

import java.net.URI;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

record ModerationSettings(URI reportsUrl, URI reportUrlBase, long synchronizationIntervalTicks) {
    private static final URI DEFAULT_REPORTS_URL = URI.create(
        "https://support.pumpe-klein.de/minecraft/reports"
    );
    private static final URI DEFAULT_REPORT_URL_BASE = URI.create(
        "https://support.pumpe-klein.de/minecraft/report"
    );

    static ModerationSettings from(FileConfiguration config, Logger logger) {
        return new ModerationSettings(
            readUrl(config, logger, "reports-url", DEFAULT_REPORTS_URL),
            readUrl(config, logger, "report-url-base", DEFAULT_REPORT_URL_BASE),
            Math.max(20L, config.getLong("synchronization-interval-ticks", 40L))
        );
    }

    URI reportUrl(int reportId) {
        return URI.create(stripTrailingSlash(reportUrlBase.toString()) + "/" + reportId);
    }

    private static URI readUrl(FileConfiguration config, Logger logger, String path, URI fallback) {
        String configured = config.getString(path, fallback.toString()).trim();
        try {
            URI url = URI.create(configured);
            String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && url.getHost() != null) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
        }
        logger.warning("Invalid " + path + "; using " + fallback);
        return fallback;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
