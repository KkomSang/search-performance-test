package bench.seed;

import bench.document.PlaceDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

// 시작 시 jaso 인덱스 생성
@Component
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);
    private static final int MAX_ATTEMPTS = 30;

    private final ElasticsearchOperations elasticsearchOperations;

    public IndexInitializer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex();
    }

    public void ensureIndex() {
        IndexOperations ops = elasticsearchOperations.indexOps(PlaceDocument.class);
        for (int attempt = 1; ; attempt++) {
            try {
                if (!ops.exists()) {
                    ops.createWithMapping();
                    log.info("Elasticsearch 'jaso' 인덱스 생성 완료");
                }
                return;
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new IllegalStateException("Elasticsearch 연결 실패. docker compose ps로 상태 확인", e);
                }
                log.warn("Elasticsearch 대기 중... ({}/{}) {}", attempt, MAX_ATTEMPTS, e.getMessage());
                sleep();
            }
        }
    }

    public void recreateIndex() {
        IndexOperations ops = elasticsearchOperations.indexOps(PlaceDocument.class);
        if (ops.exists()) {
            ops.delete();
        }
        ops.createWithMapping();
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }
}
