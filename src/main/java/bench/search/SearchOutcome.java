package bench.search;

import java.util.List;

public record SearchOutcome(long total, List<Long> ids, List<String> names) {
}
