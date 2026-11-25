import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { EventView } from "./event-view";

export default function Page({ params }: PageProps<"/events/[id]">) {
  return (
    <Suspense fallback={<Skeleton className="h-96 rounded-xl" />}>
      {params.then(({ id }) => <EventView id={id} />)}
    </Suspense>
  );
}
