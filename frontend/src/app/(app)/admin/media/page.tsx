import { Suspense } from "react";

import { MediaAdminList } from "./media-admin-list";

export default function MediaAdminPage() {
  return (
    <Suspense>
      <MediaAdminList />
    </Suspense>
  );
}
