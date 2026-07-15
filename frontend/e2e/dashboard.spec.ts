import { expect, test } from '@playwright/test';

test('renders the Sentinel Grid dashboard and tactical map', async ({ page }) => {
  await page.route('**/api/drones', async (route) => {
    await route.fulfill({ json: [] });
  });

  await page.route('**/api/weather', async (route) => {
    await route.fulfill({
      json: {
        current: {
          temperature_2m: 20,
          wind_speed_10m: 5,
          relative_humidity_2m: 50,
        },
      },
    });
  });

  await page.route('https://*.basemaps.cartocdn.com/**', async (route) => {
    await route.fulfill({ status: 204 });
  });

  await page.routeWebSocket('**/ws/telemetry', (socket) => {
    socket.send(JSON.stringify({}));
  });

  await page.goto('/');

  await expect(page.getByRole('heading', { name: /SENTINEL GRID/i })).toBeVisible();

  const map = page.locator('#map');
  await expect(map).toBeVisible();
  await expect(map).toHaveClass(/leaflet-container/);
});
