import { Suspense } from "react";
import { AppShell } from "@/components/herald/app-shell";
import { AuthGate } from "./auth-gate";

/**
 * The console is an authenticated, client-rendered app: data is fetched in the browser with the
 * tenant's API key, so there is no server-rendered content to validate for instant navigation.
 */
export const instant = false;

export default function ConsoleLayout({ children }: LayoutProps<"/">) {
  return (
    <AuthGate>
      <AppShell>
        <Suspense>{children}</Suspense>
      </AppShell>
    </AuthGate>
  );
}
