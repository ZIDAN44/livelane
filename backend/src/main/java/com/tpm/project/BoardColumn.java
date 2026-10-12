package com.tpm.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A Kanban column (status). Tasks moved into a {@code doneColumn} are marked completed. */
@Entity
@Table(name = "board_columns")
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String name;

    /** Fractional-index key; see {@link com.tpm.common.FractionalIndex}. */
    @Column(nullable = false)
    private String position;

    @Column(name = "wip_limit")
    private Integer wipLimit;

    @Column(name = "done_column")
    private boolean doneColumn;

    @Version
    private long version;

    protected BoardColumn() {}

    public BoardColumn(Project project, String name, String position, Integer wipLimit, boolean doneColumn) {
        this.project = project;
        this.name = name;
        this.position = position;
        this.wipLimit = wipLimit;
        this.doneColumn = doneColumn;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getPosition() { return position; }
    public Integer getWipLimit() { return wipLimit; }
    public boolean isDoneColumn() { return doneColumn; }
    public long getVersion() { return version; }

    public void setName(String name) { this.name = name; }
    public void setPosition(String position) { this.position = position; }
    public void setWipLimit(Integer wipLimit) { this.wipLimit = wipLimit; }
    public void setDoneColumn(boolean doneColumn) { this.doneColumn = doneColumn; }
}
