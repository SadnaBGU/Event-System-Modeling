import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { router } from './routes/router';
import './App.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchOnWindowFocus: false, retry: 1, staleTime: 30_000 },
  },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      {/* NotificationProvider will wrap here once Student C builds it */}
      <RouterProvider router={router} />
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  );
}

export default App;
