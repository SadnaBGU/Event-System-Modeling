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
    if (!err.response) {
      return STATUS_MESSAGES[0];
    }

    const status = err.response.status;

    if (STATUS_MESSAGES[status]) {
      return STATUS_MESSAGES[status];
    }

    if (status >= 500) {
      return STATUS_MESSAGES[500];
    }

    return fallback;
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

const STATUS_MESSAGES: Record<number, string> = {
  0: 'Network problem. Check your connection and try again.',
  400: 'Some details are invalid. Please check your input and try again.',
  401: 'Your session expired. Please sign in again.',
  403: "You don't have permission to do this action.",
  404: 'The requested item could not be found.',
  409: 'This action conflicts with the current state. Refresh and try again.',
  422: 'This action is not allowed right now.',
  500: 'The server had a problem. Please try again shortly.',
};