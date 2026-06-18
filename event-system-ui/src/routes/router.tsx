import { createBrowserRouter } from 'react-router-dom';
import { Layout } from '../components/Layout';
import { HomePage } from '../features/home/HomePage';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { EventDetailPage } from '../features/catalog/EventDetailPage';
import { QueuePage } from '../features/queue/QueuePage';
import { OrderPage } from '../features/orders/OrderPage';
import { HistoryPage } from '../features/history/HistoryPage';
import { ReceiptDetailPage } from '../features/history/ReceiptDetailPage';
import { NotificationsPage } from '../features/notifications/NotificationsPage';
import { CompaniesListPage } from '../features/companies/CompaniesListPage';
import { CompanyDetailPage } from '../features/companies/CompanyDetailPage';
import { RolesPage } from '../features/companies/RolesPage';
import { CreateEventPage } from '../features/events-mgmt/CreateEventPage';
import { EditEventPage } from '../features/events-mgmt/EditEventPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import { PolicyEditorPage } from '../features/policies/PolicyEditorPage';
import { AdminDashboardPage } from '../features/admin/AdminDashboardPage';
import { SuspensionsPage } from '../features/admin/SuspensionsPage';
import { GlobalHistoryPage } from '../features/admin/GlobalHistoryPage';
import { BanMemberPage } from '../features/admin/BanMemberPage';
import { CloseCompanyPage } from '../features/admin/CloseCompanyPage';
import { Placeholder } from '../components/Placeholder';
import { RequireAuth } from '../auth/RequireAuth';

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },

      { path: '/events', element: <CatalogPage /> },
      { path: '/events/:eventId', element: <EventDetailPage /> },
      { path: '/events/:eventId/queue', element: <QueuePage /> },
      { path: '/events/:eventId/edit', element: <RequireAuth><EditEventPage /></RequireAuth> },
      { path: '/orders/:orderId', element: <OrderPage /> },
      {
        path: '/events/:eventId/policies',
        element: (
          <RequireAuth roles={['COMPANY_OWNER', 'COMPANY_MANAGER']}>
            <PolicyEditorPage scope="event" />
          </RequireAuth>
        ),
      },
      { path: '/history', element: <RequireAuth><HistoryPage /></RequireAuth> },
      { path: '/history/:recordId', element: <RequireAuth><ReceiptDetailPage /></RequireAuth> },
      { path: '/notifications', element: <RequireAuth><NotificationsPage /></RequireAuth> },
      { path: '/profile', element: <RequireAuth><ProfilePage /></RequireAuth> },

      {
        path: '/companies',
        // Any signed-in member can view the companies page; creating a company is the
        // path to becoming an owner, so we cannot pre-require the owner role here.
        element: (
          <RequireAuth>
            <CompaniesListPage />
          </RequireAuth>
        ),
      },
      {
        path: '/companies/:companyId',
        element: (
          <RequireAuth>
            <CompanyDetailPage />
          </RequireAuth>
        ),
      },
      {
        path: '/companies/:companyId/roles',
        element: (
          <RequireAuth>
            <RolesPage />
          </RequireAuth>
        ),
      },
      {
        path: '/companies/:companyId/policies',
        element: (
          <RequireAuth>
            <PolicyEditorPage scope="company" />
          </RequireAuth>
        ),
      },
      {
        path: '/companies/:companyId/events/new',
        element: (
          <RequireAuth>
            <CreateEventPage />
          </RequireAuth>
        ),
      },

      {
        path: '/admin',
        element: <RequireAuth roles={['ADMIN']}><AdminDashboardPage /></RequireAuth>,
      },
      {
        path: '/admin/suspensions',
        element: <RequireAuth roles={['ADMIN']}><SuspensionsPage /></RequireAuth>,
      },
      {
        path: '/admin/members',
        element: <RequireAuth roles={['ADMIN']}><BanMemberPage /></RequireAuth>,
      },
      {
        path: '/admin/companies',
        element: <RequireAuth roles={['ADMIN']}><CloseCompanyPage /></RequireAuth>,
      },
      {
        path: '/admin/history',
        element: <RequireAuth roles={['ADMIN']}><GlobalHistoryPage /></RequireAuth>,
      },

      { path: '*', element: <Placeholder title="Not found" /> },
    ],
  },
]);
