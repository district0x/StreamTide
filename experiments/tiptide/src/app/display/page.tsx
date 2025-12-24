'use client';

import { useEffect, useState, useRef } from 'react';
import QRCode from 'qrcode';

// The URL that the QR code points to
const TIP_PAGE_URL = typeof window !== 'undefined'
  ? `${window.location.origin}/`
  : 'http://localhost:3000/';

interface TipEvent {
  amount: number;
  timestamp: number;
}

export default function DisplayPage() {
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [showCelebration, setShowCelebration] = useState(false);
  const [lastTip, setLastTip] = useState<TipEvent | null>(null);
  const [currentPerformer, setCurrentPerformer] = useState<string>('');
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Generate QR code on mount
  useEffect(() => {
    QRCode.toDataURL(TIP_PAGE_URL, {
      width: 400,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#ffffff',
      },
    }).then(setQrCodeUrl);
  }, []);

  // Listen for tip events via Server-Sent Events or polling
  useEffect(() => {
    // For now, we'll use a simple polling approach
    // Later this will be replaced with WebSocket or SSE
    const checkForTips = async () => {
      try {
        const res = await fetch('/api/tips/latest');
        if (res.ok) {
          const data = await res.json();
          if (data.tip && data.tip.timestamp > (lastTip?.timestamp || 0)) {
            triggerCelebration(data.tip.amount);
          }
        }
      } catch {
        // Ignore fetch errors during development
      }
    };

    const interval = setInterval(checkForTips, 2000);
    return () => clearInterval(interval);
  }, [lastTip]);

  const triggerCelebration = (amount: number) => {
    setLastTip({ amount, timestamp: Date.now() });
    setShowCelebration(true);

    // Play sound
    if (audioRef.current) {
      audioRef.current.currentTime = 0;
      audioRef.current.play().catch(() => {});
    }

    // Hide celebration after 5 seconds
    setTimeout(() => {
      setShowCelebration(false);
    }, 5000);
  };

  if (showCelebration && lastTip) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-green-400 to-emerald-600 flex flex-col items-center justify-center animate-pulse">
        <audio ref={audioRef} src="/tip-sound.mp3" preload="auto" />
        <div className="text-[12rem] mb-8 animate-bounce">
          💵
        </div>
        <h1 className="text-8xl font-bold text-white mb-4 drop-shadow-lg">
          ${lastTip.amount} TIP!
        </h1>
        <p className="text-3xl text-white/90">
          Thank you!
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-900 via-violet-900 to-indigo-900 flex flex-col items-center justify-center p-8">
      <audio ref={audioRef} src="/tip-sound.mp3" preload="auto" />

      {currentPerformer && (
        <div className="text-center mb-8">
          <p className="text-purple-300 text-xl">Now performing</p>
          <h2 className="text-4xl font-bold text-white">{currentPerformer}</h2>
        </div>
      )}

      <div className="bg-white p-6 rounded-3xl shadow-2xl mb-8">
        {qrCodeUrl ? (
          <img src={qrCodeUrl} alt="Scan to tip" className="w-80 h-80" />
        ) : (
          <div className="w-80 h-80 bg-gray-200 animate-pulse rounded-xl" />
        )}
      </div>

      <h1 className="text-5xl font-bold text-white mb-4">
        Scan to Tip
      </h1>

      <p className="text-xl text-purple-200 mb-2">
        Apple Pay • Google Pay • Card
      </p>

      <p className="text-purple-400 text-sm">
        Powered by USDC on Base • Zero fees
      </p>

      {/* Dev controls */}
      <div className="fixed bottom-4 right-4 flex gap-2">
        <button
          onClick={() => triggerCelebration(5)}
          className="bg-purple-600 hover:bg-purple-700 text-white text-xs px-3 py-2 rounded-lg opacity-50 hover:opacity-100"
        >
          Test $5
        </button>
        <button
          onClick={() => triggerCelebration(10)}
          className="bg-purple-600 hover:bg-purple-700 text-white text-xs px-3 py-2 rounded-lg opacity-50 hover:opacity-100"
        >
          Test $10
        </button>
      </div>
    </div>
  );
}
