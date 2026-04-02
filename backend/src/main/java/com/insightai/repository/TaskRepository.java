package com.insightai.repository;

import com.insightai.entity.Task;
import com.insightai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByMeeting_CreatedBy(User user);

    List<Task> findByAssignedTo(User user);

    @Query("SELECT t FROM Task t WHERE t.meeting.createdBy = :user OR t.assignedTo = :user")
    List<Task> findByMeeting_CreatedByOrAssignedTo(@Param("user") User user);

    void deleteByMeeting(com.insightai.entity.Meeting meeting);
}
