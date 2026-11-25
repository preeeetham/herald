"use client";

import { useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { SWRConfig } from "swr";
import { ApiError } from "@/lib/api";
import { session } from "@/lib/session";

/**
 * Sends the user to sign-in when there is no key, or when the API rejects it (revoked key,
 * suspended tenant). Children always render so the route segment can be prerendered; without a
 * key their data hooks simply receive 401s, which trigger the redirect.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const router = useRouter();

  useEffect(() => {
    if (!session.get()) router.replace("/login");
  }, [router]);

  return (
    <SWRConfig
      value={{
        shouldRetryOnError: (error) => !(error instanceof ApiError && error.status < 500),
        onError: (error) => {
          if (error instanceof ApiError && error.status === 401) {
            session.clear();
            router.replace("/login?expired=1");
          }
        },
      }}
    >
      {children}
    </SWRConfig>
  );
}
