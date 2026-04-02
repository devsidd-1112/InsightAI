package com.insightai.controller;

import com.insightai.dto.MeetingRequest;
import com.insightai.dto.MeetingResponse;
import com.insightai.dto.SummaryResponse;
import com.insightai.dto.TranscriptRequest;
import com.insightai.entity.User;
import com.insightai.repository.UserRepository;
import com.insightai.security.UserPrincipal;
import com.insightai.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

        private final MeetingService meetingService;
        private final UserRepository userRepository;

        @PostMapping
        public ResponseEntity<MeetingResponse> createMeeting(
                        @Valid @RequestBody MeetingRequest request,
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                MeetingResponse response = meetingService.createMeeting(request, currentUser);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping
        public ResponseEntity<List<MeetingResponse>> getAllMeetings(
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<MeetingResponse> meetings = meetingService.getUserMeetings(currentUser);
                return ResponseEntity.ok(meetings);
        }

        @GetMapping("/{id}")
        public ResponseEntity<MeetingResponse> getMeetingById(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                MeetingResponse response = meetingService.getMeetingById(id, currentUser);
                return ResponseEntity.ok(response);
        }

        @PostMapping("/{id}/summary")
        public ResponseEntity<SummaryResponse> generateSummary(
                        @PathVariable Long id,
                        @RequestParam(required = false, defaultValue = "false") boolean regenerate,
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                SummaryResponse response = meetingService.generateSummary(id, currentUser, regenerate);
                return ResponseEntity.ok(response);
        }

        @PutMapping("/{id}/transcript")
        public ResponseEntity<MeetingResponse> updateTranscript(
                        @PathVariable Long id,
                        @Valid @RequestBody TranscriptRequest request,
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                MeetingResponse response = meetingService.updateTranscript(id, request.getTranscript(), currentUser);
                return ResponseEntity.ok(response);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteMeeting(
                        @PathVariable Long id,
                        @AuthenticationPrincipal UserPrincipal userPrincipal) {

                User currentUser = userRepository.findById(userPrincipal.getId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                meetingService.deleteMeeting(id, currentUser);
                return ResponseEntity.noContent().build();
        }
}
