import { useEffect } from 'react';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { toast, Toaster } from 'sonner';
import { router } from './routes/router';
import { useAuthStore } from './auth/authStore';
import { AUTH_EXPIRED_EVENT } from './api/client';
import { NotificationProvider } from './notifications/NotificationProvider';
import './App.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
    },
  },
});

function App() {
  const hydrate = useAuthStore((s) => s.hydrateFromStorage);
  const clear = useAuthStore((s) => s.clear);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    const handler = () => clear();
    window.addEventListener(AUTH_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handler);
  }, [clear]);

  useEffect(() => {
    const handleOffline = () => {
      toast.error('Internet connection lost. Please check your network.');
    };

    const handleOnline = () => {
      toast.success('Internet connection restored.');
    };

    window.addEventListener('offline', handleOffline);
    window.addEventListener('online', handleOnline);

    return () => {
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('online', handleOnline);
    };
  }, []);
  
  return (
    <QueryClientProvider client={queryClient}>
      <NotificationProvider>
        <RouterProvider router={router} />
        <Toaster richColors position="top-right" closeButton />
      </NotificationProvider>
    </QueryClientProvider>
  );
}

export default App;
