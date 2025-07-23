import { Link } from 'react-router-dom';

const SttList = () => {
  const sttOptions = [
    {
      id: 'sync',
      title: 'STT 동기 실행',
      path: '/stt/sync',
      description: '동기식으로 음성을 텍스트로 변환합니다.'
    },
    {
      id: 'async',
      title: 'STT 비동기 실행',
      path: '/stt/async',
      description: '비동기식으로 음성을 텍스트로 변환합니다.'
    },
    {
      id: 'streaming',
      title: 'STT 스트리밍',
      path: '/stt/streaming',
      description: '실시간으로 음성을 텍스트로 변환합니다.'
    }
  ];

  return (
    <div style={{padding: '20px', maxWidth: '800px', margin: '0 auto'}}>
      <h1>STT 테스트</h1>
      <p>테스트할 STT 방식을 선택하세요:</p>

      <div style={{display: 'grid', gap: '20px', marginTop: '30px'}}>
        {sttOptions.map((option) => (
          <Link
            key={option.id}
            to={option.path}
            style={{
              textDecoration: 'none',
              color: 'inherit',
              border: '1px solid #ddd',
              borderRadius: '8px',
              padding: '20px',
              transition: 'all 0.2s ease',
              display: 'block'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#007bff';
              e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,123,255,0.2)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#ddd';
              e.currentTarget.style.boxShadow = 'none';
            }}
          >
            <h3 style={{margin: '0 0 10px 0', color: '#333'}}>
              {option.title}
            </h3>
            <p style={{margin: '0', color: '#666', fontSize: '14px'}}>
              {option.description}
            </p>
          </Link>
        ))}
      </div>
    </div>
  );
};

export default SttList;