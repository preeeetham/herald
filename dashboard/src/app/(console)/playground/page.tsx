"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { IconLoader2, IconSend } from "@tabler/icons-react";
import { PageHeader } from "@/components/herald/page-header";
import { CodeBlock } from "@/components/ui/code-block";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { ApiError, request } from "@/lib/api";

const TEMPLATES: Record<string, object> = {
  "claim.created": {
    claimNumber: "CLM-204811",
    policyNumber: "POL-55-1932",
    lossDate: "2026-11-24",
    lossType: "collision",
    insured: { name: "Priya Raman", state: "KA" },
    reserve: { value: 85000, currency: "INR" },
  },
  "policy.bound": {
    policyNumber: "POL-55-1932",
    product: "personal_auto",
    effectiveDate: "2026-12-01",
    premium: { value: 18450, currency: "INR" },
  },
  "payment.scheduled": {
    paymentId: "PAY-88123",
    claimNumber: "CLM-204811",
    payee: "Sri Sai Motors",
    amount: { value: 42000, currency: "INR" },
    dueDate: "2026-11-30",
  },
};

interface Result {
  status: number;
  body: unknown;
  headers: Record<string, string>;
}

export default function PlaygroundPage() {
  const [type, setType] = useState("claim.created");
  const [payload, setPayload] = useState(JSON.stringify(TEMPLATES["claim.created"], null, 2));
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  const curl = useMemo(
    () =>
      [
        "curl -X POST https://herald.example.com/v1/events \\",
        '  -H "Authorization: Bearer $HERALD_API_KEY" \\',
        '  -H "Content-Type: application/json" \\',
        ...(idempotencyKey ? [`  -H "Idempotency-Key: ${idempotencyKey}" \\`] : []),
        `  -d '${JSON.stringify({ type, payload: safeParse(payload) }, null, 2)}'`,
      ].join("\n"),
    [type, payload, idempotencyKey],
  );

  async function send() {
    let parsed: unknown;
    try {
      parsed = JSON.parse(payload);
    } catch {
      toast.error("Payload is not valid JSON");
      return;
    }
    setPending(true);
    try {
      const response = await request<unknown>("/v1/events", {
        method: "POST",
        body: { type, payload: parsed },
        headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
      });
      setResult({ status: response.status, body: response.data, headers: pick(response.headers) });
    } catch (e) {
      if (e instanceof ApiError) setResult({ status: e.status, body: e.problem, headers: {} });
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <PageHeader title="Playground" description="Publish a test event and watch it fan out to your endpoints." />
      <div className="grid gap-6 xl:grid-cols-2 [&>*]:min-w-0">
        <Card>
          <CardHeader>
            <CardTitle>Event</CardTitle>
            <CardDescription>Sent exactly as your core system would send it.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Template</Label>
                <Select
                  value={type in TEMPLATES ? type : undefined}
                  onValueChange={(t) => {
                    setType(t);
                    setPayload(JSON.stringify(TEMPLATES[t], null, 2));
                  }}
                >
                  <SelectTrigger className="w-full"><SelectValue placeholder="Custom" /></SelectTrigger>
                  <SelectContent>
                    {Object.keys(TEMPLATES).map((t) => (
                      <SelectItem key={t} value={t} className="font-mono">{t}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="type">Type</Label>
                <Input id="type" value={type} onChange={(e) => setType(e.target.value)} className="font-mono" />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="payload">Payload</Label>
              <Textarea id="payload" value={payload} onChange={(e) => setPayload(e.target.value)} rows={14} className="font-mono text-xs" spellCheck={false} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="idem">Idempotency-Key (optional)</Label>
              <Input id="idem" value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} placeholder="claim-204811-created" className="font-mono" />
              <p className="text-xs text-muted-foreground">Send twice with the same key: the second call returns the original event (200).</p>
            </div>
            <Button onClick={send} disabled={pending} className="w-full">
              {pending ? <IconLoader2 className="size-4 animate-spin" /> : <IconSend className="size-4" />}
              Publish event
            </Button>
          </CardContent>
        </Card>
        <div className="space-y-6">
          <CodeBlock language="bash" filename="equivalent request" code={curl} />
          {result && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-3">
                  Response
                  <span className={result.status < 300 ? "font-mono text-success" : "font-mono text-destructive"}>{result.status}</span>
                </CardTitle>
                {Object.keys(result.headers).length > 0 && (
                  <CardDescription className="font-mono text-xs">
                    {Object.entries(result.headers).map(([k, v]) => `${k}: ${v}`).join("  ·  ")}
                  </CardDescription>
                )}
              </CardHeader>
              <CardContent className="space-y-3">
                <pre className="overflow-auto rounded-md bg-background p-3 font-mono text-xs">{JSON.stringify(result.body, null, 2)}</pre>
                {isEvent(result.body) && (
                  <Button asChild variant="outline">
                    <Link href={`/events/${result.body.id}`}>Follow its deliveries →</Link>
                  </Button>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function safeParse(text: string) {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function pick(headers: Headers) {
  const out: Record<string, string> = {};
  for (const name of ["ratelimit-remaining", "ratelimit-limit", "idempotent-replayed", "retry-after"]) {
    const value = headers.get(name);
    if (value) out[name] = value;
  }
  return out;
}

function isEvent(body: unknown): body is { id: string } {
  return typeof body === "object" && body !== null && "id" in body && "deliveries" in body;
}
