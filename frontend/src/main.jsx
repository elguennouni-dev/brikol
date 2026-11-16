import { createRoot } from 'react-dom/client';
import './app.css';
import './loader.css';
import './desktop.css';
import './admin.css';
import App from './App';
import { applyLang } from './session';

applyLang();
createRoot(document.getElementById('app')).render(<App />);

// The splash screen (plain HTML in index.html, visible before any JS runs) leaves once the app is up.
const MIN_MS = 1300;
const splash = document.getElementById('splash');
if (splash) {
  const wait = Math.max(0, MIN_MS - performance.now());
  setTimeout(() => {
    splash.classList.add('hide');
    setTimeout(() => splash.remove(), 600);
  }, wait);
}
