import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

const SttStreaming = () => {
  const [isStreaming, setIsStreaming] = useState(false);
  const [transcription, setTranscription] = useState<string>('');
  const [error, setError] = useState<string>('');
  const [connectionStatus, setConnectionStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');

  const wsRef = useRef<WebSocket | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    return () => {
      stopStreaming();
    };
  }, []);

  const connectWebSocket = () => {
    try {
      setConnectionStatus('connecting');
      const wsUrl = `${import.meta.env.VITE_WS_BASE_URL}/api/stt/streaming`;
      wsRef.current = new WebSocket(wsUrl);

      wsRef.current.onopen = () => {
        setConnectionStatus('connected');
        setError('');
      };

      wsRef.current.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.type === 'transcription') {
          if (data.isFinal) {
            setTranscription(prev => prev + data.text + ' ');
          } else {
            setTranscription(prev => {
              const sentences = prev.split(' ');
              sentences[sentences.length - 1] = data.text;
              return sentences.join(' ');
            });
          }
        } else if (data.type === 'error') {
          setError(data.message);
        }
      };

      wsRef.current.onerror = () => {
        setError('WebSocket 연결 오류가 발생했습니다.');
        setConnectionStatus('disconnected');
      };

      wsRef.current.onclose = () => {
        setConnectionStatus('disconnected');
      };

    } catch (err) {
      setError('WebSocket 연결 실패: ' + (err instanceof Error ? err.message : String(err)));
      setConnectionStatus('disconnected');
    }
  };

  const startStreaming = async () => {
    try {
      if (connectionStatus !== 'connected') {
        connectWebSocket();
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 16000,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true
        }
      });

      streamRef.current = stream;

      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: 'audio/webm;codecs=opus'
      });

      mediaRecorderRef.current = mediaRecorder;

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0 && wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
          wsRef.current.send(event.data);
        }
      };

      mediaRecorder.start(250);
      setIsStreaming(true);
      setError('');
      setTranscription('');

    } catch (err) {
      setError('마이크 접근 권한이 필요합니다: ' + (err instanceof Error ? err.message : String(err)));
    }
  };

  const stopStreaming = () => {
    if (mediaRecorderRef.current && isStreaming) {
      mediaRecorderRef.current.stop();
      setIsStreaming(false);
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }

    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    setConnectionStatus('disconnected');
  };

  const clearTranscription = () => {
    setTranscription('');
  };

  const getConnectionStatusColor = () => {
    switch (connectionStatus) {
      case 'connected':
        return '#28a745';
      case 'connecting':
        return '#ffc107';
      case 'disconnected':
        return '#dc3545';
      default:
        return '#6c757d';
    }
  };

  const getConnectionStatusText = () => {
    switch (connectionStatus) {
      case 'connected':
        return '연결됨';
      case 'connecting':
        return '연결 중...';
      case 'disconnected':
        return '연결 끊김';
      default:
        return '알 수 없음';
    }
  };

  return (
    <div style={{padding: '20px', maxWidth: '800px', margin: '0 auto'}}>
      <div style={{marginBottom: '20px'}}>
        <Link to="/" style={{textDecoration: 'none', color: '#007bff'}}>
          ← 메인으로 돌아가기
        </Link>
      </div>

      <h1>STT 스트리밍</h1>
      <p>실시간으로 음성을 텍스트로 변환합니다. WebSocket을 통해 연속적인 음성 인식이 가능합니다.</p>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>연결 상태</h3>
        <div style={{marginBottom: '20px'}}>
          <span style={{fontWeight: 'bold'}}>WebSocket 상태: </span>
          <span style={{
            color: getConnectionStatusColor(),
            fontWeight: 'bold'
          }}>
            {getConnectionStatusText()}
          </span>
        </div>

        {connectionStatus !== 'connected' && (
          <button
            onClick={connectWebSocket}
            disabled={connectionStatus === 'connecting'}
            style={{
              padding: '10px 20px',
              backgroundColor: connectionStatus === 'connecting' ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: connectionStatus === 'connecting' ? 'not-allowed' : 'pointer',
              fontSize: '16px'
            }}
          >
            {connectionStatus === 'connecting' ? '연결 중...' : 'WebSocket 연결'}
          </button>
        )}
      </div>

      <div style={{marginBottom: '30px', padding: '20px', border: '1px solid #ddd', borderRadius: '8px'}}>
        <h3>스트리밍 제어</h3>
        <div style={{marginBottom: '20px', display: 'flex', gap: '10px'}}>
          <button
            onClick={isStreaming ? stopStreaming : startStreaming}
            disabled={connectionStatus !== 'connected'}
            style={{
              padding: '12px 24px',
              backgroundColor: connectionStatus !== 'connected' ? '#6c757d' : (isStreaming ? '#dc3545' : '#28a745'),
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: connectionStatus !== 'connected' ? 'not-allowed' : 'pointer',
              fontSize: '16px'
            }}
          >
            {isStreaming ? '스트리밍 중지' : '스트리밍 시작'}
          </button>

          <button
            onClick={clearTranscription}
            style={{
              padding: '12px 24px',
              backgroundColor: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '16px'
            }}
          >
            텍스트 지우기
          </button>
        </div>

        {isStreaming && (
          <div style={{
            color: '#dc3545',
            fontSize: '14px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <span style={{
              width: '10px',
              height: '10px',
              backgroundColor: '#dc3545',
              borderRadius: '50%',
              animation: 'blink 1s infinite'
            }}></span>
            실시간 음성 인식 중...
          </div>
        )}
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

      <div style={{
        padding: '20px',
        border: '1px solid #ddd',
        borderRadius: '4px',
        backgroundColor: '#f8f9fa',
        minHeight: '200px'
      }}>
        <h4 style={{margin: '0 0 15px 0'}}>실시간 변환 결과:</h4>
        <div style={{
          padding: '15px',
          backgroundColor: 'white',
          border: '1px solid #e9ecef',
          borderRadius: '4px',
          minHeight: '150px',
          fontSize: '16px',
          lineHeight: '1.5',
          whiteSpace: 'pre-wrap',
          fontFamily: 'monospace'
        }}>
          {transcription || '음성 인식 결과가 여기에 표시됩니다...'}
        </div>
      </div>

      <style>{`
        @keyframes blink {
          0%, 50% { opacity: 1; }
          51%, 100% { opacity: 0; }
        }
      `}</style>
    </div>
  );
};

export default SttStreaming;