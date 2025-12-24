'use client';

import { useState } from 'react';

// Your Coinbase CDP Project ID
const COINBASE_APP_ID = '6ebf00fc-6b6a-45fb-9864-678171aefc02';

const TIP_AMOUNTS = [5, 10, 15];

export default function Home() {
  const [lastTip, setLastTip] = useState<number | null>(null);
  const [showCelebration, setShowCelebration] = useState(false);
  const [loading, setLoading] = useState<number | null>(null);

  const handleTip = async (amount: number) => {
    setLoading(amount);

    try {
      // Get session token from our backend
      const response = await fetch('/api/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount }),
      });

      if (!response.ok) {
        const error = await response.json();
        console.error('Session token error:', error);
        alert('Failed to initialize payment. Please try again.');
        setLoading(null);
        return;
      }

      const { onrampUrl } = await response.json();

      // Open Coinbase Onramp
      window.open(onrampUrl, '_blank');
    } catch (error) {
      console.error('Payment error:', error);
      alert('Something went wrong. Please try again.');
    } finally {
      setLoading(null);
    }
  };

  // This will be triggered by webhook later
  const triggerCelebration = (amount: number) => {
    setLastTip(amount);
    setShowCelebration(true);

    // Play sound
    const audio = new Audio('/tip-sound.mp3');
    audio.play().catch(() => {}); // Ignore autoplay restrictions

    // Hide celebration after 5 seconds
    setTimeout(() => {
      setShowCelebration(false);
      setLastTip(null);
    }, 5000);
  };

  if (showCelebration) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-green-400 to-emerald-600 flex flex-col items-center justify-center animate-pulse">
        <div className="text-9xl mb-8 animate-bounce">
          💵
        </div>
        <h1 className="text-6xl font-bold text-white mb-4">
          ${lastTip} TIP!
        </h1>
        <p className="text-2xl text-white/80">
          Thank you for supporting the artist!
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-900 via-violet-900 to-indigo-900 flex flex-col items-center justify-center p-8">
      <div className="text-center mb-12">
        <h1 className="text-5xl font-bold text-white mb-4">
          Tip the Artist
        </h1>
        <p className="text-xl text-purple-200">
          Tap to tip via Apple Pay
        </p>
      </div>

      <div className="flex flex-col gap-6 w-full max-w-sm">
        {TIP_AMOUNTS.map((amount) => (
          <button
            key={amount}
            onClick={() => handleTip(amount)}
            disabled={loading !== null}
            className={`bg-white hover:bg-purple-100 text-purple-900 font-bold text-3xl py-8 px-12 rounded-2xl shadow-2xl transform transition-all duration-200 ${
              loading === amount
                ? 'opacity-50 cursor-wait'
                : loading !== null
                  ? 'opacity-30'
                  : 'hover:scale-105 active:scale-95'
            }`}
          >
            {loading === amount ? 'Loading...' : `$${amount}`}
          </button>
        ))}
      </div>

      <p className="text-purple-300 text-sm mt-12 text-center max-w-xs">
        Powered by USDC on Base • Zero fees
      </p>

      {/* Dev button to test celebration - remove in production */}
      <button
        onClick={() => triggerCelebration(5)}
        className="mt-8 text-purple-400 text-xs underline opacity-50 hover:opacity-100"
      >
        [Dev] Test celebration
      </button>
    </div>
  );
}
