import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { SttResponse } from "./type/SttTypes.ts";


const SttSync = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [response, setResponse] = useState<SttResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);

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
      setResponse(null);
      setError('');
    }
  };

  const handleSttSync = async () => {
    if (!audioFile) {
      setError('오디오 파일을 선택하거나 녹음해주세요.');
      return;
    }

    setLoading(true);
    setError('');
    setResponse(null);

    try {
      const formData = new FormData();
      formData.append('file', audioFile);
      formData.append('enableAutomaticPunctuation', 'true');
      formData.append('enableWordTimeOffsets', 'false');

      const apiResponse = await fetch(`${import.meta.env.VITE_WS_BASE_URL}/speech`, {
        method: 'POST',
        body: formData,
      });

      if (!apiResponse.ok) {
        throw new Error(`HTTP error! status: ${apiResponse.status}`);
      }

      const data: SttResponse = await apiResponse.json();

      if (!data.success) {
        setError(data.errorMessage || 'STT 처리에 실패했습니다.');
      } else {
        setResponse(data);
      }
    } catch (err) {
      setError('STT 처리 중 오류가 발생했습니다: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{padding: '20px', maxWidth: '800px', margin: '0 auto'}}>
      <div style={{marginBottom: '20px'}}>
        <Link to="/" style={{textDecoration: 'none', color: '#007bff'}}>
          ← 메인으로 돌아가기
        </Link>
      </div>

      <h1>STT 동기 실행</h1>
      <p>음성 파일을 업로드하거나 직접 녹음하여 동기식으로 텍스트로 변환합니다.</p>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>녹음하기</h3>
        <div style={{marginBottom: '20px'}}>
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
          <div style={{color: '#dc3545', fontSize: '14px'}}>
            🔴 녹음 중...
          </div>
        )}
      </div>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>파일 업로드</h3>
        <input
          type="file"
          accept="audio/*"
          onChange={handleFileChange}
          ref={fileInputRef}
          style={{marginBottom: '20px'}}
        />
        {audioFile && (
          <div style={{fontSize: '14px', color: '#666'}}>
            선택된 파일: {audioFile.name}
          </div>
        )}
      </div>

      <div style={{marginBottom: '30px'}}>
        <button
          onClick={handleSttSync}
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
          {loading ? 'STT 처리 중...' : 'STT 동기 실행'}
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

      {response && (
        <div style={{
          padding: '20px',
          backgroundColor: '#d4edda',
          border: '1px solid #c3e6cb',
          borderRadius: '4px',
          marginBottom: '20px'
        }}>
          <h4 style={{margin: '0 0 15px 0', color: '#155724'}}>변환 결과</h4>

          <div style={{marginBottom: '15px'}}>
            <strong style={{color: '#155724'}}>변환된 텍스트:</strong>
            <p style={{margin: '5px 0', color: '#155724', fontSize: '16px', lineHeight: '1.5'}}>
              {response.transcribedText}
            </p>
          </div>

          <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '15px', fontSize: '14px'}}>
            <div>
              <strong style={{color: '#155724'}}>원본 파일명:</strong>
              <p style={{margin: '2px 0', color: '#155724'}}>{response.originalFilename}</p>
            </div>
            <div>
              <strong style={{color: '#155724'}}>신뢰도 점수:</strong>
              <p style={{margin: '2px 0', color: '#155724'}}>
                {response.confidenceScore ? (response.confidenceScore * 100).toFixed(1) + '%' : 'N/A'}
              </p>
            </div>
            <div>
              <strong style={{color: '#155724'}}>처리 시간:</strong>
              <p style={{margin: '2px 0', color: '#155724'}}>{response.processingTimeMs}ms</p>
            </div>
            <div>
              <strong style={{color: '#155724'}}>파일 크기:</strong>
              <p style={{margin: '2px 0', color: '#155724'}}>
                {(response.fileSize / 1024).toFixed(1)}KB
              </p>
            </div>
            {response.encoding && (
              <div>
                <strong style={{color: '#155724'}}>인코딩:</strong>
                <p style={{margin: '2px 0', color: '#155724'}}>{response.encoding}</p>
              </div>
            )}
            {response.resultCount && (
              <div>
                <strong style={{color: '#155724'}}>결과 수:</strong>
                <p style={{margin: '2px 0', color: '#155724'}}>{response.resultCount}</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default SttSync;