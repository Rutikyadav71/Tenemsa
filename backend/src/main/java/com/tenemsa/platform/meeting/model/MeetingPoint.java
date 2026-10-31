package com.tenemsa.platform.meeting.model;

import jakarta.persistence.*;

/** One decision or one open question of a meeting. */
@Entity
@Table(name = "meeting_points", indexes = {
        @Index(name = "idx_meeting_points_meeting", columnList = "company_id, meeting_id")
})
public class MeetingPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType type;

    @Column(name = "content", nullable = false, length = 1000)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private int position;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getMeetingId() { return meetingId; }
    public void setMeetingId(Long meetingId) { this.meetingId = meetingId; }
    public PointType getType() { return type; }
    public void setType(PointType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
