"use client";

const KEY = "herald.apiKey";

/**
 * The tenant API key lives in sessionStorage only: it is scoped to the tab and cleared when the
 * tab closes, which limits exposure compared with localStorage.
 */
export const session = {
  get(): string | null {
    return typeof window === "undefined" ? null : window.sessionStorage.getItem(KEY);
  },
  set(apiKey: string) {
    window.sessionStorage.setItem(KEY, apiKey);
  },
  clear() {
    window.sessionStorage.removeItem(KEY);
  },
};
