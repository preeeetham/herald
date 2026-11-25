"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { IconArrowRight, IconLoader2 } from "@tabler/icons-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError, request } from "@/lib/api";
import { session } from "@/lib/session";

export function LoginForm() {
  const router = useRouter();
  const expired = useSearchParams().get("expired");
  const [key, setKey] = useState("");
  const [error, setError] = useState<string | null>(expired ? "Your key was revoked or the tenant was suspended." : null);
  const [pending, setPending] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await request("/v1/me", { apiKey: key.trim() });
      session.set(key.trim());
      router.replace("/overview");
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? "That key is not valid." : "Could not reach the Herald API.");
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} className="mt-6 space-y-4">
      <div className="space-y-2">
        <Label htmlFor="api-key">API key</Label>
        <Input
          id="api-key"
          type="password"
          autoComplete="off"
          spellCheck={false}
          placeholder="hk_xxxxxxxx_…"
          className="font-mono"
          value={key}
          onChange={(e) => setKey(e.target.value)}
          required
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <Button type="submit" className="w-full" disabled={pending || key.length < 10}>
        {pending ? <IconLoader2 className="size-4 animate-spin" /> : <IconArrowRight className="size-4" />}
        Continue
      </Button>
    </form>
  );
}
