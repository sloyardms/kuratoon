import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Kuratoon",
  description:
    "Self-hosted library and reader for manga, manhwa, manhua and comics.",
};

// Dark mode is the default; light/system become user settings in a later phase.
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="dark h-full antialiased">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
