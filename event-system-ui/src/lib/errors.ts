import axios from 'axios';

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

    switch (status) {
      case 400:
        return 'Some details are invalid. Please check your input and try again.';
      case 401:
        return 'Your session expired. Please sign in again.';
      case 403:
        return "You don't have permission to do this action.";
      case 404:
        return 'The requested item could not be found.';
      case 409:
        return 'This action conflicts with the current state. Refresh and try again.';
      case 422:
        return 'This action is not allowed right now.';
      default:
        if (status && status >= 500) return 'The server had a problem. Please try again shortly.';
        if (!err.response) return 'Network problem. Check your connection and try again.';
        return fallback;
    }
  }
  return fallback;
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
