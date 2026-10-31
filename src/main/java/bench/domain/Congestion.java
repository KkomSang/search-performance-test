package bench.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// 기존 서버와 동일한 구조
@Entity
@Table(name = "congestion")
public class Congestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    private Float zero;
    private Float one;
    private Float two;
    private Float three;
    private Float four;
    private Float five;
    private Float six;
    private Float seven;
    private Float eight;
    private Float nine;
    private Float ten;
    private Float eleven;
    private Float twelve;
    private Float thirteen;
    private Float fourteen;
    private Float fifteen;
    private Float sixteen;
    private Float seventeen;
    private Float eighteen;
    private Float nineteen;
    private Float twenty;
    private Float twentyOne;
    private Float twentyTwo;
    private Float twentyThree;
    private Float twentyFour;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Congestion() {
    }

    public Long getId() {
        return id;
    }
}
