import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App.tsx';
import '@fontsource-variable/dm-sans';
import '@fontsource-variable/outfit';
import './styles/index.css';
import { ThemeProvider } from './shared/theme/ThemeProvider.tsx';
import { FeedbackProvider } from './shared/ui';

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('Root element #root not found');

ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <ThemeProvider>
      <FeedbackProvider>
        <App />
      </FeedbackProvider>
    </ThemeProvider>
  </React.StrictMode>
);
