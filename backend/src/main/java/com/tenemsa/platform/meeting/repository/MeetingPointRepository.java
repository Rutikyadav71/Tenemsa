package com.tenemsa.platform.meeting.repository;

import com.tenemsa.platform.meeting.model.MeetingPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeetingPointRepository extends JpaRepository<MeetingPoint, Long> {

    List<MeetingPoint> findByCompanyIdAndMeetingIdOrderByPositionAsc(Long companyId, Long meetingId);

    @Modifying
    @Query("delete from MeetingPoint p where p.companyId = :companyId and p.meetingId = :meetingId")
    void deleteAllForMeeting(@Param("companyId") Long companyId, @Param("meetingId") Long meetingId);
}
