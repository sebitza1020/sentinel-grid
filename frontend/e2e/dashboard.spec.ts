import { expect, Page, test } from '@playwright/test';

const canonical = [
  ['SPECTRE-01', 'SPECTRE-IV', 'RECON', 245, 32000, 2, 14000],
  ['ZEPHYR-02', 'ZEPHYR-1', 'RECON', 280, 26000, 1.5, 10000],
  ['ORACLE-03', 'ORACLE-K2', 'RECON', 215, 42000, 3.5, 18000],
  ['NIGHTJAR-04', 'NIGHTJAR-6', 'RECON', 235, 30000, 2.5, 15000],
  ['ARGUS-05', 'ARGUS-V', 'RECON', 200, 46000, 4, 20000],
  ['PHANTOM-06', 'PHANTOM-S9', 'RECON', 255, 28500, 2, 13000],
  ['GHOST-07', 'GHOSTLANCE-R3', 'RECON', 270, 24000, 1, 11000],
  ['CHIMERA-11', 'CHIMERA-7', 'INTERCEPTOR', 365, 18000, 9, 18000],
  ['RAZOR-12', 'RAZORWING-M5', 'INTERCEPTOR', 420, 15000, 7, 16500],
  ['TEMPEST-13', 'TEMPEST-I4', 'INTERCEPTOR', 395, 20000, 11, 20000],
  ['VIPER-14', 'VIPER-A8', 'INTERCEPTOR', 440, 14000, 6, 15500],
  ['LANCER-15', 'LANCER-Q6', 'INTERCEPTOR', 380, 22000, 12, 22000],
  ['FALCON-16', 'FALCON-XR', 'INTERCEPTOR', 410, 19000, 8, 19000],
  ['MANTIS-17', 'MANTIS-J9', 'INTERCEPTOR', 350, 24000, 14, 24000],
  ['TITAN-21', 'TITAN-X9', 'HEAVY_SUPPORT', 145, 18000, 110, 78000],
  ['VULCAN-22', 'VULCAN-H2', 'HEAVY_SUPPORT', 165, 20000, 90, 68000],
  ['COLOSSUS-23', 'COLOSSUS-M8', 'HEAVY_SUPPORT', 125, 24000, 130, 90000],
  ['BASTION-24', 'BASTION-C4', 'HEAVY_SUPPORT', 135, 26000, 120, 85000],
  ['ATLAS-25', 'ATLAS-G7', 'HEAVY_SUPPORT', 155, 22000, 100, 75000],
  ['WARHAMMER-26', 'WARHAMMER-D3', 'HEAVY_SUPPORT', 180, 16000, 75, 62000],
  ['GOLIATH-27', 'GOLIATH-K5', 'HEAVY_SUPPORT', 140, 28000, 125, 88000],
] as const;

const drones = canonical.map(
  ([callSign, model, modelClass, topSpeed, radarRange, payloadCapacity, batteryCapacity], i) => ({
    id: `unit-${i + 1}`,
    callSign,
    model,
    modelClass,
    topSpeed,
    radarRange,
    payloadCapacity,
    batteryCapacity,
    visionModes: ['Standard', 'Thermal', 'Infrared'],
    status: 'OFFLINE',
    createdAt: '2026-07-15T12:00:00',
  }),
);

const telemetry = Object.fromEntries(
  drones.map((drone, i) => [
    drone.callSign,
    {
      lat: 44.4268 + (i % 7) * 0.003,
      lng: 26.1025 + Math.floor(i / 7) * 0.004,
      alt: 150 + i,
      battery: 80,
      threat_level: 'SAFE',
      report: 'Sector clear.',
    },
  ]),
);

async function mockDashboard(page: Page, authenticated = false): Promise<void> {
  if (authenticated) {
    await page.addInitScript(() => localStorage.setItem('sentinel_token', 'e2e-token'));
  }
  await page.route('**/api/drones', (route) => route.fulfill({ json: drones }));
  await page.route('**/api/weather', (route) =>
    route.fulfill({
      json: {
        current: { temperature_2m: 20, wind_speed_10m: 5, relative_humidity_2m: 50 },
      },
    }),
  );
  await page.route('https://*.basemaps.cartocdn.com/**', (route) => route.fulfill({ status: 204 }));
  await page.routeWebSocket('**/ws/telemetry', (socket) => socket.send(JSON.stringify(telemetry)));
}

test('renders the Sentinel Grid dashboard and tactical map', async ({ page }) => {
  await mockDashboard(page);
  await page.goto('/');

  await expect(page.getByRole('heading', { name: /SENTINEL GRID/i })).toBeVisible();
  await expect(page.locator('#map')).toBeVisible();
  await expect(page.locator('#map')).toHaveClass(/leaflet-container/);
});

test('renders 21 expandable fleet dossiers and a matching map popup', async ({ page }) => {
  await mockDashboard(page, true);
  await page.goto('/');
  await page.getByRole('button', { name: /manage fleet/i }).click();

  const cards = page.locator('.drone-card');
  await expect(cards).toHaveCount(21);

  const spectre = page.locator('.drone-card[data-call-sign="SPECTRE-01"]');
  await spectre.click();
  await expect(spectre.getByText('32,000 m')).toBeVisible();
  await expect(spectre.getByText('Thermal')).toBeVisible();

  await page.locator('.leaflet-interactive').first().click({ force: true });
  const popup = page.locator('.drone-popup');
  await expect(popup).toContainText('SPECTRE-01');
  await expect(popup).toContainText('SPECTRE-IV');
  await expect(popup).toContainText('32,000 m');
});

test('keeps the 21-unit dossier panel usable on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockDashboard(page, true);
  await page.goto('/');
  await page.getByRole('button', { name: /manage fleet/i }).click();

  const sidebar = page.locator('.sidebar');
  await expect(sidebar).toBeVisible();
  await expect(page.locator('.drone-card')).toHaveCount(21);
  await page.locator('.drone-card[data-call-sign="SPECTRE-01"]').click();
  await expect(page.getByText('ACTIVE VISION MODES')).toBeVisible();

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  expect(hasHorizontalOverflow).toBe(false);
});
