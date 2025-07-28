export interface SttResponse {
  success: boolean;
  originalFilename: string;
  transcribedText: string;
  confidenceScore: number;
  processingTimeMs: number;
  fileSize: number;
  errorMessage?: string;
  encoding?: string;
  resultCount?: number;
}

export interface TranscriptData {
  transcript: string;
  confidence: number;
  isFinal: boolean;
  stability: number;
  words?: WordTimeInfo[];
}

export interface WordTimeInfo {
  word: string;
  startTime: number;
  endTime: number;
}

export interface StreamingResult {
  transcript: string;
  confidence: number;
  isFinal: boolean;
  timestamp: string;
}