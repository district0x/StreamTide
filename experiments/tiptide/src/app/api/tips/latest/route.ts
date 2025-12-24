import { NextResponse } from 'next/server';

// Shared store - in production, use Redis or a database
// For now, we'll use a simple file-based approach or in-memory

// This is a placeholder - the actual tip data comes from the webhook
// In production, both routes would share a Redis instance or database
let latestTip: { amount: number; timestamp: number } | null = null;

export async function GET() {
  return NextResponse.json({ tip: latestTip });
}

// Allow webhook to update the latest tip
export async function POST(request: Request) {
  const data = await request.json();
  latestTip = {
    amount: data.amount,
    timestamp: Date.now(),
  };
  return NextResponse.json({ success: true });
}
