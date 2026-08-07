package de.pumpecraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class PumpeDatabasePlugin extends JavaPlugin {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private HikariDataSource dataSource;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            DatabaseSettings settings = loadSettings();
            dataSource = new HikariDataSource(createPoolConfig(settings));
            verifyConnection(dataSource);

            MigrateResult migration = Flyway.configure(getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load()
                .migrate();

            DatabaseService service = new DatabaseService(dataSource);
            getServer().getServicesManager().register(
                DatabaseService.class,
                service,
                this,
                ServicePriority.Normal
            );
            getLogger().info(
                "Database ready; schema version " + migration.targetSchemaVersion
                    + ", applied " + migration.migrationsExecuted + " migration(s)."
            );
        } catch (RuntimeException exception) {
            closePool();
            getLogger().log(Level.SEVERE, "Database startup failed; dependent plugins cannot start.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        closePool();
    }

    private DatabaseSettings loadSettings() {
        FileConfiguration config = getConfig();
        String host = setting("DB_HOST", config.getString("database.host", ""));
        int port = integerSetting("DB_PORT", config.getInt("database.port", 3306));
        String database = setting("DB_NAME", config.getString("database.name", ""));
        String user = setting("DB_USER", config.getString("database.user", ""));
        String password = setting("DB_PASSWORD", config.getString("database.password", ""));
        String charset = setting("DB_CHARSET", config.getString("database.charset", "utf8mb4"));
        int poolSize = integerSetting("DB_POOL_SIZE", config.getInt("database.pool-size", 10));
        long timeout = longSetting(
            "DB_CONNECTION_TIMEOUT_MS",
            config.getLong("database.connection-timeout-ms", Duration.ofSeconds(10).toMillis())
        );

        requireValue("DB_HOST", "database.host", host);
        requireValue("DB_NAME", "database.name", database);
        requireValue("DB_USER", "database.user", user);
        requireValue("DB_PASSWORD", "database.password", password);
        requireValue("DB_CHARSET", "database.charset", charset);
        if (!SQL_IDENTIFIER.matcher(database).matches()) {
            throw new IllegalArgumentException("DB_NAME may only contain letters, digits and underscores.");
        }
        if (!SQL_IDENTIFIER.matcher(charset).matches()) {
            throw new IllegalArgumentException("DB_CHARSET contains invalid characters.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("DB_PORT must be between 1 and 65535.");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("DB_POOL_SIZE must be positive.");
        }
        if (timeout < 250L) {
            throw new IllegalArgumentException("DB_CONNECTION_TIMEOUT_MS must be at least 250.");
        }

        return new DatabaseSettings(host, port, database, user, password, charset, poolSize, timeout);
    }

    private HikariConfig createPoolConfig(DatabaseSettings settings) {
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("PumpeCraft-Database");
        pool.setDriverClassName("org.mariadb.jdbc.Driver");
        pool.setJdbcUrl(
            "jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
        );
        pool.setUsername(settings.user());
        pool.setPassword(settings.password());
        pool.setMaximumPoolSize(settings.poolSize());
        pool.setMinimumIdle(Math.min(2, settings.poolSize()));
        pool.setConnectionTimeout(settings.connectionTimeoutMs());
        pool.setKeepaliveTime(Duration.ofMinutes(2).toMillis());
        pool.setMaxLifetime(Duration.ofMinutes(25).toMillis());
        pool.setConnectionTestQuery("SELECT 1");
        pool.setConnectionInitSql("SET NAMES " + settings.charset());
        return pool;
    }

    private void verifyConnection(HikariDataSource pool) {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new IllegalStateException("Database health check returned an unexpected result.");
            }
        } catch (Exception exception) {
            throw new DatabaseException("Could not connect to the configured database.", exception);
        }
    }

    private void closePool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static String setting(String environmentName, String configuredValue) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? configuredValue.trim() : environmentValue.trim();
    }

    private static int integerSetting(String environmentName, int configuredValue) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? configuredValue : Integer.parseInt(environmentValue.trim());
    }

    private static long longSetting(String environmentName, long configuredValue) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? configuredValue : Long.parseLong(environmentValue.trim());
    }

    private void requireValue(String environmentName, String configPath, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                environmentName + " is required. Set the environment variable or '" + configPath
                    + "' in " + new java.io.File(getDataFolder(), "config.yml").getAbsolutePath()
            );
        }
    }

    private record DatabaseSettings(
        String host,
        int port,
        String database,
        String user,
        String password,
        String charset,
        int poolSize,
        long connectionTimeoutMs
    ) {
    }
}
