package bench.seed;

import java.util.List;
import java.util.SplittableRandom;

// 더미 데이터 생성 (같은 id면 항상 같은 데이터)
// 이름은 앞쪽 단어가 더 자주 뽑히게 해서 흔한 키워드, 드문 키워드가 같이 생김
public final class DataGenerator {

    private DataGenerator() {
    }

    public record GeneratedPlace(long id, String name, String address, String category,
                                 String phone, String url, String summary,
                                 double latitude, double longitude) {
    }

    // 앞쪽 id는 실제 장소로 고정
    private static final String[][] LANDMARKS = {
            {"노들섬", "서울특별시 용산구 양녕로 445"},
            {"마포중앙도서관", "서울특별시 마포구 성산로 128"},
            {"국립중앙도서관", "서울특별시 서초구 반포대로 201"},
            {"서울도서관", "서울특별시 중구 세종대로 110"},
            {"정독도서관", "서울특별시 종로구 북촌로5길 48"},
            {"남산도서관", "서울특별시 용산구 소월로 109"},
    };

    private static final String[][] GU_DONGS = {
            {"강남구", "역삼동", "삼성동", "논현동", "신사동"},
            {"강동구", "천호동", "길동", "암사동"},
            {"강북구", "수유동", "미아동", "번동"},
            {"강서구", "화곡동", "등촌동", "마곡동"},
            {"관악구", "봉천동", "신림동", "남현동"},
            {"광진구", "자양동", "구의동", "화양동"},
            {"구로구", "구로동", "신도림동", "개봉동"},
            {"금천구", "가산동", "독산동", "시흥동"},
            {"노원구", "상계동", "중계동", "공릉동"},
            {"도봉구", "쌍문동", "방학동", "창동"},
            {"동대문구", "회기동", "청량리동", "장안동"},
            {"동작구", "사당동", "흑석동", "노량진동"},
            {"마포구", "망원동", "연남동", "합정동", "서교동", "상수동"},
            {"서대문구", "신촌동", "연희동", "홍제동"},
            {"서초구", "서초동", "반포동", "방배동"},
            {"성동구", "성수동", "왕십리동", "금호동"},
            {"성북구", "성북동", "안암동", "정릉동"},
            {"송파구", "잠실동", "방이동", "문정동"},
            {"양천구", "목동", "신정동", "신월동"},
            {"영등포구", "문래동", "여의도동", "당산동"},
            {"용산구", "이태원동", "한남동", "후암동"},
            {"은평구", "불광동", "응암동", "진관동"},
            {"종로구", "익선동", "삼청동", "혜화동", "부암동"},
            {"중구", "을지로동", "명동", "필동"},
            {"중랑구", "면목동", "상봉동", "망우동"},
    };

    private static final String[] ROADS = {
            "월드컵로", "양화로", "성수이로", "왕십리로", "테헤란로", "도산대로", "세종대로", "종로",
            "을지로", "퇴계로", "한강대로", "올림픽로", "동일로", "통일로", "연희로", "망원로",
            "독막로", "와우산로", "서강로", "신촌로", "보문로", "도봉로", "시흥대로", "경인로",
    };

    // 앞에 있을수록 자주 뽑힘
    private static final String[] STEMS = {
            "책", "숲", "햇살", "온기", "골목", "다락", "서재", "책장", "오후", "모퉁이",
            "별빛", "달빛", "구름", "바람", "나무", "정원", "새벽", "고요", "푸른", "하늘",
            "느린", "작은", "오래된", "우리", "동네", "페이지", "문장", "여백", "쉼표", "마침표",
            "온새미", "가온", "다솜", "미리내", "윤슬", "물결", "산책", "기억", "노을", "아지트",
            "북극성", "등대", "섬", "언덕", "들꽃", "솔숲", "은행나무", "단풍", "첫눈", "봄날",
    };

    private static final String[] TYPES = {
            "카페", "북카페", "스터디카페", "도서관", "작은도서관", "책방", "독서실", "서점", "라운지", "라이브러리",
    };

    private static final String[] CATEGORIES = {"CAFE", "LIBRARY", "STUDY", "BOOKSTORE", "ETC"};

    public static GeneratedPlace generate(long id) {
        SplittableRandom r = new SplittableRandom(id * 0x9E3779B97F4A7C15L + 7);
        String[] guDongs = GU_DONGS[r.nextInt(GU_DONGS.length)];
        String gu = guDongs[0];
        String dong = guDongs[1 + r.nextInt(guDongs.length - 1)];

        String name;
        String address;
        if (id <= LANDMARKS.length) {
            name = LANDMARKS[(int) id - 1][0];
            address = LANDMARKS[(int) id - 1][1];
        } else {
            name = buildName(r, dong);
            address = r.nextInt(2) == 0
                    ? "서울특별시 " + gu + " " + dong + " " + (1 + r.nextInt(900)) + "-" + (1 + r.nextInt(60))
                    : "서울특별시 " + gu + " " + ROADS[r.nextInt(ROADS.length)] + " " + (1 + r.nextInt(400));
        }

        return new GeneratedPlace(
                id,
                name,
                address,
                CATEGORIES[r.nextInt(CATEGORIES.length)],
                "02-" + (100 + r.nextInt(900)) + "-" + (1000 + r.nextInt(9000)),
                "https://example.com/place/" + id,
                name + "에서 조용히 책을 읽을 수 있어요.",
                37.45 + r.nextDouble() * 0.2,
                126.85 + r.nextDouble() * 0.3);
    }

    private static String buildName(SplittableRandom r, String dong) {
        String stem = skewed(r, STEMS);
        String type = skewed(r, TYPES);
        int pattern = r.nextInt(10);
        if (pattern < 4) {
            return stem + type;
        }
        if (pattern < 7) {
            return stem + " " + type + " " + dong.replace("동", "") + "점";
        }
        if (pattern < 9) {
            return stem + skewed(r, STEMS) + type;
        }
        return dong.replace("동", "") + " " + stem + " " + type;
    }

    private static String skewed(SplittableRandom r, String[] values) {
        double x = r.nextDouble();
        return values[(int) (x * x * values.length)];
    }

    public static List<String[]> landmarks() {
        return List.of(LANDMARKS);
    }
}
