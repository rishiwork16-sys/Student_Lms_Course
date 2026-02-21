package com.finallms.backend.entity;

import javax.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id")
    @ToString.Exclude
    private Module module; // Link to the module

    private String title;

    @Column(length = 2000)
    private String description;

    private int durationMinutes;
    private int passingMarks;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<Question> questions;

    public int getPassingMarks() {
        return this.passingMarks;
    }
}
