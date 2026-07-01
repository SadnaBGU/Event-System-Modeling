import axios from 'axios';
import { extractApiMessage } from '../api/client';

/**
 * Turns any thrown error (Axios or otherwise) into a short, clear, user-facing
 * message. Keeps messages concise and informative — no stack traces, no raw
 * "Request failed with status code 4xx" noise.
 *
 * @param err      the caught error
 * @param fallback context-specific fallback, e.g. "Couldn't add the ticket"
 */
export function friendlyError(err: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(err)) {
    const status = err.response?.status;
    // Prefer a backend-provided message when it's short and meaningful.
    const apiMsg = err.response?.data?.message as string | undefined;

    switch (status) {
      case 400:
        return apiMsg ?? 'Some details are invalid. Please check and try again.';
      case 401:
        return 'Your session expired. Please sign in again.';
      case 403:
        return "You don't have permission to do that.";
      case 404:
        return apiMsg ?? 'Not found.';
      case 409:
        return apiMsg ?? 'That action conflicts with the current state.';
      case 422:
        return apiMsg ?? 'That action is not allowed right now.';
      default:
        if (status && status >= 500) return 'The server had a problem. Please try again shortly.';
        if (!err.response) return 'Network problem. Check your connection and try again.';
        return apiMsg ?? fallback;
    }
  }
  return extractApiMessage(err, fallback);
}

export function apiErrorCode(err: unknown): string | undefined {
  if (axios.isAxiosError(err)) {
    const code = err.response?.data?.errorCode;
    return typeof code === 'string' ? code : undefined;
  }

  if (err && typeof err === 'object') {
    const maybeErr = err as { errorCode?: unknown };
    return typeof maybeErr.errorCode === 'string' ? maybeErr.errorCode : undefined;
  }

  return undefined;
}
