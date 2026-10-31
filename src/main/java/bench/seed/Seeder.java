package bench.seed;

import bench.document.PlaceDocument;
import bench.seed.DataGenerator.GeneratedPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

// MySQL, ES에 같은 데이터 추가 (이미 있는 id는 건너뜀)
@Service
public class Seeder {

    private static final Logger log = LoggerFactory.getLogger(Seeder.class);
    private static final int CHUNK = 2000;
    private static final String FULLTEXT_INDEX = "ft_place_name_address";

    private final JdbcTemplate jdbc;
    private final ElasticsearchOperations elasticsearchOperations;
    private final IndexInitializer indexInitializer;

    public Seeder(JdbcTemplate jdbc, ElasticsearchOperations elasticsearchOperations, IndexInitializer indexInitializer) {
        this.jdbc = jdbc;
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexInitializer = indexInitializer;
    }

    public record SeedResult(long inserted, long total, long elapsedSeconds) {
    }

    public synchronized SeedResult seed(long total) {
        long startedAt = System.currentTimeMillis();
        indexInitializer.ensureIndex();

        Long current = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM place", Long.class);
        long from = (current == null ? 0 : current) + 1;
        for (long start = from; start <= total; start += CHUNK) {
            long end = Math.min(start + CHUNK - 1, total);
            insertChunk(start, end);
            if (end % 50_000 == 0 || end == total) {
                log.info("시딩 진행: {} / {}", end, total);
            }
        }

        ensureFulltextIndex();
        elasticsearchOperations.indexOps(PlaceDocument.class).refresh();
        long inserted = Math.max(0, total - from + 1);
        return new SeedResult(inserted, Math.max(total, from - 1), (System.currentTimeMillis() - startedAt) / 1000);
    }

    private void insertChunk(long start, long end) {
        List<GeneratedPlace> rows = LongStream.rangeClosed(start, end)
                .mapToObj(DataGenerator::generate)
                .toList();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.batchUpdate(
                "INSERT INTO congestion (id, created_at, updated_at) VALUES (?, ?, ?)",
                rows, rows.size(),
                (ps, p) -> {
                    ps.setLong(1, p.id());
                    ps.setTimestamp(2, now);
                    ps.setTimestamp(3, now);
                });

        jdbc.batchUpdate(
                "INSERT INTO place (id, name, address, category, phone, url, summary, latitude, longitude, " +
                        "review_count, congestion_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rows, rows.size(),
                (ps, p) -> {
                    ps.setLong(1, p.id());
                    ps.setString(2, p.name());
                    ps.setString(3, p.address());
                    ps.setString(4, p.category());
                    ps.setString(5, p.phone());
                    ps.setString(6, p.url());
                    ps.setString(7, p.summary());
                    ps.setDouble(8, p.latitude());
                    ps.setDouble(9, p.longitude());
                    ps.setInt(10, 0);
                    ps.setLong(11, p.id());
                    ps.setTimestamp(12, now);
                    ps.setTimestamp(13, now);
                });

        jdbc.update("UPDATE congestion SET place_id = id WHERE id BETWEEN ? AND ?", start, end);

        List<PlaceDocument> docs = rows.stream()
                .map(p -> new PlaceDocument(p.id(), p.name(), p.address()))
                .toList();
        elasticsearchOperations.save(docs);
    }

    private void ensureFulltextIndex() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = 'place' AND index_name = ?",
                Integer.class, FULLTEXT_INDEX);
        if (exists == null || exists == 0) {
            log.info("MySQL FULLTEXT(ngram) 인덱스 생성 중...");
            jdbc.execute("ALTER TABLE place ADD FULLTEXT INDEX " + FULLTEXT_INDEX + " (name, address) WITH PARSER ngram");
        }
    }

    public synchronized void reset() {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                st.execute("TRUNCATE TABLE place");
                st.execute("TRUNCATE TABLE congestion");
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
        indexInitializer.recreateIndex();
    }

    public long dbCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM place", Long.class);
        return n == null ? 0 : n;
    }

    public long esCount() {
        return elasticsearchOperations.count(
                org.springframework.data.elasticsearch.core.query.Query.findAll(), PlaceDocument.class);
    }
}
