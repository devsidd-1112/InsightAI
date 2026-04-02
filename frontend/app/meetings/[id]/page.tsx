"use client";

import ProtectedRoute from '@/components/protected-route';
import AppLayout from '@/components/app-layout';
import { useRouter, useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import axiosInstance from '@/lib/api';
import { Meeting, SummaryResponse } from '@/lib/types';
import { formatRelativeDate } from '@/lib/utils';

export default function MeetingDetailPage() {
  const router = useRouter();
  const params = useParams();
  const meetingId = params.id as string;

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [loading, setLoading] = useState(true);
  const [generatingSummary, setGeneratingSummary] = useState(false);
  const [summaryData, setSummaryData] = useState<SummaryResponse | null>(null);
  const [error, setError] = useState('');

  const [isEditingTranscript, setIsEditingTranscript] = useState(false);
  const [newTranscript, setNewTranscript] = useState('');
  const [savingTranscript, setSavingTranscript] = useState(false);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    fetchMeeting();
  }, [meetingId]);

  const fetchMeeting = async () => {
    try {
      const response = await axiosInstance.get(`/api/meetings/${meetingId}`);
      setMeeting(response.data);
    } catch (error: any) {
      setError(error.response?.data?.message || 'Failed to load meeting');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateSummary = async (regenerate = false) => {
    setGeneratingSummary(true);
    setError('');

    try {
      const response = await axiosInstance.post(
        `/api/meetings/${meetingId}/summary?regenerate=${regenerate}`
      );
      setSummaryData(response.data);
      await fetchMeeting();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to generate summary');
    } finally {
      setGeneratingSummary(false);
    }
  };

  const handleDeleteClick = () => {
    setShowDeleteModal(true);
  };

  const confirmDelete = async () => {
    setIsDeleting(true);
    try {
      await axiosInstance.delete(`/api/meetings/${meetingId}`);
      router.push('/meetings');
    } catch (err: any) {
      console.error('Error deleting meeting:', err);
      setError(err.response?.data?.message || 'Failed to delete meeting. Please ensure your backend server has been restarted to apply the latest changes.');
      setShowDeleteModal(false);
      setIsDeleting(false);
    }
  };

  const handleSaveTranscript = async () => {
    if (!newTranscript.trim()) return;
    setSavingTranscript(true);
    setError('');

    try {
      await axiosInstance.put(`/api/meetings/${meetingId}/transcript`, { transcript: newTranscript });
      setIsEditingTranscript(false);
      setNewTranscript('');
      await fetchMeeting();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save transcript');
    } finally {
      setSavingTranscript(false);
    }
  };

  if (loading) {
    return (
      <ProtectedRoute>
        <AppLayout>
          <div className="flex justify-center items-center h-96">
            <div className="flex flex-col items-center gap-4">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
              <p className="text-sm text-muted-foreground">Loading meeting...</p>
            </div>
          </div>
        </AppLayout>
      </ProtectedRoute>
    );
  }

  if (error && !meeting) {
    return (
      <ProtectedRoute>
        <AppLayout>
          <div className="flex items-center justify-center h-96">
            <div className="text-center">
              <div className="flex justify-center mb-4">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-destructive/10">
                  <svg className="w-8 h-8 text-destructive" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
              </div>
              <h2 className="text-2xl font-bold mb-2">Error</h2>
              <p className="text-muted-foreground mb-6">{error}</p>
              <button
                onClick={() => router.push('/meetings')}
                className="px-6 py-3 ai-gradient text-white rounded-xl 
                           hover:shadow-lg hover:scale-[1.02] active:scale-[0.98]
                           transition-all duration-200 font-semibold"
              >
                Back to Meetings
              </button>
            </div>
          </div>
        </AppLayout>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute>
      <AppLayout>
        <div className="space-y-6">
          {/* Back Button */}
          <button
            onClick={() => router.push('/meetings')}
            className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Back to Meetings
          </button>

          {meeting && (
            <>
              {/* Meeting Header */}
              <div className="glass rounded-2xl border border-white/20 p-8">
                <div className="flex items-start gap-4 mb-6">
                  <div className="flex h-14 w-14 items-center justify-center rounded-xl ai-gradient flex-shrink-0">
                    <svg className="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-start">
                      <h2 className="text-3xl font-bold mb-3">{meeting.title}</h2>
                      <button
                        onClick={handleDeleteClick}
                        className="p-2 text-muted-foreground hover:text-white hover:bg-destructive rounded-xl transition-all border border-border hover:border-destructive shadow-sm hover:shadow-destructive/20 ml-4 flex-shrink-0"
                        title="Delete Meeting"
                      >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                    <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                      <span className="flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                        {new Date(meeting.meetingDateTime).toLocaleString('en-US', {
                          month: 'long',
                          day: 'numeric',
                          year: 'numeric',
                          hour: 'numeric',
                          minute: '2-digit'
                        })}
                      </span>
                      <span className="flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                        </svg>
                        {meeting.createdBy.fullName}
                      </span>
                    </div>
                  </div>
                </div>
                {meeting.description && (
                  <p className="text-muted-foreground">{meeting.description}</p>
                )}
              </div>

              {/* AI Summary Section - Highlighted with animated sparkle */}
              {meeting.summary ? (
                <div className="glass rounded-2xl border-2 border-green-500/30 
                                bg-gradient-to-br from-green-50/50 to-emerald-50/50 
                                dark:from-green-900/10 dark:to-emerald-900/10 p-8
                                hover:shadow-2xl hover:border-green-500/50 transition-all duration-300
                                relative overflow-hidden">
                  {/* Animated gradient border effect */}
                  <div className="absolute inset-0 bg-gradient-to-r from-green-500/10 via-emerald-500/10 to-green-500/10 opacity-0 hover:opacity-100 transition-opacity duration-500 pointer-events-none"></div>

                  <div className="relative z-10">
                    <div className="flex items-center justify-between mb-6">
                      <div className="flex items-center gap-3">
                        <div className="flex h-12 w-12 items-center justify-center rounded-xl 
                                        bg-gradient-to-br from-green-500 to-emerald-600
                                        shadow-lg shadow-green-500/30 animate-pulse">
                          <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M12 2L9 9L2 12L9 15L12 22L15 15L22 12L15 9L12 2Z" />
                          </svg>
                        </div>
                        <div>
                          <h3 className="text-xl font-bold">AI-Generated Summary</h3>
                          <p className="text-xs text-green-600 dark:text-green-400 flex items-center gap-1.5 mt-1">
                            <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 24 24">
                              <path d="M12 2L9 9L2 12L9 15L12 22L15 15L22 12L15 9L12 2Z" />
                            </svg>
                            Powered by AI
                          </p>
                        </div>
                      </div>
                      {meeting.transcript && (
                        <button
                          onClick={() => handleGenerateSummary(true)}
                          disabled={generatingSummary}
                          className="px-4 py-2.5 text-sm border-2 border-green-600 text-green-600 dark:text-green-400 
                                     rounded-xl hover:bg-green-600 hover:text-white hover:scale-105 active:scale-95
                                     disabled:opacity-50 disabled:hover:scale-100 disabled:cursor-not-allowed
                                     transition-all duration-200 font-semibold"
                        >
                          {generatingSummary ? 'Regenerating...' : 'Regenerate'}
                        </button>
                      )}
                    </div>
                    <div className="prose prose-sm max-w-none">
                      <div className="whitespace-pre-wrap text-foreground leading-relaxed">{meeting.summary}</div>
                    </div>
                  </div>
                </div>
              ) : meeting.transcript ? (
                <div className="glass rounded-2xl border-2 border-primary/30 p-8
                                hover:shadow-2xl hover:border-primary/50 transition-all duration-300
                                bg-gradient-to-br from-primary/5 to-transparent">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl ai-gradient
                                    shadow-lg shadow-primary/30">
                      <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 24 24">
                        <path d="M12 2L9 9L2 12L9 15L12 22L15 15L22 12L15 9L12 2Z" />
                      </svg>
                    </div>
                    <div>
                      <h3 className="text-xl font-bold">Generate AI Summary</h3>
                      <p className="text-xs text-muted-foreground mt-0.5">Transform your transcript into insights</p>
                    </div>
                  </div>
                  <p className="text-muted-foreground mb-6 leading-relaxed">
                    Use AI to automatically generate a comprehensive summary and extract action items from this meeting transcript.
                  </p>
                  <button
                    onClick={() => handleGenerateSummary(false)}
                    disabled={generatingSummary}
                    className="px-8 py-4 ai-gradient text-white rounded-xl 
                               hover:shadow-2xl hover:shadow-primary/30 hover:scale-[1.02] active:scale-[0.98]
                               disabled:opacity-50 disabled:hover:scale-100 disabled:cursor-not-allowed
                               transition-all duration-300 font-semibold inline-flex items-center gap-2.5
                               text-base"
                  >
                    {generatingSummary ? (
                      <>
                        <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                        Generating Summary...
                      </>
                    ) : (
                      <>
                        <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                          <path d="M12 2L9 9L2 12L9 15L12 22L15 15L22 12L15 9L12 2Z" />
                        </svg>
                        Generate AI Summary
                      </>
                    )}
                  </button>
                </div>
              ) : null}

              {/* Transcript Section - Enhanced styling */}
              {meeting.transcript ? (
                <div className="glass rounded-2xl border border-white/20 p-8
                                hover:shadow-lg transition-all duration-300">
                  <div className="flex items-center justify-between mb-5">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-muted">
                        <svg className="w-5 h-5 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                      </div>
                      <div>
                        <h3 className="text-xl font-bold">Raw Transcript</h3>
                        <p className="text-xs text-muted-foreground">Original meeting recording</p>
                      </div>
                    </div>
                  </div>
                  <div className="bg-muted/30 p-6 rounded-xl whitespace-pre-wrap text-sm leading-relaxed 
                                  custom-scrollbar max-h-96 overflow-y-auto
                                  border border-border/50">
                    {meeting.transcript}
                  </div>
                </div>
              ) : (
                <div className="glass rounded-2xl border border-white/20 p-8">
                  <div className="flex items-center gap-2.5 mb-5">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-yellow-500/20 text-yellow-600 dark:text-yellow-500">
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                      </svg>
                    </div>
                    <div>
                      <h3 className="text-xl font-bold">No Transcript</h3>
                      <p className="text-xs text-muted-foreground">Add transcript to generate AI summary</p>
                    </div>
                  </div>

                  {isEditingTranscript ? (
                    <div className="space-y-4">
                      <textarea
                        value={newTranscript}
                        onChange={(e) => setNewTranscript(e.target.value)}
                        placeholder="Paste your meeting transcript here..."
                        className="w-full h-48 px-4 py-3 bg-background border border-input rounded-xl 
                                   focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent 
                                   custom-scrollbar resize-none"
                      />
                      <div className="flex items-center gap-3 justify-end">
                        <button
                          onClick={() => {
                            setIsEditingTranscript(false);
                            setNewTranscript('');
                          }}
                          disabled={savingTranscript}
                          className="px-4 py-2 text-sm font-semibold rounded-lg hover:bg-muted transition-colors disabled:opacity-50"
                        >
                          Cancel
                        </button>
                        <button
                          onClick={handleSaveTranscript}
                          disabled={savingTranscript || !newTranscript.trim()}
                          className="px-6 py-2 ai-gradient text-white text-sm font-semibold rounded-lg 
                                     hover:shadow-lg active:scale-95 transition-all
                                     disabled:opacity-50 disabled:active:scale-100 disabled:cursor-not-allowed"
                        >
                          {savingTranscript ? 'Saving...' : 'Save Transcript'}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      onClick={() => setIsEditingTranscript(true)}
                      className="px-6 py-3 border-2 border-dashed border-border rounded-xl w-full
                                 hover:border-primary/50 hover:bg-primary/5 transition-all
                                 flex flex-col items-center justify-center gap-2 text-muted-foreground hover:text-foreground"
                    >
                      <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                      </svg>
                      <span className="font-semibold">Add Transcript</span>
                    </button>
                  )}
                </div>
              )}

              {/* Error Message */}
              {error && (
                <div className="glass rounded-2xl border-2 border-destructive/30 bg-destructive/5 p-6">
                  <div className="flex items-start gap-3">
                    <svg className="w-5 h-5 text-destructive flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p className="text-sm text-destructive">{error}</p>
                  </div>
                </div>
              )}

              {/* Success Message */}
              {summaryData && (
                <div className="glass rounded-2xl border-2 border-blue-500/30 bg-blue-50/50 dark:bg-blue-900/10 p-6">
                  <div className="flex items-start gap-3">
                    <svg className="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <div className="flex-1">
                      <p className="text-sm font-medium text-blue-800 dark:text-blue-200 mb-2">Summary generated successfully!</p>
                      {summaryData.tasksCreated > 0 && (
                        <p className="text-sm text-blue-700 dark:text-blue-300 mb-4">
                          {summaryData.tasksCreated} action item(s) extracted and added to your task list.
                        </p>
                      )}
                      <button
                        onClick={() => router.push('/tasks')}
                        className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg 
                                   hover:bg-blue-700 hover:scale-105 active:scale-95
                                   transition-all duration-200 font-semibold"
                      >
                        View Tasks →
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {showDeleteModal && (
          <div
            className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4 animate-in fade-in duration-200"
            role="dialog"
            aria-modal="true"
          >
            <div className="glass rounded-2xl border border-white/20 p-8 max-w-md w-full shadow-2xl animate-in zoom-in-95 duration-200">
              <div className="flex items-center justify-center mb-6">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10 text-destructive mb-2">
                  <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                </div>
              </div>

              <h2 className="text-2xl font-bold text-center mb-3">Delete Meeting?</h2>
              <p className="text-muted-foreground text-center mb-8 leading-relaxed">
                Are you sure you want to delete this meeting? This action will permanently remove the meeting, its transcript, summary, and all associated action items. This cannot be undone.
              </p>

              <div className="flex gap-4">
                <button
                  onClick={() => setShowDeleteModal(false)}
                  disabled={isDeleting}
                  className="flex-1 px-6 py-3 border-2 border-border rounded-xl 
                             hover:bg-accent active:scale-[0.98] disabled:opacity-50
                             transition-all duration-200 font-semibold"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmDelete}
                  disabled={isDeleting}
                  className="flex-1 px-6 py-3 bg-destructive text-destructive-foreground rounded-xl 
                             hover:shadow-lg hover:shadow-destructive/20 hover:scale-[1.02] active:scale-[0.98]
                             disabled:opacity-50 disabled:hover:scale-100 disabled:cursor-not-allowed
                             transition-all duration-200 font-semibold inline-flex items-center justify-center"
                >
                  {isDeleting ? (
                    <span className="flex items-center justify-center gap-2">
                      <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white/80"></div>
                      Deleting...
                    </span>
                  ) : (
                    'Delete'
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      </AppLayout>
    </ProtectedRoute>
  );
}
