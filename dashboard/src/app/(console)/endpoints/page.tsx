"use client";

import { useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { IconDots, IconPlugConnected, IconPlus } from "@tabler/icons-react";
import { EmptyState } from "@/components/herald/empty-state";
import { PageHeader } from "@/components/herald/page-header";
import { SecretDialog } from "@/components/herald/secret-dialog";
import { StatusBadge } from "@/components/herald/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiError, api, useApi } from "@/lib/api";
import { timeAgo } from "@/lib/format";
import type { Endpoint, EndpointWithSecret } from "@/lib/types";

export default function EndpointsPage() {
  const { data: endpoints, mutate } = useApi<Endpoint[]>("/v1/endpoints");
  const [creating, setCreating] = useState(false);
  const [secret, setSecret] = useState<{ title: string; value: string } | null>(null);

  async function run(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      toast.success(success);
      await mutate();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Request failed");
    }
  }

  return (
    <>
      <PageHeader
        title="Endpoints"
        description="HTTPS receivers and the event types they subscribe to. Wildcards like claim.* match every claim event."
        actions={
          <Button onClick={() => setCreating(true)}>
            <IconPlus className="size-4" /> Add endpoint
          </Button>
        }
      />

      {!endpoints ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : !endpoints.length ? (
        <EmptyState
          icon={<IconPlugConnected className="size-5" />}
          title="No endpoints yet"
          description="Register a partner's webhook URL to start receiving signed deliveries."
          action={<Button onClick={() => setCreating(true)}>Add your first endpoint</Button>}
        />
      ) : (
        <Card className="py-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Endpoint</TableHead>
                <TableHead>Subscriptions</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Updated</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {endpoints.map((endpoint) => (
                <TableRow key={endpoint.id}>
                  <TableCell className="max-w-md pl-6">
                    <div className="truncate font-mono text-xs">{endpoint.url}</div>
                    <div className="truncate text-xs text-muted-foreground">{endpoint.description ?? "—"}</div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {endpoint.eventTypes.map((t) => (
                        <Badge key={t} variant="secondary" className="font-mono text-[11px]">{t}</Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell><StatusBadge status={endpoint.status} /></TableCell>
                  <TableCell className="text-muted-foreground">{timeAgo(endpoint.updatedAt)}</TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" aria-label="Endpoint actions"><IconDots className="size-4" /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem asChild>
                          <Link href={`/deliveries?endpointId=${endpoint.id}`}>View deliveries</Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onSelect={() =>
                            run(async () => {
                              const { secret: value } = await api.get<{ secret: string }>(`/v1/endpoints/${endpoint.id}/secret`);
                              setSecret({ title: "Signing secret", value });
                            }, "Secret revealed")
                          }
                        >
                          Reveal signing secret
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onSelect={() =>
                            run(async () => {
                              const { data } = await api.post<EndpointWithSecret>(`/v1/endpoints/${endpoint.id}/secret/rotate`);
                              setSecret({ title: "New signing secret", value: data.secret });
                            }, "Secret rotated — the old one keeps signing for 24 h")
                          }
                        >
                          Rotate secret
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onSelect={() =>
                            run(
                              () => api.patch(`/v1/endpoints/${endpoint.id}`, { status: endpoint.status === "ENABLED" ? "DISABLED" : "ENABLED" }),
                              endpoint.status === "ENABLED" ? "Endpoint disabled" : "Endpoint enabled",
                            )
                          }
                        >
                          {endpoint.status === "ENABLED" ? "Disable" : "Enable"}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          variant="destructive"
                          onSelect={() => {
                            if (confirm(`Delete ${endpoint.url}? Its delivery history is deleted too.`)) {
                              void run(() => api.delete(`/v1/endpoints/${endpoint.id}`), "Endpoint deleted");
                            }
                          }}
                        >
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <CreateEndpointDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={async (created) => {
          setCreating(false);
          setSecret({ title: "Endpoint created", value: created.secret });
          await mutate();
        }}
      />
      <SecretDialog
        title={secret?.title ?? ""}
        description="Receivers verify the webhook-signature header with this secret (Standard Webhooks)."
        secret={secret?.value ?? null}
        onClose={() => setSecret(null)}
      />
    </>
  );
}

function CreateEndpointDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (created: EndpointWithSecret) => void;
}) {
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [types, setTypes] = useState("claim.*");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setErrors({});
    try {
      const eventTypes = types.split(",").map((t) => t.trim()).filter(Boolean);
      const { data } = await api.post<EndpointWithSecret>("/v1/endpoints", { url, description: description || null, eventTypes });
      setUrl("");
      setDescription("");
      onCreated(data);
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.problem.errors ?? { form: e.message });
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Add endpoint</DialogTitle>
            <DialogDescription>Herald signs every delivery and retries failures with exponential back-off.</DialogDescription>
          </DialogHeader>
          <Field label="URL" error={errors.url}>
            <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://partner.example.com/webhooks" className="font-mono" required />
          </Field>
          <Field label="Description" error={errors.description}>
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Repair-shop network" />
          </Field>
          <Field label="Event types" hint="Comma-separated. Use * for everything or claim.* for a family." error={errors.eventTypes}>
            <Input value={types} onChange={(e) => setTypes(e.target.value)} className="font-mono" required />
          </Field>
          {errors.form && <p className="text-sm text-destructive">{errors.form}</p>}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={pending}>Create endpoint</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error ? <p className="text-xs text-destructive">{error}</p> : hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
