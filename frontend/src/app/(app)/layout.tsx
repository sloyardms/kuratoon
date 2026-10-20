import { TopBar } from "@/components/top-bar";

/** Signed-in area: top bar plus page content (proxy.ts keeps anonymous users out). */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      <TopBar />
      <main className="mx-auto w-full max-w-[1600px] flex-1 px-4 py-6">
        {children}
      </main>
    </>
  );
}
