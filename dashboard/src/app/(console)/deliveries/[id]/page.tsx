import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { DeliveryView } from "./delivery-view";

export default function Page({ params }: PageProps<"/deliveries/[id]">) {
  return (
    <Suspense fallback={<Skeleton className="h-96 rounded-xl" />}>
      {params.then(({ id }) => <DeliveryView id={id} />)}
    </Suspense>
  );
}
