# TipTide - Open Mic Digital Tip Jar

A physical-world tipping experience using Apple Pay → USDC on Base, with real-time celebration feedback.

## Quick Start

```bash
npm install
cp .env.example .env.local
# Edit .env.local with your configuration
npm run dev
```

## Pages

- `/` - Tip page (what tippers see when they scan the QR code)
- `/display` - Display page (shows QR code + celebrations on iPad)

## Setup

### 1. Coinbase Developer Portal

1. Go to [Coinbase Developer Portal](https://portal.cdp.coinbase.com/)
2. Create a new project
3. Enable Onramp for your project
4. Get your App ID
5. Set up a webhook endpoint pointing to `https://your-domain.com/api/webhook/coinbase`

### 2. Your Wallet

You need a wallet on Base to receive tips. You can use:
- Coinbase Wallet
- MetaMask (add Base network)
- Any EVM-compatible wallet

Copy your wallet address to `DESTINATION_WALLET` in `.env.local`

### 3. Philips Hue (Optional)

1. Find your bridge IP: Visit https://discovery.meethue.com/
2. Create a username:
   - Press the button on your Hue bridge
   - Within 30 seconds, run:
     ```bash
     curl -X POST http://{BRIDGE_IP}/api -d '{"devicetype":"tiptide#device"}'
     ```
   - Copy the username from the response
3. Find your light ID:
   ```bash
   curl http://{BRIDGE_IP}/api/{USERNAME}/lights
   ```
4. Add to `.env.local`:
   ```
   HUE_BRIDGE_IP=192.168.1.x
   HUE_USERNAME=your_username
   HUE_LIGHT_ID=1
   ```

### 4. Testing Locally

1. Run `npm run dev`
2. Open `/display` on your iPad (or browser)
3. Use the "Test $5" button to simulate a tip
4. The celebration animation should play

To test the full flow with real payments:
1. Use ngrok to expose your local server: `ngrok http 3000`
2. Update your Coinbase webhook to point to the ngrok URL
3. Scan the QR code with your phone and complete a real tip

## Deployment

### Vercel (Recommended)

```bash
npm install -g vercel
vercel
```

Add your environment variables in the Vercel dashboard.

### Local Network (for venue use)

Run on a Mac at the venue:
```bash
npm run build
npm start
```

Access the display page from iPad on the same network.

## Architecture

```
Tipper's Phone
     │
     ▼ (scans QR)
  /tip page
     │
     ▼ (taps Apple Pay)
  Coinbase Onramp
     │
     ▼ (USDC on Base)
  Your Wallet
     │
     ▼ (webhook)
  /api/webhook/coinbase
     │
     ├──▶ /api/tips/latest (updates state)
     │
     └──▶ Hue Bridge (flashes light)
             │
             ▼
       /display page (polls for tips, shows celebration)
```

## Phase 0 Checklist

- [x] Landing page with tip buttons
- [x] Coinbase Onramp integration
- [x] QR code display
- [x] Webhook receiver
- [x] Celebration animation
- [x] Hue light integration
- [ ] Test with real payment
- [ ] Deploy to production

## Next Steps (Phase 1+)

See the plan file for the full roadmap including:
- Per-artist QR codes
- Embedded wallets for artists
- Automatic splitting
- Quadratic matching distribution
