package bench.search;

import java.util.Arrays;

public enum Strategy {
    // JPQL LIKE
    LIKE("like"),
    // ES 검색 후 결과마다 findById (실제 서버 코드)
    ES_N1("es-n1"),
    // MySQL FULLTEXT (ngram)
    FULLTEXT("fulltext"),
    // ES 검색 후 findAllById로 한 번에 조회
    MY("es-my");

    private final String code;

    Strategy(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Strategy from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown strategy: " + code + " (like, es-n1, fulltext, es-my)"));
    }
}
