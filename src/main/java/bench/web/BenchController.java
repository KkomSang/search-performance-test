package bench.web;

import bench.search.SearchOutcome;
import bench.search.SearchService;
import bench.search.Strategy;
import bench.seed.Seeder;
import bench.support.QueryCounter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bench")
public class BenchController {

    private final SearchService searchService;
    private final Seeder seeder;

    public BenchController(SearchService searchService, Seeder seeder) {
        this.searchService = searchService;
        this.seeder = seeder;
    }

    public record SearchResponse(String strategy, String q, long total, int size,
                                 int queryCount, double serverMs, List<String> names) {
    }

    @GetMapping("/search/{strategy}")
    public SearchResponse search(@PathVariable String strategy,
                                 @RequestParam String q,
                                 @RequestParam(defaultValue = "1") int page) {
        return run(parse(strategy), q, page);
    }

    // 방식별 결과 수, SQL 수 비교 (속도 측정용 X)
    @GetMapping("/compare")
    public List<SearchResponse> compare(@RequestParam String q) {
        List<SearchResponse> result = new ArrayList<>();
        for (Strategy s : Strategy.values()) {
            result.add(run(s, q, 1));
        }
        return result;
    }

    @PostMapping("/seed")
    public Seeder.SeedResult seed(@RequestParam long total) {
        return seeder.seed(total);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        seeder.reset();
        return stats();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("mysqlRows", seeder.dbCount());
        stats.put("esDocs", seeder.esCount());
        return stats;
    }

    private SearchResponse run(Strategy strategy, String q, int page) {
        QueryCounter.reset();
        long t0 = System.nanoTime();
        SearchOutcome outcome = searchService.search(strategy, q, page);
        double serverMs = (System.nanoTime() - t0) / 1_000_000.0;
        return new SearchResponse(strategy.code(), q, outcome.total(), outcome.ids().size(),
                QueryCounter.get(), serverMs, outcome.names());
    }

    private static Strategy parse(String code) {
        try {
            return Strategy.from(code);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
