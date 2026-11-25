"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { IconRefresh, IconSend } from "@tabler/icons-react";
import { EmptyState } from "@/components/herald/empty-state";
import { PageHeader } from "@/components/herald/page-header";
import { Pager, useCursor } from "@/components/herald/pager";
import { HttpStatus, StatusBadge } from "@/components/herald/status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError, api, useApi } from "@/lib/api";
import { timeAgo } from "@/lib/format";
import type { Delivery, DeliveryStatus, Page } from "@/lib/types";

const FILTERS = ["ALL", "PENDING", "QUEUED", "DELIVERING", "SUCCEEDED", "DEAD"] as const;

export default function DeliveriesPage() {
  const router = useRouter();
  const params = useSearchParams();
  const status = (params.get("status") ?? "ALL") as (typeof FILTERS)[number];
  const endpointId = params.get("endpointId");
  const cursor = useCursor();
  const [replaying, setReplaying] = useState(false);

  const query = new URLSearchParams({ limit: "25" });
  if (status !== "ALL") query.set("status", status);
  if (endpointId) query.set("endpointId", endpointId);
  if (cursor.cursor) query.set("before", cursor.cursor);
  const { data, mutate } = useApi<Page<Delivery>>(`/v1/deliveries?${query}`, { refreshInterval: 3_000 });

  function setFilter(next: string) {
    const search = new URLSearchParams(params);
    if (next === "ALL") search.delete("status");
    else search.set("status", next);
    cursor.reset();
    router.replace(`/deliveries?${search}`);
  }

  async function replayDead() {
    setReplaying(true);
    try {
      const { data: result } = await api.post<{ replayed: number }>("/v1/deliveries/replay", {
        status: "DEAD",
        endpointId: endpointId ?? undefined,
      });
      toast.success(`Re-queued ${result.replayed} dead-lettered deliveries`);
      await mutate();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Replay failed");
    } finally {
      setReplaying(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Deliveries"
        description="One delivery per event × endpoint. Retries use exponential back-off; exhausted ones are dead-lettered."
        actions={
          status === "DEAD" && (
            <Button onClick={replayDead} disabled={replaying || !data?.data.length}>
              <IconRefresh className="size-4" /> Replay all dead{endpointId ? " for endpoint" : ""}
            </Button>
          )
        }
      />
      <div className="flex flex-wrap items-center gap-3">
        <Tabs value={status} onValueChange={setFilter}>
          <TabsList>
            {FILTERS.map((f) => (
              <TabsTrigger key={f} value={f} className="text-xs">{f.toLowerCase()}</TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        {endpointId && (
          <Badge variant="outline" className="cursor-pointer font-mono" onClick={() => router.replace(status === "ALL" ? "/deliveries" : `/deliveries?status=${status}`)}>
            endpoint …{endpointId.slice(-8)} ✕
          </Badge>
        )}
      </div>
      {!data ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : !data.data.length ? (
        <EmptyState icon={<IconSend className="size-5" />} title="Nothing here" description="No deliveries match this filter." />
      ) : (
        <>
          <Card className="py-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">Status</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Endpoint</TableHead>
                  <TableHead className="text-right">Attempts</TableHead>
                  <TableHead className="text-right">Last code</TableHead>
                  <TableHead>Last error</TableHead>
                  <TableHead className="pr-6 text-right">Next / done</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.data.map((d) => (
                  <TableRow key={d.id} className="cursor-pointer" onClick={() => router.push(`/deliveries/${d.id}`)}>
                    <TableCell className="pl-6"><StatusBadge status={d.status as DeliveryStatus} /></TableCell>
                    <TableCell><Badge variant="secondary" className="font-mono text-[11px]">{d.eventType}</Badge></TableCell>
                    <TableCell className="max-w-56 truncate font-mono text-xs">{d.endpointUrl}</TableCell>
                    <TableCell className="text-right font-mono tabular-nums">{d.attemptCount}</TableCell>
                    <TableCell className="text-right"><HttpStatus code={d.lastStatusCode} /></TableCell>
                    <TableCell className="max-w-48 truncate text-xs text-muted-foreground">{d.lastError ?? "—"}</TableCell>
                    <TableCell className="pr-6 text-right text-muted-foreground">
                      {d.completedAt ? timeAgo(d.completedAt) : timeAgo(d.nextAttemptAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
          <Pager page={cursor.page} nextCursor={data.nextCursor} onNext={cursor.next} onPrevious={cursor.previous} />
        </>
      )}
    </>
  );
}
