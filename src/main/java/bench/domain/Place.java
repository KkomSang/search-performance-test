package bench.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
    private String category;
    private String phone;
    private String url;
    private String summary;
    private Double latitude;
    private Double longitude;
    private Integer reviewCount;

    @OneToOne
    private Congestion congestion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Place() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Congestion getCongestion() {
        return congestion;
    }
}
