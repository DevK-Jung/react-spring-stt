import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import './App.css';
import SttList from './pages/stt/SttList.tsx';
import SttSync from './pages/stt/SttSync.tsx';
import SttAsync from './pages/stt/SttAsync.tsx';
import SttStreaming from './pages/stt/SttStreaming.tsx';

function App() {
  return (
    <Router>
      <div className="App">
        <Routes>
          <Route path="/" element={<SttList/>}/>
          <Route path="/stt/sync" element={<SttSync/>}/>
          <Route path="/stt/async" element={<SttAsync/>}/>
          <Route path="/stt/streaming" element={<SttStreaming/>}/>
        </Routes>
      </div>
    </Router>
  );
}

export default App;
