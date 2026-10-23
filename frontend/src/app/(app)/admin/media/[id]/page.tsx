import { MediaEdit } from "./media-edit";

export default async function MediaEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <MediaEdit id={id} />;
}
