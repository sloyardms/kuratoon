import { Suspense } from "react";

import { UsersAdmin } from "./users-admin";

export default function UsersPage() {
  return (
    <Suspense>
      <UsersAdmin />
    </Suspense>
  );
}
