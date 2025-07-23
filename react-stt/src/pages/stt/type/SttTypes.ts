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