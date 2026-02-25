package hello.tradexserver.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chart_layouts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartLayout extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    private String symbol;

    private String resolution;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    public void update(String name, String symbol, String resolution, String content) {
        this.name = name;
        this.symbol = symbol;
        this.resolution = resolution;
        this.content = content;
    }
}
