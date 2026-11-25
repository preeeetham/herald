package dev.herald.api.tenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private static final String COLUMNS =
            "id, slug, name, status, rate_limit_per_second, rate_limit_burst, max_concurrency, created_at, updated_at";

    private final JdbcClient jdbc;

    TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Tenant tenant) {
        jdbc.sql("""
                INSERT INTO tenant (id, slug, name, status, rate_limit_per_second, rate_limit_burst, max_concurrency)
                VALUES (:id, :slug, :name, :status, :rps, :burst, :concurrency)
                """)
                .param("id", tenant.id())
                .param("slug", tenant.slug())
                .param("name", tenant.name())
                .param("status", tenant.status().name())
                .param("rps", tenant.rateLimitPerSecond())
                .param("burst", tenant.rateLimitBurst())
                .param("concurrency", tenant.maxConcurrency())
                .update();
    }

    public Optional<Tenant> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenant WHERE id = :id")
                .param("id", id)
                .query(TenantRepository::map)
                .optional();
    }

    public boolean existsBySlug(String slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM tenant WHERE slug = :slug)")
                .param("slug", slug)
                .query(Boolean.class)
                .single();
    }

    public List<Tenant> findPage(UUID after, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenant WHERE (CAST(:after AS uuid) IS NULL OR id > :after) ORDER BY id LIMIT :limit")
                .param("after", after)
                .param("limit", limit)
                .query(TenantRepository::map)
                .list();
    }

    public void update(Tenant tenant) {
        jdbc.sql("""
                UPDATE tenant SET name = :name, status = :status, rate_limit_per_second = :rps,
                       rate_limit_burst = :burst, max_concurrency = :concurrency, updated_at = now()
                WHERE id = :id
                """)
                .param("id", tenant.id())
                .param("name", tenant.name())
                .param("status", tenant.status().name())
                .param("rps", tenant.rateLimitPerSecond())
                .param("burst", tenant.rateLimitBurst())
                .param("concurrency", tenant.maxConcurrency())
                .update();
    }

    static Tenant map(ResultSet rs, int row) throws SQLException {
        return new Tenant(
                rs.getObject("id", UUID.class),
                rs.getString("slug"),
                rs.getString("name"),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getInt("rate_limit_per_second"),
                rs.getInt("rate_limit_burst"),
                rs.getInt("max_concurrency"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
