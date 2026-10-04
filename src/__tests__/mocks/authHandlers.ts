import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

export const mockProvider = {
  id: 'PROV-001',
  name: 'Dr. A. Mehta',
  email: 'a.mehta@medisphere.dev',
  role: 'CLINICIAN' as const,
  specialty: 'Cardiology',
  facility: 'Hospital A',
  active: true,
  notificationPrefs: { critical: true, risk: true, approvals: true, system: false },
};

export const mockAccessToken = 'mock.jwt.access.token';

export const authHandlers = [
  // Successful login
  http.post(`${BASE}/api/auth/login`, async ({ request }) => {
    const body = await request.json() as { email?: string; password?: string };
    if (body.email === 'a.mehta@medisphere.dev' && body.password === 'Medisphere@123') {
      return HttpResponse.json({
        accessToken: mockAccessToken,
        provider: mockProvider,
      });
    }
    return HttpResponse.json({ message: 'Invalid credentials' }, { status: 401 });
  }),

  // Logout
  http.post(`${BASE}/api/auth/logout`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Refresh — no cookie → 401
  http.post(`${BASE}/api/auth/refresh`, () => {
    return HttpResponse.json({ message: 'No refresh token' }, { status: 401 });
  }),

  // Get me
  http.get(`${BASE}/api/auth/me`, () => {
    return HttpResponse.json(mockProvider);
  }),
];
