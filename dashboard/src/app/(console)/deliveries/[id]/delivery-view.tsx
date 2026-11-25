"use client";

import Link from "next/link";
import { toast } from "sonner";
import { IconArrowLeft, IconClockPause, IconRefresh } from "@tabler/icons-react";
import { AttemptTimeline } from "@/components/herald/attempt-timeline";
import { EmptyState } from "@/components/herald/empty-state";
import { PageHeader } from "@/components/herald/page-header";
import { StatusBadge } from "@/components/herald/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError, api, useApi } from "@/lib/api";
import { dateTime, timeAgo } from "@/lib/format";
import type { DeliveryDetail } from "@/lib/types";

export function DeliveryView({ id }: { id: string }) {
  const { data, mutate } = useApi<DeliveryDetail>(`/v1/deliveries/${id}`, { refreshInterval: 2_000 });

  if (!data) return <Skeleton className="h-96 rounded-xl" />;
  const { delivery, attempts } = data;
  const replayable = delivery.status === "SUCCEEDED" || delivery.status === "DEAD";

  async function replay() {
    try {
      await api.post(`/v1/deliveries/${id}/replay`);
      toast.success("Delivery re-queued with a fresh retry budget");
      await mutate();
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Replay failed");
    }
  }

  return (
    <>
      <Link href="/deliveries" className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <IconArrowLeft className="size-4" /> Deliveries
      </Link>
      <PageHeader
        title={`${delivery.eventType} → ${new URL(delivery.endpointUrl).host}`}
        description={delivery.id}
        actions={
          <Button onClick={replay} disabled={!replayable}>
            <IconRefresh className="size-4" /> Replay
          </Button>
        }
      />
      <div className="grid gap-6 xl:grid-cols-3 [&>*]:min-w-0">
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Summary</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-3 text-sm">
              <Row label="Status"><StatusBadge status={delivery.status} /></Row>
              <Row label="Endpoint"><span className="break-all font-mono text-xs">{delivery.endpointUrl}</span></Row>
              <Row label="Event"><Link className="font-mono text-xs hover:underline" href={`/events/${delivery.eventId}`}>{delivery.eventId}</Link></Row>
              <Row label="Attempts">{delivery.attemptCount}</Row>
              <Row label="Created">{dateTime(delivery.createdAt)}</Row>
              {delivery.completedAt ? (
                <Row label="Completed">{dateTime(delivery.completedAt)}</Row>
              ) : (
                <Row label="Next attempt">{timeAgo(delivery.nextAttemptAt)}</Row>
              )}
              {delivery.lastError && <Row label="Last error"><span className="text-destructive">{delivery.lastError}</span></Row>}
            </dl>
          </CardContent>
        </Card>
        <div className="xl:col-span-2">
          {attempts.length ? (
            <AttemptTimeline attempts={attempts} />
          ) : (
            <EmptyState icon={<IconClockPause className="size-5" />} title="No attempts yet" description="The dispatcher picks up due deliveries every 250 ms." />
          )}
        </div>
      </div>
    </>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-3 gap-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="col-span-2">{children}</dd>
    </div>
  );
}
