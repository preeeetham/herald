"use client";

import { useState } from "react";
import { toast } from "sonner";
import { IconPlus } from "@tabler/icons-react";
import { PageHeader } from "@/components/herald/page-header";
import { SecretDialog } from "@/components/herald/secret-dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiError, api, useApi } from "@/lib/api";
import { session } from "@/lib/session";
import { timeAgo } from "@/lib/format";
import type { ApiKeySummary, IssuedApiKey } from "@/lib/types";

export default function ApiKeysPage() {
  const { data: keys, mutate } = useApi<ApiKeySummary[]>("/v1/api-keys");
  const [name, setName] = useState("");
  const [issued, setIssued] = useState<string | null>(null);
  const currentPrefix = session.get()?.split("_")[1];

  async function create(event: React.FormEvent) {
    event.preventDefault();
    try {
      const { data } = await api.post<IssuedApiKey>("/v1/api-keys", { name });
      setIssued(data.key);
      setName("");
      await mutate();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Could not create key");
    }
  }

  async function revoke(key: ApiKeySummary) {
    if (!confirm(`Revoke "${key.name}"? Systems using it will start receiving 401.`)) return;
    await api.delete(`/v1/api-keys/${key.id}`);
    toast.success("Key revoked");
    await mutate();
  }

  return (
    <>
      <PageHeader title="API keys" description="Keys authenticate your systems. Only a SHA-256 hash is stored; the full key is shown once." />
      <form onSubmit={create} className="flex max-w-md gap-2">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Key name, e.g. policy-admin-prod" required />
        <Button type="submit"><IconPlus className="size-4" /> Create</Button>
      </form>
      {!keys ? (
        <Skeleton className="h-48 rounded-xl" />
      ) : (
        <Card className="py-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Name</TableHead>
                <TableHead>Key</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Last used</TableHead>
                <TableHead className="pr-6 text-right" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {keys.map((key) => (
                <TableRow key={key.id} className={key.revokedAt ? "opacity-50" : undefined}>
                  <TableCell className="pl-6">{key.name}</TableCell>
                  <TableCell className="font-mono text-xs">hk_{key.prefix}_••••••••{key.prefix === currentPrefix && <span className="ml-2 text-primary">(this session)</span>}</TableCell>
                  <TableCell className="text-muted-foreground">{timeAgo(key.createdAt)}</TableCell>
                  <TableCell className="text-muted-foreground">{timeAgo(key.lastUsedAt)}</TableCell>
                  <TableCell className="pr-6 text-right">
                    {key.revokedAt ? (
                      <span className="text-xs text-muted-foreground">revoked {timeAgo(key.revokedAt)}</span>
                    ) : (
                      <Button variant="ghost" size="sm" className="text-destructive" disabled={key.prefix === currentPrefix} onClick={() => revoke(key)}>
                        Revoke
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
      <SecretDialog title="API key created" description="Use it as a Bearer token: Authorization: Bearer hk_…" secret={issued} onClose={() => setIssued(null)} />
    </>
  );
}
