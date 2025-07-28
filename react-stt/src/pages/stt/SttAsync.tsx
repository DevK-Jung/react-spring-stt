import React, { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { StreamingResult, TranscriptData } from './type/SttTypes';

const SttAsync = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string>('');
  const [streamingResults, setStreamingResults] = useState<StreamingResult[]>([]);
  const [currentTranscript, setCurrentTranscript] = useState<string>('');
  const [finalTranscript, setFinalTranscript] = useState<string>('');
  const [enableAutomaticPunctuation, setEnableAutomaticPunctuation] = useState(true);
  const [enableWordTimeOffsets, setEnableWordTimeOffsets] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const eventSourceRef = useRef<EventSource | null>(null);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({audio: true});
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        audioChunksRef.current.push(event.data);
      };

      mediaRecorder.onstop = () => {
        const audioBlob = new Blob(audioChunksRef.current, {type: 'audio/wav'});
        const audioFile = new File([audioBlob], 'recording.wav', {type: 'audio/wav'});
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
      setError('');
      resetResults();
    }
  };

  const resetResults = () => {
    setStreamingResults([]);
    setCurrentTranscript('');
    setFinalTranscript('');
  };

  const handleStreamingSTT = async () => {
    if (!audioFile) {
      setError('음성 파일을 선택해주세요.');
      return;
    }

    setIsStreaming(true);
    setError('');
    resetResults();

    try {
      const formData = new FormData();
      formData.append('audioFile', audioFile);
      formData.append('enableAutomaticPunctuation', enableAutomaticPunctuation.toString());
      formData.append('enableWordTimeOffsets', enableWordTimeOffsets.toString());

      // const eventSource = new EventSource(`http://localhost:8099/speech/streaming?${new URLSearchParams({
      //   enableAutomaticPunctuation: enableAutomaticPunctuation.toString(),
      //   enableWordTimeOffsets: enableWordTimeOffsets.toString()
      // })}`);

      const eventSource = new EventSource(`http://localhost:8099/speech/streaming`);

      eventSourceRef.current = eventSource;

      eventSource.addEventListener('message', (event) => {
        try {
          const data: TranscriptData = JSON.parse(event.data);

          const newResult: StreamingResult = {
            transcript: data.transcript,
            confidence: data.confidence,
            isFinal: data.isFinal,
            timestamp: new Date().toLocaleTimeString()
          };

          if (data.isFinal) {
            setFinalTranscript(prev => prev + ' ' + data.transcript);
            setStreamingResults(prev => [...prev, newResult]);
            setCurrentTranscript('');
          } else {
            setCurrentTranscript(data.transcript);
          }

        } catch (err) {
          console.error('SSE 데이터 파싱 오류:', err);
        }
      });

      eventSource.addEventListener('error', (event: any) => {
        try {
          const errorData = JSON.parse(event.data);
          setError('음성 인식 오류: ' + errorData.error);
        } catch (err) {
          setError('스트리밍 연결 오류가 발생했습니다.');
        }
        setIsStreaming(false);
      });

      eventSource.addEventListener('completed', () => {
        console.log('음성 인식 완료');
        setIsStreaming(false);
        eventSource.close();
      });

      eventSource.onerror = (event) => {
        console.error('SSE 연결 오류:', event);
        setError('서버 연결에 실패했습니다.');
        setIsStreaming(false);
        eventSource.close();
      };

      // 파일 업로드를 위한 별도 요청
      await fetch('http://localhost:8080/api/stt/upload', {
        method: 'POST',
        body: formData
      });

    } catch (err) {
      setError('STT 스트리밍 시작 중 오류가 발생했습니다: ' + (err instanceof Error ? err.message : String(err)));
      setIsStreaming(false);
    }
  };

  const stopStreaming = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setIsStreaming(false);
  };

  const clearResults = () => {
    resetResults();
    setError('');
  };

// 컴포넌트 언마운트 시 정리
  React.useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  return (
    <div style={{padding: '20px', maxWidth: '800px', margin: '0 auto'}}>
      <div style={{marginBottom: '20px'}}>
        <Link to="/" style={{textDecoration: 'none', color: '#007bff'}}>
          ← 메인으로 돌아가기
        </Link>
      </div>

      <h1>STT 비동기 처리</h1>
      <p>음성 파일을 업로드하거나 직접 녹음하여 비동기로 텍스트 변환을 수행할 수 있습니다.</p>

      {/* 설정 옵션 */}
      <div style={{
        marginBottom: '30px',
        padding: '20px',
        border: '1px solid #ddd',
        borderRadius: '8px',
        backgroundColor: '#f8f9fa'
      }}>
        <h3>인식 설정</h3>
        <div style={{marginBottom: '15px'}}>
          <label style={{display: 'flex', alignItems: 'center', cursor: 'pointer'}}>
            <input
              type="checkbox"
              checked={enableAutomaticPunctuation}
              onChange={(e) => setEnableAutomaticPunctuation(e.target.checked)}
              style={{marginRight: '8px'}}
            />
            자동 구두점 추가
          </label>
        </div>
        <div>
          <label style={{display: 'flex', alignItems: 'center', cursor: 'pointer'}}>
            <input
              type="checkbox"
              checked={enableWordTimeOffsets}
              onChange={(e) => setEnableWordTimeOffsets(e.target.checked)}
              style={{marginRight: '8px'}}
            />
            단어별 타임스탬프 포함
          </label>
        </div>
      </div>

      {/* 녹음 섹션 */}
      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>녹음하기</h3>
        <div style={{marginBottom: '20px'}}>
          <button
            onClick={isRecording ? stopRecording : startRecording}
            disabled={isStreaming}
            style={{
              padding: '10px 20px',
              backgroundColor: isRecording ? '#dc3545' : '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: isStreaming ? 'not-allowed' : 'pointer',
              fontSize: '16px',
              opacity: isStreaming ? 0.5 : 1
            }}
          >
            {isRecording ? '녹음 중지' : '녹음 시작'}
          </button>
        </div>
        {isRecording && (
          <div style={{color: '#dc3545', fontSize: '14px'}}>
            🔴 녹음 중...
          </div>
        )}
      </div>

      {/* 파일 업로드 섹션 */}
      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>파일 업로드</h3>
        <input
          type="file"
          accept="audio/*"
          onChange={handleFileChange}
          ref={fileInputRef}
          disabled={isStreaming}
          style={{marginBottom: '20px'}}
        />
        {audioFile && (
          <div style={{fontSize: '14px', color: '#666'}}>
            선택된 파일: {audioFile.name} ({(audioFile.size / 1024 / 1024).toFixed(2)}MB)
          </div>
        )}
      </div>

      {/* 스트리밍 제어 버튼들 */}
      <div style={{marginBottom: '30px', display: 'flex', gap: '10px'}}>
        <button
          onClick={handleStreamingSTT}
          disabled={!audioFile || isStreaming}
          style={{
            padding: '12px 24px',
            backgroundColor: (!audioFile || isStreaming) ? '#6c757d' : '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: (!audioFile || isStreaming) ? 'not-allowed' : 'pointer',
            fontSize: '16px'
          }}
        >
          {isStreaming ? '🔄 스트리밍 중...' : '🎤 STT 스트리밍 시작'}
        </button>

        {isStreaming && (
          <button
            onClick={stopStreaming}
            style={{
              padding: '12px 24px',
              backgroundColor: '#dc3545',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '16px'
            }}
          >
            ⏹️ 중지
          </button>
        )}

        <button
          onClick={clearResults}
          disabled={isStreaming}
          style={{
            padding: '12px 24px',
            backgroundColor: '#6c757d',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: isStreaming ? 'not-allowed' : 'pointer',
            fontSize: '16px'
          }}
        >
          🗑️ 결과 지우기
        </button>
      </div>

      {/* 오류 메시지 */}
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

      {/* 실시간 결과 표시 */}
      {(isStreaming || streamingResults.length > 0 || currentTranscript || finalTranscript) && (
        <div style={{
          padding: '20px',
          border: '2px solid #007bff',
          borderRadius: '8px',
          backgroundColor: '#f8f9fa',
          marginBottom: '20px'
        }}>
          <h4 style={{margin: '0 0 15px 0', color: '#007bff'}}>
            🎤 실시간 음성 인식 결과
            {isStreaming && <span style={{color: '#28a745', marginLeft: '10px'}}>● LIVE</span>}
          </h4>

          {/* 현재 인식 중인 텍스트 (임시) */}
          {currentTranscript && (
            <div style={{
              padding: '10px',
              backgroundColor: '#fff3cd',
              border: '1px solid #ffeaa7',
              borderRadius: '4px',
              marginBottom: '15px',
              fontSize: '16px',
              fontStyle: 'italic',
              color: '#856404'
            }}>
              <strong>인식 중:</strong> {currentTranscript}
            </div>
          )}

          {/* 최종 확정된 텍스트 */}
          {finalTranscript && (
            <div style={{
              padding: '15px',
              backgroundColor: '#d4edda',
              border: '1px solid #c3e6cb',
              borderRadius: '4px',
              marginBottom: '15px'
            }}>
              <h5 style={{margin: '0 0 10px 0', color: '#155724'}}>최종 변환 결과:</h5>
              <p style={{margin: '0', fontSize: '16px', lineHeight: '1.5', color: '#155724'}}>
                {finalTranscript.trim()}
              </p>
            </div>
          )}

          {/* 스트리밍 진행 상황 */}
          {streamingResults.length > 0 && (
            <div>
              <h5 style={{margin: '0 0 10px 0'}}>인식 과정:</h5>
              <div style={{maxHeight: '300px', overflowY: 'auto', border: '1px solid #dee2e6', borderRadius: '4px'}}>
                {streamingResults.map((result, index) => (
                  <div
                    key={index}
                    style={{
                      padding: '8px 12px',
                      borderBottom: index < streamingResults.length - 1 ? '1px solid #dee2e6' : 'none',
                      backgroundColor: result.isFinal ? '#e7f3ff' : '#f8f9fa'
                    }}
                  >
                    <div style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '4px'
                    }}>
                      <span style={{fontSize: '12px', color: '#666'}}>
                        {result.timestamp}
                      </span>
                      <div style={{display: 'flex', gap: '8px'}}>
                        <span style={{
                          fontSize: '11px',
                          padding: '2px 6px',
                          borderRadius: '3px',
                          backgroundColor: result.isFinal ? '#28a745' : '#ffc107',
                          color: result.isFinal ? 'white' : '#212529'
                        }}>
                          {result.isFinal ? 'FINAL' : 'INTERIM'}
                        </span>
                        <span style={{
                          fontSize: '11px',
                          padding: '2px 6px',
                          borderRadius: '3px',
                          backgroundColor: '#6c757d',
                          color: 'white'
                        }}>
                          신뢰도: {(result.confidence * 100).toFixed(1)}%
                        </span>
                      </div>
                    </div>
                    <div style={{fontSize: '14px'}}>
                      {result.transcript}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {isStreaming && streamingResults.length === 0 && !currentTranscript && (
            <div style={{fontSize: '14px', color: '#666', textAlign: 'center', padding: '20px'}}>
              🎵 음성 분석 중... 잠시만 기다려주세요.
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default SttAsync;