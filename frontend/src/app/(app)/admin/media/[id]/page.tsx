import { Suspense } from "react";

import { MediaEdit } from "./media-edit";

/** The id is read inside Suspense, so the route shell prerenders (cacheComponents). */
export default function MediaEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  return (
    <Suspense>
      <MediaEdit params={params} />
    </Suspense>
  );
}
