package bench.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

// 요청별 실행된 SQL 수 카운트
public class QueryCounter implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int get() {
        return COUNT.get()[0];
    }
}
