package com.insightai.service;

import com.insightai.dto.*;
import com.insightai.entity.Meeting;
import com.insightai.entity.Role;
import com.insightai.entity.Task;
import com.insightai.entity.TaskStatus;
import com.insightai.entity.User;
import com.insightai.exception.AuthorizationException;
import com.insightai.exception.ResourceNotFoundException;
import com.insightai.exception.ValidationException;
import com.insightai.repository.MeetingRepository;
import com.insightai.repository.TaskRepository;
import com.insightai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final Logger logger = LoggerFactory.getLogger(MeetingService.class);

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final GeminiService geminiService;

    @Transactional
    public MeetingResponse createMeeting(MeetingRequest request, User currentUser) {
        logger.info("Creating meeting for user: {}", currentUser.getEmail());

        Meeting meeting = Meeting.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .transcript(request.getTranscript())
                .meetingDateTime(request.getMeetingDateTime())
                .createdBy(currentUser)
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        logger.info("Meeting created with id: {}", savedMeeting.getId());

        return mapToResponse(savedMeeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> getUserMeetings(User currentUser) {
        logger.info("Fetching meetings for user: {} with role: {}", currentUser.getEmail(), currentUser.getRole());

        List<Meeting> meetings;

        if (currentUser.getRole() == Role.ADMIN) {
            // Admin can see all meetings
            meetings = meetingRepository.findAll();
            logger.debug("Admin user - returning all {} meetings", meetings.size());
        } else {
            // Regular users see only their own meetings
            meetings = meetingRepository.findByCreatedBy(currentUser);
            logger.debug("Regular user - returning {} meetings", meetings.size());
        }

        return meetings.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeetingById(Long id, User currentUser) {
        logger.info("Fetching meeting {} for user: {}", id, currentUser.getEmail());

        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + id));

        // Check authorization: user must own the meeting or be an admin
        if (!meeting.getCreatedBy().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            logger.warn("User {} attempted to access meeting {} without authorization",
                    currentUser.getEmail(), id);
            throw new AuthorizationException("You don't have permission to access this meeting");
        }

        return mapToResponse(meeting);
    }

    @Transactional
    public MeetingResponse updateTranscript(Long meetingId, String transcript, User currentUser) {
        logger.info("Updating transcript for meeting {} by user: {}", meetingId, currentUser.getEmail());

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + meetingId));

        if (!meeting.getCreatedBy().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            logger.warn("User {} attempted to update transcript for meeting {} without authorization",
                    currentUser.getEmail(), meetingId);
            throw new AuthorizationException("You don't have permission to update this meeting");
        }

        meeting.setTranscript(transcript);
        Meeting savedMeeting = meetingRepository.save(meeting);

        return mapToResponse(savedMeeting);
    }

    @Transactional
    public void deleteMeeting(Long meetingId, User currentUser) {
        logger.info("Deleting meeting {} by user: {}", meetingId, currentUser.getEmail());

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + meetingId));

        if (!meeting.getCreatedBy().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            logger.warn("User {} attempted to delete meeting {} without authorization",
                    currentUser.getEmail(), meetingId);
            throw new AuthorizationException("You don't have permission to delete this meeting");
        }

        // Delete associated tasks first to avoid foreign key constraint violations
        taskRepository.deleteByMeeting(meeting);

        // Delete the meeting
        meetingRepository.delete(meeting);
        logger.info("Meeting {} successfully deleted", meetingId);
    }

    @Transactional
    public SummaryResponse generateSummary(Long meetingId, User currentUser, boolean regenerate) {
        logger.info("Generating summary for meeting {} by user: {}", meetingId, currentUser.getEmail());

        // Fetch meeting
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found with id: " + meetingId));

        // Check authorization: user must own the meeting or be an admin
        if (!meeting.getCreatedBy().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            logger.warn("User {} attempted to generate summary for meeting {} without authorization",
                    currentUser.getEmail(), meetingId);
            throw new AuthorizationException("You don't have permission to generate summary for this meeting");
        }

        // Check if transcript exists
        if (!StringUtils.hasText(meeting.getTranscript())) {
            throw new ValidationException("Cannot generate summary: meeting has no transcript");
        }

        // Check if summary already exists (unless regenerate flag is set)
        if (StringUtils.hasText(meeting.getSummary()) && !regenerate) {
            logger.info("Summary already exists for meeting {}. Use regenerate=true to regenerate.", meetingId);
            throw new ValidationException("Summary already exists. Use regenerate parameter to regenerate.");
        }

        // Call Gemini API to generate summary
        GeminiParsedResponse geminiResponse = geminiService.generateSummary(meeting.getTranscript());

        // Build summary text
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("SUMMARY:\n");

        String cleanSummary = geminiResponse.getSummary();
        if (cleanSummary != null) {
            // Hotfix: If Gemini nested the entire JSON object inside the summary string,
            // we try to extract just the summary text from it.
            if (cleanSummary.contains("\"summary\":")) {
                try {
                    // Quick and dirty extraction of just the summary text
                    int summaryStart = cleanSummary.indexOf("\"summary\":") + 10;
                    int startQuote = cleanSummary.indexOf("\"", summaryStart);
                    int endQuote = cleanSummary.indexOf("\"", startQuote + 1);

                    if (startQuote != -1 && endQuote != -1) {
                        cleanSummary = cleanSummary.substring(startQuote + 1, endQuote);
                    }
                } catch (Exception e) {
                    // If parsing fails, just use the string as is, we'll let the user see it
                    logger.warn("Failed to extract summary from nested JSON string: {}", e.getMessage());
                }
            }
            // Also clean up any loose braces just in case
            cleanSummary = cleanSummary.replaceFirst("^\\s*\\{\\s*", "");
        }

        summaryBuilder.append(cleanSummary).append("\n\n");

        if (geminiResponse.getKeyDecisions() != null && !geminiResponse.getKeyDecisions().isEmpty()) {
            summaryBuilder.append("KEY DECISIONS:\n");
            for (String decision : geminiResponse.getKeyDecisions()) {
                // Some models return the JSON structure as text here too, clean strings
                String cleanDecision = decision.replace("\"", "").trim();
                if (cleanDecision.endsWith(","))
                    cleanDecision = cleanDecision.substring(0, cleanDecision.length() - 1);

                summaryBuilder.append("- ").append(cleanDecision).append("\n");
            }
            summaryBuilder.append("\n");
        }

        // Save summary to meeting
        meeting.setSummary(summaryBuilder.toString().trim());
        meeting.setSummaryGeneratedBy(currentUser.getId());
        meetingRepository.save(meeting);

        logger.info("Summary saved for meeting {}", meetingId);

        // Create tasks from action items
        int tasksCreated = 0;
        List<ActionItem> actionItems = new ArrayList<>();

        if (geminiResponse.getActionItems() != null && !geminiResponse.getActionItems().isEmpty()) {
            for (ActionItem actionItem : geminiResponse.getActionItems()) {
                Task task = Task.builder()
                        .title(actionItem.getAction())
                        .status(TaskStatus.TODO)
                        .meeting(meeting)
                        .build();

                // Try to find user by owner name if specified
                if (StringUtils.hasText(actionItem.getOwner()) &&
                        !actionItem.getOwner().equalsIgnoreCase("Unassigned")) {
                    // For now, we'll leave assignedTo as null
                    // In a real system, you might want to match owner names to users
                }

                taskRepository.save(task);
                tasksCreated++;
                actionItems.add(actionItem);
            }

            logger.info("Created {} tasks from action items for meeting {}", tasksCreated, meetingId);
        }

        return SummaryResponse.builder()
                .summary(meeting.getSummary())
                .actionItems(actionItems)
                .tasksCreated(tasksCreated)
                .build();
    }

    private MeetingResponse mapToResponse(Meeting meeting) {
        return MeetingResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .transcript(meeting.getTranscript())
                .summary(meeting.getSummary())
                .meetingDateTime(meeting.getMeetingDateTime())
                .createdBy(UserResponse.builder()
                        .id(meeting.getCreatedBy().getId())
                        .fullName(meeting.getCreatedBy().getFullName())
                        .email(meeting.getCreatedBy().getEmail())
                        .role(meeting.getCreatedBy().getRole().name())
                        .createdAt(meeting.getCreatedBy().getCreatedAt())
                        .build())
                .summaryGeneratedBy(meeting.getSummaryGeneratedBy())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }
}
