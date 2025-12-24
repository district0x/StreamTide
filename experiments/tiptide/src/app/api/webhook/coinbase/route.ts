import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { flashForAmount } from '@/lib/hue';

// In-memory store for tips (replace with database in production)
let latestTip: { amount: number; timestamp: number } | null = null;

// Coinbase webhook signature verification
const COINBASE_WEBHOOK_SECRET = process.env.COINBASE_WEBHOOK_SECRET || '';

function verifySignature(payload: string, signature: string): boolean {
  if (!COINBASE_WEBHOOK_SECRET) {
    console.warn('COINBASE_WEBHOOK_SECRET not set - skipping verification');
    return true; // Allow in development
  }

  const hmac = crypto.createHmac('sha256', COINBASE_WEBHOOK_SECRET);
  hmac.update(payload);
  const expectedSignature = hmac.digest('hex');

  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expectedSignature)
  );
}

export async function POST(request: NextRequest) {
  try {
    const payload = await request.text();
    const signature = request.headers.get('x-cc-webhook-signature') || '';

    // Verify webhook signature
    if (COINBASE_WEBHOOK_SECRET && !verifySignature(payload, signature)) {
      console.error('Invalid webhook signature');
      return NextResponse.json({ error: 'Invalid signature' }, { status: 401 });
    }

    const data = JSON.parse(payload);
    console.log('Webhook received:', JSON.stringify(data, null, 2));

    // Handle different event types
    const eventType = data.event?.type;

    if (eventType === 'charge:confirmed' || eventType === 'charge:pending') {
      const payments = data.event?.data?.payments || [];
      const payment = payments[0];

      if (payment) {
        const amount = parseFloat(payment.value?.local?.amount || '0');
        const currency = payment.value?.local?.currency || 'USD';

        console.log(`Payment ${eventType}: $${amount} ${currency}`);

        // Store the tip (notify the tips endpoint)
        latestTip = {
          amount,
          timestamp: Date.now(),
        };

        // Also notify the tips API so display can poll it
        try {
          const baseUrl = process.env.NEXT_PUBLIC_BASE_URL || 'http://localhost:3000';
          await fetch(`${baseUrl}/api/tips/latest`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ amount }),
          });
        } catch (e) {
          console.error('Failed to notify tips API:', e);
        }

        // Trigger Hue light flash
        try {
          await flashForAmount(amount);
        } catch (e) {
          console.error('Failed to flash Hue light:', e);
        }

        return NextResponse.json({
          success: true,
          message: `Processed ${eventType}`,
          amount,
        });
      }
    }

    return NextResponse.json({ success: true, message: 'Event received' });
  } catch (error) {
    console.error('Webhook error:', error);
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    );
  }
}

// Endpoint to get the latest tip (for polling from display)
export async function GET() {
  return NextResponse.json({ tip: latestTip });
}
