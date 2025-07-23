import React, { useState, useRef } from 'react';
import { Link } from 'react-router-dom';

interface JobStatus {
  jobId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  result?: string;
  error?: string;
}

const SttAsync: React.FC = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [jobStatus, setJobStatus] = useState<JobStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        audioChunksRef.current.push(event.data);
      };

      mediaRecorder.onstop = () => {
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        const audioFile = new File([audioBlob], 'recording.wav', { type: 'audio/wav' });
        setAudioFile(audioFile);
        stream.getTracks().forEach(track => track.stop());
      };

      mediaRecorder.start();
      setIsRecording(true);
      setError('');
    } catch (err) {
      setError('마이크 접근 권한이 필요합니다.');
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
    }
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      setAudioFile(file);
      setJobStatus(null);
      setError('');
    }
  };

  const pollJobStatus = async (jobId: string) => {
    try {
      const response = await fetch(`/api/stt/async/status/${jobId}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setJobStatus(data);

      if (data.status === 'completed' || data.status === 'failed') {
        if (pollIntervalRef.current) {
          clearInterval(pollIntervalRef.current);
          pollIntervalRef.current = null;
        }
        setLoading(false);
      }
    } catch (err) {
      setError('작업 상태 확인 중 오류가 발생했습니다: ' + (err instanceof Error ? err.message : String(err)));
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }
      setLoading(false);
    }
  };

  const handleSttAsync = async () => {
    if (!audioFile) {
      setError('오디오 파일을 선택하거나 녹음해주세요.');
      return;
    }

    setLoading(true);
    setError('');
    setJobStatus(null);

    try {
      const formData = new FormData();
      formData.append('audio', audioFile);

      const response = await fetch(`${import.meta.env.VITE_WS_BASE_URL}/async`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      const jobId = data.jobId;

      setJobStatus({
        jobId,
        status: 'pending'
      });

      pollIntervalRef.current = setInterval(() => {
        pollJobStatus(jobId);
      }, 2000);

    } catch (err) {
      setError('STT 작업 시작 중 오류가 발생했습니다: ' + (err instanceof Error ? err.message : String(err)));
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'pending': return '#ffc107';
      case 'processing': return '#007bff';
      case 'completed': return '#28a745';
      case 'failed': return '#dc3545';
      default: return '#6c757d';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'pending': return '대기 중';
      case 'processing': return '처리 중';
      case 'completed': return '완료';
      case 'failed': return '실패';
      default: return '알 수 없음';
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
      <div style={{ marginBottom: '20px' }}>
        <Link to="/" style={{ textDecoration: 'none', color: '#007bff' }}>
          ← 메인으로 돌아가기
        </Link>
      </div>

      <h1>STT 비동기 실행</h1>
      <p>음성 파일을 업로드하거나 직접 녹음하여 비동기식으로 텍스트로 변환합니다. 작업이 완료될 때까지 기다릴 필요가 없습니다.</p>

      <div style={{ marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px' }}>
        <h3>녹음하기</h3>
        <div style={{ marginBottom: '20px' }}>
          <button
            onClick={isRecording ? stopRecording : startRecording}
            style={{
              padding: '10px 20px',
              backgroundColor: isRecording ? '#dc3545' : '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '16px'
            }}
          >
            {isRecording ? '녹음 중지' : '녹음 시작'}
          </button>
        </div>
        {isRecording && (
          <div style={{ color: '#dc3545', fontSize: '14px' }}>
            🔴 녹음 중...
          </div>
        )}
      </div>

      <div style={{ marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px' }}>
        <h3>파일 업로드</h3>
        <input
          type="file"
          accept="audio/*"
          onChange={handleFileChange}
          ref={fileInputRef}
          style={{ marginBottom: '20px' }}
        />
        {audioFile && (
          <div style={{ fontSize: '14px', color: '#666' }}>
            선택된 파일: {audioFile.name}
          </div>
        )}
      </div>

      <div style={{ marginBottom: '30px' }}>
        <button
          onClick={handleSttAsync}
          disabled={!audioFile || loading}
          style={{
            padding: '12px 24px',
            backgroundColor: (!audioFile || loading) ? '#6c757d' : '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: (!audioFile || loading) ? 'not-allowed' : 'pointer',
            fontSize: '16px'
          }}
        >
          {loading ? 'STT 작업 진행 중...' : 'STT 비동기 실행'}
        </button>
      </div>

      {error && (
        <div style={{
          padding: '15px',
          backgroundColor: '#f8d7da',
          border: '1px solid #f5c6cb',
          borderRadius: '4px',
          color: '#721c24',
          marginBottom: '20px'
        }}>
          {error}
        </div>
      )}

      {jobStatus && (
        <div style={{
          padding: '20px',
          border: '1px solid #ddd',
          borderRadius: '4px',
          marginBottom: '20px'
        }}>
          <h4 style={{ margin: '0 0 15px 0' }}>작업 상태</h4>
          <div style={{ marginBottom: '10px' }}>
            <strong>작업 ID:</strong> {jobStatus.jobId}
          </div>
          <div style={{ marginBottom: '15px' }}>
            <strong>상태:</strong> 
            <span style={{ 
              color: getStatusColor(jobStatus.status),
              fontWeight: 'bold',
              marginLeft: '8px'
            }}>
              {getStatusText(jobStatus.status)}
            </span>
          </div>

          {jobStatus.status === 'completed' && jobStatus.result && (
            <div style={{
              padding: '15px',
              backgroundColor: '#d4edda',
              border: '1px solid #c3e6cb',
              borderRadius: '4px'
            }}>
              <h5 style={{ margin: '0 0 10px 0', color: '#155724' }}>변환 결과:</h5>
              <p style={{ margin: '0', color: '#155724' }}>{jobStatus.result}</p>
            </div>
          )}

          {jobStatus.status === 'failed' && jobStatus.error && (
            <div style={{
              padding: '15px',
              backgroundColor: '#f8d7da',
              border: '1px solid #f5c6cb',
              borderRadius: '4px'
            }}>
              <h5 style={{ margin: '0 0 10px 0', color: '#721c24' }}>오류:</h5>
              <p style={{ margin: '0', color: '#721c24' }}>{jobStatus.error}</p>
            </div>
          )}

          {(jobStatus.status === 'pending' || jobStatus.status === 'processing') && (
            <div style={{ fontSize: '14px', color: '#666' }}>
              작업이 진행 중입니다. 잠시만 기다려주세요...
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SttAsync;