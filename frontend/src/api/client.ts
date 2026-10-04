import axios from "axios";

export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
});

/**
 * Request cancellation is an expected part of React effect cleanup. It must not
 * be presented as an API failure when Strict Mode remounts a page or the user
 * navigates away while a request is still in flight.
 */
export function isApiRequestCanceled(error: unknown, signal?: AbortSignal): boolean {
  return Boolean(signal?.aborted)
    || axios.isCancel(error)
    || (error instanceof DOMException && error.name === "AbortError");
}
