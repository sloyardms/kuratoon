/** Home. The feeds (Continue Reading, Latest updates, …) arrive in phase 5. */
export default function Home() {
  return (
    <section className="flex flex-1 flex-col items-center justify-center gap-2 py-24 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">Your library</h1>
      <p className="text-muted-foreground">
        Nothing here yet. Imported series will show up on this page.
      </p>
    </section>
  );
}
