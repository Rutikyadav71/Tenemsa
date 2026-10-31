package com.tenemsa.platform.meeting.repository;

import com.tenemsa.platform.meeting.model.MeetingActionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItem, Long> {

    List<MeetingActionItem> findByCompanyIdAndMeetingIdOrderByPositionAsc(Long companyId, Long meetingId);

    List<MeetingActionItem> findByCompanyIdAndMeetingIdIn(Long companyId, Collection<Long> meetingIds);

    @Modifying
    @Query("delete from MeetingActionItem a where a.companyId = :companyId and a.meetingId = :meetingId")
    void deleteAllForMeeting(@Param("companyId") Long companyId, @Param("meetingId") Long meetingId);
}
