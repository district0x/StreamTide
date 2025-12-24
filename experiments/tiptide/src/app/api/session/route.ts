import { NextRequest, NextResponse } from 'next/server';
import { SignJWT, importSPKI, importPKCS8 } from 'jose';
import crypto from 'crypto';

// Your destination wallet on Base
const DESTINATION_WALLET = process.env.DESTINATION_WALLET || '';
const CDP_API_KEY_NAME = process.env.CDP_API_KEY_NAME || '';
const CDP_API_KEY_PRIVATE_KEY = process.env.CDP_API_KEY_PRIVATE_KEY || '';

async function generateJWT(): Promise<string> {
  // Convert escaped newlines to actual newlines
  const pemKey = CDP_API_KEY_PRIVATE_KEY.replace(/\\n/g, '\n');

  // The key is in SEC1 EC format, convert to crypto KeyObject
  const keyObject = crypto.createPrivateKey({
    key: pemKey,
    format: 'pem',
  });

  // Import for jose using the crypto key
  const privateKey = keyObject;

  const now = Math.floor(Date.now() / 1000);

  const jwt = await new SignJWT({
    sub: CDP_API_KEY_NAME,
    iss: 'coinbase-cloud',
    uri: 'POST api.developer.coinbase.com/onramp/v1/token',
  })
    .setProtectedHeader({
      alg: 'ES256',
      kid: CDP_API_KEY_NAME,
      nonce: crypto.randomBytes(16).toString('hex'),
    })
    .setIssuedAt(now)
    .setNotBefore(now)
    .setExpirationTime(now + 120)
    .sign(privateKey);

  return jwt;
}

export async function POST(request: NextRequest) {
  try {
    if (!CDP_API_KEY_NAME || !CDP_API_KEY_PRIVATE_KEY || !DESTINATION_WALLET) {
      console.error('Missing CDP credentials or wallet address');
      return NextResponse.json(
        { error: 'Server configuration error' },
        { status: 500 }
      );
    }

    const body = await request.json();
    const { amount } = body;

    // Generate JWT for API authentication
    const jwt = await generateJWT();

    console.log('Generated JWT, calling Coinbase API...');
    console.log('Using key:', CDP_API_KEY_NAME);
    console.log('JWT (first 50 chars):', jwt.substring(0, 50));

    // Request session token from Coinbase
    const response = await fetch('https://api.developer.coinbase.com/onramp/v1/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwt}`,
      },
      body: JSON.stringify({
        destination_wallets: [
          {
            address: DESTINATION_WALLET,
            blockchains: ['base'],
          },
        ],
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error('Coinbase API error:', response.status, errorText);
      return NextResponse.json(
        { error: 'Failed to generate session token', details: errorText },
        { status: response.status }
      );
    }

    const data = await response.json();
    console.log('Got session token from Coinbase');

    // Build the onramp URL with session token
    const onrampUrl = `https://pay.coinbase.com/buy/select-asset?sessionToken=${data.token}`;

    return NextResponse.json({
      onrampUrl,
      sessionToken: data.token,
    });
  } catch (error) {
    console.error('Session token error:', error);
    return NextResponse.json(
      { error: 'Internal server error', details: String(error) },
      { status: 500 }
    );
  }
}
