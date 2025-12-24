// Philips Hue integration for tip celebrations

const HUE_BRIDGE_IP = process.env.HUE_BRIDGE_IP || '';
const HUE_USERNAME = process.env.HUE_USERNAME || '';
const HUE_LIGHT_ID = process.env.HUE_LIGHT_ID || '1';

interface HueState {
  on?: boolean;
  bri?: number;
  hue?: number;
  sat?: number;
  alert?: 'none' | 'select' | 'lselect';
  transitiontime?: number;
}

async function setLightState(state: HueState): Promise<boolean> {
  if (!HUE_BRIDGE_IP || !HUE_USERNAME) {
    console.warn('Hue not configured - skipping light control');
    return false;
  }

  try {
    const url = `http://${HUE_BRIDGE_IP}/api/${HUE_USERNAME}/lights/${HUE_LIGHT_ID}/state`;
    const response = await fetch(url, {
      method: 'PUT',
      body: JSON.stringify(state),
    });

    if (!response.ok) {
      console.error('Hue API error:', await response.text());
      return false;
    }

    return true;
  } catch (error) {
    console.error('Failed to control Hue light:', error);
    return false;
  }
}

export async function flashGreen(): Promise<void> {
  // Set to bright green
  await setLightState({
    on: true,
    bri: 254,
    hue: 25500, // Green
    sat: 254,
    transitiontime: 0,
  });

  // Flash effect
  await setLightState({
    alert: 'lselect', // Long flash (15 seconds of pulsing)
  });
}

export async function flashForAmount(amount: number): Promise<void> {
  // Different colors for different amounts
  let hue = 25500; // Default green

  if (amount >= 15) {
    hue = 46920; // Blue for big tips
  } else if (amount >= 10) {
    hue = 12750; // Yellow for medium tips
  }

  await setLightState({
    on: true,
    bri: 254,
    hue,
    sat: 254,
    transitiontime: 0,
  });

  await setLightState({
    alert: 'lselect',
  });
}

export async function resetLight(): Promise<void> {
  // Return to a subtle purple ambient
  await setLightState({
    on: true,
    bri: 100,
    hue: 47000, // Purple
    sat: 200,
    alert: 'none',
    transitiontime: 20, // 2 seconds
  });
}

// Helper to discover Hue bridge on network
export async function discoverBridge(): Promise<string | null> {
  try {
    const response = await fetch('https://discovery.meethue.com/');
    const bridges = await response.json();
    if (bridges.length > 0) {
      return bridges[0].internalipaddress;
    }
  } catch (error) {
    console.error('Bridge discovery failed:', error);
  }
  return null;
}
